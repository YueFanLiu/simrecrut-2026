package fr.isep.simrecrut.service.assessment;

import static fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.domain.assessment.CaseMatchingEngine;
import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.gateway.ml.CasePredictionGateway;
import fr.isep.simrecrut.integration.pdf.CaseFileStore;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!resume-workspace")
public class CandidateCaseService {
    private final CandidateCaseMapper db;
    private final RoleMapper roles;
    private final ObjectMapper json;
    private final PublicIdGenerator ids;
    private final CaseFileStore files;
    private final CaseConfiguration configuration;
    private final CaseMatchingEngine matching;
    private final CasePredictionGateway predictions;
    private final TransactionTemplate tx;
    private final String protocolCode;

    public CandidateCaseService(CandidateCaseMapper db, RoleMapper roles, ObjectMapper json, PublicIdGenerator ids,
            CaseFileStore files, CaseConfiguration configuration, CaseMatchingEngine matching,
            CasePredictionGateway predictions, PlatformTransactionManager transactions,
            @Value("${simrecrut.cases.protocol-version:}") String protocolCode) {
        this.db = db; this.roles = roles; this.json = json; this.ids = ids; this.files = files;
        this.configuration = configuration; this.matching = matching; this.predictions = predictions;
        this.tx = new TransactionTemplate(transactions); this.protocolCode = protocolCode;
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:create')")
    public OperationResult<JsonNode> create(AccountPrincipal actor, String key, CreateCaseRequest body) {
        return mutation(actor, key, "", "CREATE", body, ignored -> {
            JobVersion job = db.published(body.jobVersionId());
            if (job == null) throw new CaseException(409, "JOB_UNAVAILABLE", "Select a current published job version.");
            List<Protocol> choices = db.protocols(job.matchingRuleVersionId, protocolCode);
            if (choices.size() != 1) throw CaseException.dependency();
            configuration.load(job, choices.get(0));
            AssessmentCase row = new AssessmentCase();
            row.publicId = ids.nextId(); row.candidateUserId = actor.userId(); row.jobVersionId = job.id;
            row.protocolVersionId = choices.get(0).id(); row.processingStatus = "AWAITING_UPLOAD";
            row.humanStatus = "NOT_ASSIGNED"; row.privacyStatus = "ACTIVE"; row.cleanupStatus = "NONE";
            row.stage = "UPLOAD"; row.revisionNo = 1L; row.purgeAfter = now().plusHours(24);
            changed(db.insert(row));
            return new Applied(row, 201, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:list')")
    public CasePage list(AccountPrincipal actor, int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination.");
        return tx.execute(ignored -> new CasePage(db.list(actor.userId(), (page - 1) * size, size).stream()
                .map(row -> new CaseSummary(row.publicId, job(row).title, row.processingStatus,
                        row.humanStatus, row.createdAt, row.purgeAfter)).toList(), db.count(actor.userId()), page, size));
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:list')")
    public CaseStatus status(AccountPrincipal actor, String id) {
        AssessmentCase row = owned(actor, db.find(validId(id)), false);
        return new CaseStatus(id, row.processingStatus, row.stage, "FAILED_RETRYABLE".equals(row.processingStatus),
                row.safeErrorCode, row.safeErrorCode == null ? null : "Processing failed. Use the permitted retry action.",
                row.cleanupStatus, row.updatedAt);
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:upload')")
    public OperationResult<JsonNode> upload(AccountPrincipal actor, String id, String key, String filename, byte[] data)
            throws java.io.IOException {
        owned(actor, db.find(validId(id)), false);
        files.validate(filename, data);
        String staged = files.stage(id, data);
        boolean[] retained = {false};
        try {
            return mutation(actor, key, id, "UPLOAD", Map.of("filename", filename, "sha256", hash(data)), row -> {
                state(row, "AWAITING_UPLOAD");
                changed(db.insertArtifact(row.id, staged, data.length, hash(data), row.purgeAfter));
                row.processingStatus = "EXTRACTING"; row.stage = "EXTRACTION"; row.uploadedAt = now(); save(row);
                enqueue(row, "CASE_EXTRACT");
                retained[0] = true;
                return new Applied(row, 202, action(row));
            });
        } finally {
            if (!retained[0]) files.discard(staged);
        }
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:edit')")
    public ReviewDraft draft(AccountPrincipal actor, String id) {
        return tx.execute(ignored -> {
            AssessmentCase row = owned(actor, db.find(validId(id)), false); state(row, "REVIEW_REQUIRED");
            JsonNode draft = read(row.draftJson);
            List<String> unresolved = matching.unresolved(draft, job(row), requirements(row), bundle(row), currentMonth());
            return new ReviewDraft(id, row.revisionNo, draft.path("professional"), strings(draft.path("confirmedAbsent")), unresolved);
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:edit')")
    public OperationResult<JsonNode> saveDraft(AccountPrincipal actor, String id, String key, UpdateDraftRequest body) {
        return mutation(actor, key, id, "EDIT", body, row -> {
            state(row, "REVIEW_REQUIRED"); revision(row, body.revision());
            row.draftJson = matching.draft(body.professional(), body.confirmedAbsent(), read(row.draftJson)).toString();
            save(row); return new Applied(row, 200, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:edit')")
    public ResearchAttributes attributes(AccountPrincipal actor, String id) {
        return tx.execute(ignored -> {
            AssessmentCase row = owned(actor, db.find(validId(id)), false);
            return new ResearchAttributes(id, row.revisionNo, db.attributes(row.id).stream()
                    .map(a -> new ResearchAttribute(a.type(), a.value(), a.source(), a.confirmed())).toList());
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:edit')")
    public OperationResult<JsonNode> saveAttributes(AccountPrincipal actor, String id, String key, AttributesRequest body) {
        return mutation(actor, key, id, "ATTRIBUTES", body, row -> {
            state(row, "REVIEW_REQUIRED"); revision(row, body.revision());
            JsonNode schema = bundle(row).schema().path("researchAttributes"); Set<String> seen = new HashSet<>();
            for (AttributeInput value : body.attributes()) {
                if (!schema.has(value.type()) || !seen.add(value.type())
                        || !CaseConfiguration.contains(schema.path(value.type()), value.value())) {
                    throw new IllegalArgumentException("Unsupported or duplicate research attribute.");
                }
            }
            db.deleteAttributes(row.id);
            for (String type : List.of("SCHOOL_CATEGORY", "REFERRAL", "GENDER", "ETHNICITY")) {
                String value = body.attributes().stream().filter(a -> a.type().equals(type))
                        .map(AttributeInput::value).findFirst().orElse("UNKNOWN");
                changed(db.insertAttribute(row.id, type, value));
            }
            save(row); return new Applied(row, 200, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:confirm')")
    public OperationResult<JsonNode> confirm(AccountPrincipal actor, String id, String key, ConfirmRequest body) {
        return mutation(actor, key, id, "CONFIRM", body, row -> {
            state(row, "REVIEW_REQUIRED"); revision(row, body.revision());
            if (!Boolean.TRUE.equals(body.confirmed())) throw new IllegalArgumentException("Explicit confirmation is required.");
            JsonNode draft = read(row.draftJson); CaseConfiguration.Bundle bundle = bundle(row);
            String referenceMonth = currentMonth();
            matching.calculate(draft, job(row), requirements(row), bundle, referenceMonth);
            changed(db.insertSnapshot(row.id, bundle.protocol().schemaVersion(), draft.path("professional").toString()));
            if (db.attributes(row.id).isEmpty()) for (String type : List.of("SCHOOL_CATEGORY", "REFERRAL", "GENDER", "ETHNICITY")) {
                changed(db.insertAttribute(row.id, type, "UNKNOWN"));
            }
            db.confirmAttributes(row.id);
            row.confirmedAbsentJson = draft.path("confirmedAbsent").toString(); row.draftJson = null;
            row.assessmentReferenceMonth = referenceMonth; row.confirmedAt = now(); row.purgeAfter = row.confirmedAt.plusDays(30);
            row.processingStatus = "CONFIRMED"; row.stage = "READY"; row.cleanupStatus = "PENDING";
            save(row); enqueue(row, "CASE_CLEANUP"); return new Applied(row, 200, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:run')")
    public OperationResult<JsonNode> run(AccountPrincipal actor, String id, String key) {
        return mutation(actor, key, id, "RUN", Map.of(), row -> {
            state(row, "CONFIRMED");
            CaseConfiguration.Bundle bundle = bundle(row);
            matching.calculate(snapshot(row), job(row), requirements(row), bundle, row.assessmentReferenceMonth);
            if (!predictions.available()) throw CaseException.dependency();
            List<Model> models = db.activeModels(row.protocolVersionId);
            Model neutral = selectModel(models, "NEUTRAL", bundle.protocol());
            Model biased = selectModel(models, "BIASED", bundle.protocol());
            row.neutralModelVersionId = neutral.id(); row.biasedModelVersionId = biased.id();
            row.processingStatus = "AUTOMATED_RUNNING"; row.stage = "MATCHING"; row.automatedStartedAt = now();
            save(row); enqueue(row, "CASE_ASSESS"); return new Applied(row, 202, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:run')")
    public OperationResult<JsonNode> retry(AccountPrincipal actor, String id, String key) {
        return mutation(actor, key, id, "RETRY", Map.of(), row -> {
            state(row, "FAILED_RETRYABLE");
            String type = row.confirmedAt == null ? "CASE_EXTRACT" : "CASE_ASSESS";
            if (type.equals("CASE_ASSESS") && !predictions.available()) throw CaseException.dependency();
            if (db.requeue(id, type) != 1) throw CaseException.conflict();
            row.processingStatus = row.confirmedAt == null ? "EXTRACTING" : "AUTOMATED_RUNNING";
            row.safeErrorCode = null; save(row); return new Applied(row, 202, action(row));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:results')")
    public FiveResults results(AccountPrincipal actor, String id) {
        return tx.execute(ignored -> {
            AssessmentCase row = owned(actor, db.find(validId(id)), false);
            if (row.confirmedAt == null) throw CaseException.conflict();
            JobVersion job = job(row);
            JsonNode professional = saved(row, "PROFESSIONAL"), neutral = saved(row, "NEUTRAL"), biased = saved(row, "BIASED");
            Map<String, HumanResult> humans = new LinkedHashMap<>();
            humans.put("standard", new HumanResult("Not assigned", null, null));
            humans.put("instructedBias", new HumanResult("Not assigned", null, null));
            for (Human human : db.humans(row.id)) {
                String slot = human.conditionType().equals("STANDARD") ? "standard" : "instructedBias";
                boolean submitted = human.status().equals("SUBMITTED");
                if (submitted && (!Set.of("Accept", "Reject").contains(human.decision()) || human.reason() == null
                        || human.reason().isBlank() || human.reason().length() > 2000)) throw CaseException.dependency();
                humans.put(slot, new HumanResult(submitted ? "Submitted" : "Pending",
                        submitted ? human.decision() : null, submitted ? human.reason() : null));
            }
            return new FiveResults(id, row.processingStatus,
                    new PublicJob(job.jobPublicId, job.publicId, job.versionNo, job.title),
                    publicObjective(professional), publicMl(neutral, false, row), publicMl(biased, true, row),
                    humans, new Privacy(row.purgeAfter, true));
        });
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:case:delete')")
    public OperationResult<JsonNode> delete(AccountPrincipal actor, String id, String key) {
        return mutation(actor, key, id, "DELETE", Map.of(), row -> {
            if (!"PURGING".equals(row.privacyStatus)) startPurge(row, "USER_REQUEST");
            return new Applied(row, 202, new DeleteResult(id, "PURGING"));
        });
    }

    private OperationResult<JsonNode> mutation(AccountPrincipal actor, String key, String id, String action,
            Object payload, Function<AssessmentCase, Applied> work) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("Invalid Idempotency-Key.");
        String hash = hash((action + ":" + id + ":" + write(payload)).getBytes(StandardCharsets.UTF_8));
        return tx.execute(ignored -> {
            authorize(actor, action);
            Receipt previous = db.receipt(actor.userId(), key);
            AssessmentCase row = id.isEmpty() ? null : db.lock(validId(id));
            if (previous != null) {
                if (previous.httpStatus() == 410) throw CaseException.missing();
                if (!previous.requestHash().equals(hash) || !previous.action().equals(action)
                        || !id.isEmpty() && !previous.casePublicId().equals(id)) throw CaseException.conflict();
                if (!action.equals("DELETE")) owned(actor, id.isEmpty() ? db.lock(previous.casePublicId()) : row, false);
                else if (row != null) owned(actor, row, true);
                return new OperationResult<>(previous.httpStatus(), read(previous.responseJson()));
            }
            if (!id.isEmpty()) owned(actor, row, action.equals("DELETE"));
            Applied applied = work.apply(row);
            JsonNode response = json.valueToTree(applied.data());
            changed(db.insertReceipt(actor.userId(), key, hash, applied.row().publicId, action, applied.status(), response.toString()));
            changed(db.audit(actor.userId(), "CASE_" + action, hash(applied.row().publicId.getBytes(StandardCharsets.UTF_8))));
            return new OperationResult<>(applied.status(), response);
        });
    }

    private void authorize(AccountPrincipal actor, String action) {
        String permission = switch (action) { case "ATTRIBUTES", "EDIT" -> "edit"; case "RETRY" -> "run";
            default -> action.toLowerCase(Locale.ROOT); };
        if (actor == null || db.lockActor(actor.userId(), actor.securityVersion()) == null
                || roles.findByUserId(actor.userId()).stream().noneMatch(r -> r.getRoleCode() == AccountRole.CANDIDATE)
                || !roles.findPermissionCodesByUserId(actor.userId()).contains("simrecrut:case:" + permission)) {
            throw new AccessDeniedException("Access denied.");
        }
    }
    private AssessmentCase owned(AccountPrincipal actor, AssessmentCase row, boolean deleting) {
        if (row == null || actor == null || row.candidateUserId != actor.userId()
                || !deleting && (!"ACTIVE".equals(row.privacyStatus) || !db.retained(row.id))) {
            throw CaseException.missing();
        }
        return row;
    }
    private void startPurge(AssessmentCase row, String reason) {
        row.privacyStatus = "PURGING"; row.draftJson = null; row.cleanupStatus = "PENDING"; row.deletionReason = reason;
        db.cancelTasks(row.publicId); db.cancelReviews(row.id); save(row); enqueue(row, "CASE_PURGE");
    }
    private CaseConfiguration.Bundle bundle(AssessmentCase row) { return configuration.load(job(row), db.protocol(row.protocolVersionId)); }
    private JobVersion job(AssessmentCase row) {
        JobVersion job = db.job(row.jobVersionId); if (job == null) throw CaseException.dependency(); return job;
    }
    private JsonNode requirements(AssessmentCase row) {
        ArrayNode values = json.createArrayNode(); db.requirements(row.jobVersionId).forEach(s -> values.add(read(s))); return values;
    }
    private JsonNode snapshot(AssessmentCase row) {
        ObjectNode result = json.createObjectNode(); result.set("professional", read(db.snapshot(row.id)));
        result.set("confirmedAbsent", read(row.confirmedAbsentJson)); return result;
    }
    private JsonNode saved(AssessmentCase row, String method) {
        String data = db.savedResult(row.id, method); return data == null ? null : read(data);
    }
    private JsonNode publicObjective(JsonNode result) {
        if (result == null) return null;
        ObjectNode data = json.createObjectNode();
        for (String field : List.of("score", "displayScore", "decision", "strengths", "gaps")) data.set(field, result.path(field));
        return data;
    }
    private JsonNode publicMl(JsonNode result, boolean biased, AssessmentCase row) {
        if (result == null) return null;
        ObjectNode data = json.createObjectNode(); data.set("acceptanceScore", result.path("acceptanceScore"));
        data.set("decision", result.path("decision"));
        if (biased) {
            ObjectNode values = data.putObject("attributeSummary");
            db.attributes(row.id).forEach(a -> values.put(a.type(), a.value()));
        }
        return data;
    }
    private Model selectModel(List<Model> models, String type, Protocol protocol) {
        List<Model> selected = models.stream().filter(m -> m.modelType().equals(type)
                && m.featureSchemaVersionId() == protocol.featureSchemaVersionId()
                && m.protocolVersionId() == protocol.id() && "ACTIVE".equals(m.status())).toList();
        if (selected.size() != 1) throw CaseException.dependency(); return selected.get(0);
    }
    private void save(AssessmentCase row) { changed(db.update(row)); row.revisionNo++; }
    private void enqueue(AssessmentCase row, String type) { changed(db.enqueue(ids.nextId(), row.publicId, type)); }
    private CaseAction action(AssessmentCase row) { return new CaseAction(row.publicId, row.processingStatus, row.revisionNo, row.purgeAfter); }
    private void state(AssessmentCase row, String required) { if (!required.equals(row.processingStatus)) throw CaseException.conflict(); }
    private void revision(AssessmentCase row, long value) { if (row.revisionNo != value) throw CaseException.conflict(); }
    private void changed(int count) { if (count != 1) throw CaseException.conflict(); }
    private String validId(String id) {
        if (id == null || !id.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw new IllegalArgumentException("Invalid case ID."); return id;
    }
    private List<String> strings(JsonNode values) {
        List<String> out = new ArrayList<>(); values.forEach(v -> out.add(v.asText())); return out;
    }
    private JsonNode read(String data) {
        try { return data == null ? json.nullNode() : json.readTree(data); }
        catch (Exception exception) { throw CaseException.dependency(); }
    }
    private String write(Object data) {
        try { return json.writeValueAsString(data); } catch (Exception exception) { throw CaseException.dependency(); }
    }
    public static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private String currentMonth() { return YearMonth.now(ZoneOffset.UTC).toString(); }
    private record Applied(AssessmentCase row, int status, Object data) { }
}
