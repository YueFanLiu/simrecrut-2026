package fr.isep.simrecrut.worker.assessment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.domain.assessment.CaseMatchingEngine;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.DeleteResult;
import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.gateway.ml.CasePredictionGateway;
import fr.isep.simrecrut.integration.pdf.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.service.assessment.*;
import fr.isep.simrecrut.workspace.Facts;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
@Profile("!resume-workspace")
public class CandidateCaseWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateCaseWorker.class);
    private final CandidateCaseMapper db;
    private final ObjectMapper json;
    private final CaseFileStore files;
    private final LocalResumeCleaner cleaner;
    private final CaseMatchingEngine matching;
    private final CaseConfiguration configuration;
    private final CasePredictionGateway predictions;
    private final PublicIdGenerator ids;
    private final TransactionTemplate tx;
    private final String workerId = UUID.randomUUID().toString();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(2);
    private final Semaphore capacity = new Semaphore(2);

    public CandidateCaseWorker(CandidateCaseMapper db, ObjectMapper json, CaseFileStore files,
            LocalResumeCleaner cleaner, CaseMatchingEngine matching, CaseConfiguration configuration,
            CasePredictionGateway predictions, PublicIdGenerator ids, PlatformTransactionManager transactions) {
        this.db = db; this.json = json; this.files = files; this.cleaner = cleaner; this.matching = matching;
        this.configuration = configuration; this.predictions = predictions; this.ids = ids;
        tx = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void dispatch() {
        try {
            for (Task task : db.pendingTasks()) {
                if (!capacity.tryAcquire()) return;
                try { workers.execute(() -> { try { execute(task); } finally { capacity.release(); } }); }
                catch (RejectedExecutionException exception) { capacity.release(); return; }
            }
        } catch (Exception exception) { LOG.warn("Candidate task dispatch is temporarily unavailable."); }
    }

    /** Claim and every write recheck both the lease and the parent's privacy/retention state. */
    public void execute(Task pending) {
        Lease lease = tx.execute(ignored -> {
            AssessmentCase row = db.lock(pending.resourcePublicId());
            if (row == null) return null;
            Task task = db.lockTask(pending.id());
            if (task == null || !(task.status().equals("QUEUED") || task.status().equals("RUNNING"))) return null;
            if (!permitted(row, task.taskType())) return null;
            if (task.attemptCount() >= task.maxAttempts()) {
                // Expired final attempts are finalized, rather than left permanently RUNNING.
                if (task.leaseToken() == null || db.expireTask(task.id()) != 1) return null;
                row.safeErrorCode = "ATTEMPTS_EXHAUSTED";
                if (task.taskType().equals("CASE_EXTRACT") || task.taskType().equals("CASE_ASSESS")) {
                    row.processingStatus = "FAILED_FINAL"; save(row);
                }
                return null;
            }
            String token = UUID.randomUUID().toString();
            if (db.claim(task.id(), token, workerId) != 1) return null;
            return new Lease(task.id(), token, row.publicId, task.taskType(), task.attemptCount() + 1, task.maxAttempts());
        });
        if (lease == null) return;
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            try { db.heartbeat(lease.id(), lease.token()); }
            catch (Exception exception) { LOG.warn("Candidate task heartbeat failed; stale writes remain blocked."); }
        }, 30, 30, TimeUnit.SECONDS);
        try {
            switch (lease.type()) {
                case "CASE_EXTRACT" -> extract(lease);
                case "CASE_ASSESS" -> assess(lease);
                case "CASE_CLEANUP" -> cleanup(lease);
                case "CASE_PURGE" -> purge(lease);
                default -> throw new IllegalStateException("Unsupported case task.");
            }
        } catch (Exception exception) { fail(lease, exception); }
        finally { heartbeat.cancel(false); }
    }

    private void extract(Lease lease) throws Exception {
        AssessmentCase row = db.find(lease.caseId());
        if (row == null) return;
        String path = db.artifact(row.id);
        if (path == null) throw new IllegalArgumentException("The original PDF is unavailable.");
        ObjectNode draft = cleaner.clean("original.pdf", files.read(path));
        draft.remove(List.of("schemaVersion", "cleanerVersion", "status"));
        draft = Facts.validate(draft); draft.putArray("confirmedAbsent");
        ObjectNode finalDraft = draft;
        guarded(lease, current -> {
            current.draftJson = finalDraft.toString(); current.processingStatus = "REVIEW_REQUIRED";
            current.stage = "REVIEW"; current.safeErrorCode = null; save(current);
            finish(lease, "SUCCEEDED");
        });
    }

    private void assess(Lease lease) {
        AssessmentCase row = db.find(lease.caseId());
        if (row == null) return;
        JobVersion job = db.job(row.jobVersionId);
        CaseConfiguration.Bundle config = configuration.load(job, db.protocol(row.protocolVersionId));
        String existing = db.savedResult(row.id, "PROFESSIONAL");
        JsonNode professional;
        if (existing == null) {
            ObjectNode draft = json.createObjectNode(); draft.set("professional", read(db.snapshot(row.id)));
            draft.set("confirmedAbsent", read(row.confirmedAbsentJson));
            ArrayNode requirements = json.createArrayNode(); db.requirements(row.jobVersionId).forEach(s -> requirements.add(read(s)));
            professional = matching.calculate(draft, job, requirements, config, row.assessmentReferenceMonth);
            JsonNode result = professional;
            guarded(lease, current -> {
                changed(db.saveResult(current.id, "PROFESSIONAL", result.toString()));
                changed(db.writeMatching(current.id, job.matchingRuleVersionId));
                changed(db.writeObjective(current.id, current.protocolVersionId, config.protocol().tRule()));
                current.stage = "NEUTRAL"; save(current);
            });
        } else professional = read(existing);
        for (String type : List.of("NEUTRAL", "BIASED")) {
            if (db.savedResult(row.id, type) != null) continue;
            Model model = db.model(type.equals("NEUTRAL") ? row.neutralModelVersionId : row.biasedModelVersionId);
            if (model == null || !model.modelType().equals(type) || model.protocolVersionId() != row.protocolVersionId
                    || model.featureSchemaVersionId() != config.protocol().featureSchemaVersionId()) throw CaseException.dependency();
            ObjectNode research = json.createObjectNode();
            Map<String, String> names = Map.of("SCHOOL_CATEGORY", "schoolPrestige", "REFERRAL", "referral",
                    "GENDER", "gender", "ETHNICITY", "ethnicity");
            db.attributes(row.id).forEach(a -> research.put(names.get(a.type()), a.value()));
            JsonNode prediction = predictions.predict(row.publicId, model, config.protocol(), professional.path("professional"), research);
            validatePrediction(row.publicId, model, config.protocol(), prediction);
            ObjectNode safePrediction = json.createObjectNode();
            for (String field : List.of("requestId", "caseId", "modelVersion", "featureSchemaVersion", "status",
                    "logit", "acceptanceScore", "modelThreshold", "commonThreshold", "decision", "commonThresholdDecision")) {
                safePrediction.set(field, prediction.path(field));
            }
            guarded(lease, current -> {
                changed(db.saveResult(current.id, type, safePrediction.toString()));
                changed(db.writeMl(current.id, type, model)); current.stage = type.equals("NEUTRAL") ? "BIASED" : "FINALIZING";
                save(current);
            });
        }
        guarded(lease, current -> {
            for (String type : List.of("PROFESSIONAL", "NEUTRAL", "BIASED")) {
                if (db.savedResult(current.id, type) == null) throw CaseException.conflict();
            }
            current.processingStatus = "AUTOMATED_COMPLETE"; current.stage = "COMPLETE";
            current.automatedCompletedAt = now(); current.purgeAfter = current.automatedCompletedAt.plusDays(30); current.safeErrorCode = null;
            save(current); finish(lease, "SUCCEEDED");
        });
    }

    private void validatePrediction(String caseId, Model model, Protocol protocol, JsonNode value) {
        if (value == null || !"SUCCESS".equals(value.path("status").asText())
                || !caseId.equals(value.path("caseId").asText())
                || !model.versionCode().equals(value.path("modelVersion").asText())
                || !protocol.schemaVersion().equals(value.path("featureSchemaVersion").asText())) throw CaseException.dependency();
        for (String field : List.of("logit", "acceptanceScore", "modelThreshold", "commonThreshold")) {
            if (!value.path(field).isNumber() || !Double.isFinite(value.path(field).asDouble())) throw CaseException.dependency();
        }
        double score = value.path("acceptanceScore").asDouble();
        if (score < 0 || score > 1 || value.path("modelThreshold").asDouble() != model.threshold()
                || value.path("commonThreshold").asDouble() != model.commonThreshold()
                || !(score >= model.threshold() ? "Accept" : "Reject").equals(value.path("decision").asText())
                || !(score >= model.commonThreshold() ? "Accept" : "Reject").equals(value.path("commonThresholdDecision").asText())) {
            throw CaseException.dependency();
        }
    }

    private void cleanup(Lease lease) throws Exception {
        files.removeCase(lease.caseId());
        guarded(lease, row -> {
            db.artifactsDeleted(row.id); row.cleanupStatus = "COMPLETE"; save(row); finish(lease, "SUCCEEDED");
        });
    }

    private void purge(Lease lease) throws Exception {
        files.removeCase(lease.caseId());
        guarded(lease, row -> {
            db.lockExperiments(row.id); db.cancelExperimentTasks(row.id); db.invalidateExperiments(row.id);
            db.deleteMetrics(row.id); db.deleteVariantReviews(row.id); db.deleteVariantResults(row.id);
            db.deleteVariants(row.id); db.deleteBaselines(row.id); db.deleteSubjects(row.id);
            db.deleteReviews(row.id); db.deleteGrants(row.id); db.deleteAttributes(row.id); db.deleteArtifacts(row.id);
            db.deleteSnapshots(row.id); db.deleteMatching(row.id); db.deleteObjective(row.id); db.deleteMl(row.id);
            db.deleteResults(row.id); db.redactOperations(row.publicId);
            db.completeDeletionReceipts(row.publicId, json.valueToTree(new DeleteResult(row.publicId, "DELETED")).toString());
            db.deletionReceipt(CandidateCaseService.hash(row.publicId.getBytes(StandardCharsets.UTF_8)), row.deletionReason);
            db.deleteTasks(row.publicId); changed(db.deleteCase(row.id));
        });
    }

    private void fail(Lease lease, Exception error) {
        try {
            guarded(lease, row -> {
                boolean cleanup = lease.type().equals("CASE_CLEANUP") || lease.type().equals("CASE_PURGE");
                boolean terminal = !cleanup && (lease.attempt() >= lease.maxAttempts() || error instanceof IllegalArgumentException);
                if (cleanup) row.cleanupStatus = "PENDING";
                else {
                    row.safeErrorCode = error instanceof CaseException c ? c.code() : "CASE_PROCESSING_FAILED";
                    row.processingStatus = terminal ? "FAILED_FINAL" : "FAILED_RETRYABLE";
                }
                save(row); finish(lease, terminal ? "FAILED_FINAL" : "RETRYABLE");
            });
        } catch (Exception ignored) { LOG.warn("Candidate task failure state could not be saved; recovery will recheck its lease."); }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void maintenance() {
        try {
            for (AssessmentCase candidate : db.maintenanceCases()) tx.executeWithoutResult(ignored -> {
                AssessmentCase row = db.lock(candidate.publicId);
                if (row == null) return;
                boolean expired = !row.purgeAfter.isAfter(now());
                if (expired && !row.privacyStatus.equals("PURGING")) {
                    row.privacyStatus = "PURGING"; row.deletionReason = "RETENTION_EXPIRY";
                    row.draftJson = null; row.cleanupStatus = "PENDING";
                    db.cancelTasks(row.publicId); db.cancelReviews(row.id); save(row);
                }
                String type = row.privacyStatus.equals("PURGING") ? "CASE_PURGE" : "CASE_CLEANUP";
                if (row.cleanupStatus.equals("PENDING")) {
                    // Cleanup is retried indefinitely; technical failures never reopen content access.
                    db.resetCleanupAttempts(row.publicId, type);
                    if (!db.hasOpenTask(row.publicId, type)) db.enqueue(ids.nextId(), row.publicId, type);
                }
            });
        } catch (Exception error) { LOG.warn("Candidate retention maintenance is temporarily unavailable."); }
    }

    private void guarded(Lease lease, Consumer<AssessmentCase> action) {
        tx.executeWithoutResult(ignored -> {
            AssessmentCase row = db.lock(lease.caseId());
            Task task = db.lockTask(lease.id());
            if (row == null || task == null || !lease.token().equals(task.leaseToken()) || !task.status().equals("RUNNING")
                    || !permitted(row, lease.type()) || db.heartbeat(lease.id(), lease.token()) != 1) {
                throw CaseException.conflict();
            }
            action.accept(row);
        });
    }
    private boolean permitted(AssessmentCase row, String type) {
        if (type.equals("CASE_PURGE")) return row.privacyStatus.equals("PURGING");
        if (!row.privacyStatus.equals("ACTIVE") || !db.retained(row.id)) return false;
        return switch (type) {
            case "CASE_EXTRACT" -> row.processingStatus.equals("EXTRACTING");
            case "CASE_ASSESS" -> row.processingStatus.equals("AUTOMATED_RUNNING");
            case "CASE_CLEANUP" -> row.confirmedAt != null;
            default -> false;
        };
    }
    private void finish(Lease lease, String status) { changed(db.finishTask(lease.id(), lease.token(), status)); }
    private void save(AssessmentCase row) { changed(db.update(row)); row.revisionNo++; }
    private void changed(int count) { if (count != 1) throw CaseException.conflict(); }
    private JsonNode read(String data) {
        try { if (data == null) throw CaseException.dependency(); return json.readTree(data); }
        catch (Exception error) { throw CaseException.dependency(); }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    @PreDestroy public void stop() { workers.shutdownNow(); heartbeats.shutdownNow(); }
    private record Lease(long id, String token, String caseId, String type, int attempt, int maxAttempts) { }
}
