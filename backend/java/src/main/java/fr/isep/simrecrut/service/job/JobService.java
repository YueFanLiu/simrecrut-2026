package fr.isep.simrecrut.service.job;

import static fr.isep.simrecrut.dto.job.JobDtos.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.mapper.job.JobMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

@Service
@Profile("!resume-workspace")
public class JobService {
    private final JobMapper db;
    private final ObjectMapper json;
    private final PublicIdGenerator ids;
    private final JobRules rules;
    private final JobRequirementExtractor extractor;
    private final TransactionTemplate tx;
    private final String ruleCode;

    public JobService(JobMapper db, ObjectMapper json, PublicIdGenerator ids, JobRules rules,
            JobRequirementExtractor extractor, PlatformTransactionManager transactions,
            @Value("${simrecrut.jobs.matching-rule-version:}") String ruleCode) {
        this.db = db;
        this.json = json;
        this.ids = ids;
        this.rules = rules;
        this.extractor = extractor;
        this.tx = new TransactionTemplate(transactions);
        this.ruleCode = ruleCode;
    }

    public Page<JsonNode> list(AccountPrincipal actor, boolean candidate, int page, int size, String keyword) {
        access(candidate ? "CANDIDATE" : "HR", candidate ? "browse" : "list");
        if (page < 1 || size < 1 || size > 100 || page > 100000 || keyword.length() > 100) {
            throw new IllegalArgumentException("Invalid pagination or search.");
        }
        Long owner = candidate ? null : actor.userId();
        return tx.execute(status -> new Page<>(db.list(owner, keyword, (page - 1) * size, size).stream()
                .map(row -> summary(row, candidate)).toList(), db.count(owner, keyword), page, size));
    }

    public JsonNode detail(AccountPrincipal actor, String jobId, boolean candidate) {
        access(candidate ? "CANDIDATE" : "HR", candidate ? "browse" : "list");
        id(jobId);
        return tx.execute(status -> {
            JobVersion row = candidate ? db.published(jobId) : ownedDraft(actor, jobId);
            if (row == null) throw missing();
            return candidate ? publicDetail(row) : detail(row);
        });
    }

    public List<JsonNode> versions(AccountPrincipal actor, String jobId) {
        access("HR", "list");
        return tx.execute(status -> {
            lock(actor, jobId);
            return db.versions(jobId).stream().map(row -> summary(row, false)).toList();
        });
    }

    public JsonNode version(AccountPrincipal actor, String jobId, String versionId) {
        access("HR", "list");
        id(versionId);
        return tx.execute(status -> {
            lock(actor, jobId);
            JobVersion row = db.version(jobId, versionId);
            if (row == null) throw missing();
            return detail(row);
        });
    }

    public JsonNode create(AccountPrincipal actor, String key, CreateRequest body) {
        access("HR", "create");
        return mutation(actor, key, "create", "", body, () -> {
            if (ruleCode.isBlank()) throw new JobException(503, "Configure the matching rule version first.");
            JobVersion row = db.rule(ruleCode);
            rules.catalogue(row);
            row.jobPublicId = ids.nextId();
            row.publicId = ids.nextId();
            row.ownerUserId = actor.userId();
            row.createdBy = actor.userId();
            row.title = body.title().trim();
            row.description = body.description().trim();
            row.versionNo = 1;
            row.revisionNo = 1L;
            row.status = "DRAFT";
            row.extractionStatus = "IDLE";
            row.createdAt = LocalDateTime.now(ZoneOffset.UTC);
            row.updatedAt = row.createdAt;
            row.activeCriteriaJson = "[]";
            row.publicRequirementsJson = "[]";
            setWeights(row, new Weights(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            db.insertJob(row);
            db.insertVersion(row);
            return detail(row);
        });
    }

    public JsonNode update(AccountPrincipal actor, String jobId, String key, UpdateRequest body) {
        access("HR", "edit");
        return mutation(actor, key, "update", jobId, body, () -> {
            JobVersion row = editable(actor, jobId, body.revision());
            boolean changed = !row.description.equals(body.description().trim());
            if (changed) {
                List<Requirement> invalidated = requirements(row).stream().map(r -> new Requirement(r.criterionType(),
                        r.requirementCode(), r.payload(), r.assessmentIncluded(), r.mandatory(), r.publicVisible(), false,
                        null, null, List.of("The description changed. Review this requirement against the new source."))).toList();
                replace(row, invalidated);
                row.extractionStatus = "IDLE";
            }
            row.title = body.title().trim();
            row.description = body.description().trim();
            save(row);
            return detail(row);
        });
    }

    public JsonNode saveRequirements(AccountPrincipal actor, String jobId, String key, RequirementsRequest body) {
        access("HR", "edit");
        return mutation(actor, key, "requirements", jobId, body, () -> {
            JobVersion row = editable(actor, jobId, body.revision());
            checkRequirements(body.requirements(), row.description);
            replace(row, body.requirements());
            save(row);
            return detail(row);
        });
    }

    public JsonNode saveWeights(AccountPrincipal actor, String jobId, String key, WeightsRequest body) {
        access("HR", "edit");
        if (new HashSet<>(body.activeCriteria()).size() != body.activeCriteria().size()) {
            throw new IllegalArgumentException("Duplicate assessment area.");
        }
        BigDecimal sum = body.weights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ONE) != 0) throw new IllegalArgumentException("Weights must total exactly one.");
        for (Criterion criterion : Criterion.values()) {
            if (!body.activeCriteria().contains(criterion) && body.weights().values().get(criterion.ordinal()).signum() != 0) {
                throw new IllegalArgumentException("Inactive criteria require zero weight.");
            }
        }
        return mutation(actor, key, "weights", jobId, body, () -> {
            JobVersion row = editable(actor, jobId, body.revision());
            setWeights(row, body.weights());
            row.activeCriteriaJson = write(body.activeCriteria());
            save(row);
            return detail(row);
        });
    }

    public Validation validate(AccountPrincipal actor, String jobId) {
        access("HR", "publish");
        return tx.execute(status -> validation(ownedDraft(actor, jobId)));
    }

    public JsonNode publish(AccountPrincipal actor, String jobId, String key, RevisionRequest body) {
        access("HR", "publish");
        return mutation(actor, key, "publish", jobId, body, () -> {
            JobVersion row = editable(actor, jobId, body.revision());
            Validation report = validation(row);
            if (!report.valid()) throw new JobException(409, "The draft is not ready for publication.",
                    Map.of("errors", report.errors(), "warnings", report.warnings()));
            row.publicRequirementsJson = write(publicRequirements(row));
            row.status = "PUBLISHED";
            row.publishedBy = actor.userId();
            row.publishedAt = LocalDateTime.now(ZoneOffset.UTC);
            save(row);
            db.updateJob(row.jobId, row.title, true);
            return detail(row);
        });
    }

    public JsonNode cloneDraft(AccountPrincipal actor, String jobId, String key, CloneRequest body) {
        access("HR", "create");
        return mutation(actor, key, "clone", jobId, body, () -> {
            lock(actor, jobId);
            JobVersion existing = db.draft(jobId);
            if (existing != null) throw new JobException(409, "An editable draft already exists.",
                    Map.of("jobId", jobId, "draftId", existing.publicId, "revision", existing.revisionNo));
            JobVersion row = db.version(jobId, body.sourceJobVersionId());
            if (row == null || !"PUBLISHED".equals(row.status)) throw missing();
            rules.catalogue(row);
            List<Requirement> copied = requirements(row);
            row.id = null;
            row.publicId = ids.nextId();
            row.versionNo = db.nextVersion(row.jobId);
            row.revisionNo = 1L;
            row.status = "DRAFT";
            row.createdBy = actor.userId();
            row.publishedBy = null;
            row.publishedAt = null;
            row.extractionStatus = "IDLE";
            row.extractionToken = null;
            row.extractionStartedAt = null;
            row.extractionError = null;
            row.createdAt = LocalDateTime.now(ZoneOffset.UTC);
            row.updatedAt = row.createdAt;
            row.publicRequirementsJson = "[]";
            db.insertVersion(row);
            replace(row, copied);
            return detail(row);
        });
    }

    public JsonNode extract(AccountPrincipal actor, String jobId, String key) {
        access("HR", "edit");
        return mutation(actor, key, "extract", jobId, Map.of(), () -> {
            JobVersion row = ownedDraft(actor, jobId);
            if ("RUNNING".equals(row.extractionStatus)) throw new JobException(409, "Extraction is already running.");
            if (!requirements(row).isEmpty()) throw new JobException(409, "Remove existing requirements before extraction.");
            extractor.requireConfigured();
            JsonNode catalogue = rules.catalogue(row);
            row.extractionStatus = "RUNNING";
            row.extractionToken = UUID.randomUUID().toString();
            row.extractionStartedAt = LocalDateTime.now(ZoneOffset.UTC);
            row.extractionError = null;
            save(row);
            String token = row.extractionToken;
            long revision = row.revisionNo;
            String description = row.description;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        extractor.submit(() -> {
                            try { finishExtraction(actor, jobId, token, revision, extractor.extract(description, catalogue)); }
                            catch (Exception error) {
                                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                                finishExtraction(actor, jobId, token, revision, null);
                            }
                        });
                    } catch (RuntimeException error) { finishExtraction(actor, jobId, token, revision, null); }
                }
            });
            return extraction(row);
        });
    }

    public JsonNode extractionStatus(AccountPrincipal actor, String jobId) {
        access("HR", "list");
        return tx.execute(status -> {
            JobVersion row = ownedDraft(actor, jobId);
            if ("RUNNING".equals(row.extractionStatus) && row.extractionStartedAt != null
                    && row.extractionStartedAt.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))) {
                row.extractionStatus = "FAILED_RETRYABLE";
                row.extractionError = "EXTRACTION_INTERRUPTED";
                row.extractionToken = null;
                save(row);
            }
            return extraction(row);
        });
    }

    private void finishExtraction(AccountPrincipal actor, String jobId, String token, long revision, List<Requirement> result) {
        // afterCommit still has the old connection bound; start an independent transaction explicitly.
        TransactionTemplate workerTx = new TransactionTemplate(tx.getTransactionManager());
        workerTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        workerTx.executeWithoutResult(status -> {
            if (db.lockActor(actor.userId()) == null) return;
            if (db.lockJob(jobId, actor.userId()) == null) return;
            JobVersion row = db.draft(jobId);
            if (row == null || !Objects.equals(token, row.extractionToken) || row.revisionNo != revision) return;
            if (result != null) {
                try { checkRequirements(result, row.description); }
                catch (IllegalArgumentException invalid) { resultFailure(row); save(row); return; }
                replace(row, result);
                row.extractionStatus = "COMPLETE";
                row.extractionError = null;
            } else resultFailure(row);
            row.extractionToken = null;
            save(row);
        });
    }

    private void resultFailure(JobVersion row) {
        row.extractionStatus = "FAILED_RETRYABLE";
        row.extractionError = "EXTRACTION_FAILED";
        row.extractionToken = null;
    }

    private JsonNode mutation(AccountPrincipal actor, String key, String action, String jobId,
            Object request, Supplier<JsonNode> operation) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("Invalid operation key.");
        String hash = hash(action + ":" + jobId + ":" + canonical(json.valueToTree(request)));
        return tx.execute(status -> {
            if (db.lockActor(actor.userId()) == null) throw new AccessDeniedException("Inactive account.");
            Map<String, Object> receipt = db.receipt(actor.userId(), key);
            if (receipt != null) {
                if (!hash.equals(receipt.get("requestHash"))) throw new JobException(409, "Operation key already used for another request.");
                lock(actor, (String) receipt.get("jobPublicId"));
                return read(receipt.get("responseJson").toString());
            }
            JsonNode result = operation.get();
            db.insertReceipt(actor.userId(), key, hash, result.path("jobId").asText(), write(result));
            return result;
        });
    }

    private String canonical(JsonNode value) {
        if (value.isObject()) {
            TreeMap<String, String> entries = new TreeMap<>();
            value.fields().forEachRemaining(e -> entries.put(e.getKey(), canonical(e.getValue())));
            return entries.entrySet().stream().map(e -> write(e.getKey()) + ":" + e.getValue())
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value.isArray()) {
            List<String> entries = new ArrayList<>(); value.forEach(e -> entries.add(canonical(e)));
            return String.join(",", entries).transform(s -> "[" + s + "]");
        }
        return value.toString();
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private void access(String role, String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + role))
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("simrecrut:job:" + permission))) {
            throw new AccessDeniedException("Access denied.");
        }
    }

    private void id(String value) {
        if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw new IllegalArgumentException("Invalid job ID.");
    }

    private void lock(AccountPrincipal actor, String jobId) {
        id(jobId);
        if (db.lockJob(jobId, actor.userId()) == null) throw missing();
    }

    private JobException missing() { return new JobException(404, "The requested job or version is unavailable."); }

    private JobVersion ownedDraft(AccountPrincipal actor, String jobId) {
        lock(actor, jobId);
        JobVersion row = db.draft(jobId);
        if (row == null) throw new JobException(409, "This job has no editable draft. Create one from a published version.");
        return row;
    }

    private JobVersion editable(AccountPrincipal actor, String jobId, long revision) {
        JobVersion row = ownedDraft(actor, jobId);
        if (row.revisionNo != revision) throw new JobException(409, "The draft changed. Reload before saving.",
                Map.of("revision", row.revisionNo));
        if ("RUNNING".equals(row.extractionStatus)) throw new JobException(409, "Wait for extraction before editing.");
        return row;
    }

    private void save(JobVersion row) {
        if (db.updateDraft(row) != 1) throw new JobException(409, "The draft changed. Reload before continuing.");
        row.revisionNo++;
        row.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void checkRequirements(List<Requirement> rows, String description) {
        Set<String> codes = new HashSet<>();
        if (rows.size() > 100) throw new IllegalArgumentException("Too many requirements.");
        for (Requirement row : rows) {
            rules.checkShape(row);
            if (!codes.add(row.requirementCode())) throw new IllegalArgumentException("Duplicate requirement code.");
            if (row.evidenceExcerpt() != null && !row.evidenceExcerpt().isBlank()
                    && !description.contains(row.evidenceExcerpt())) throw new IllegalArgumentException("Evidence is not in the description.");
        }
    }

    private void replace(JobVersion row, List<Requirement> rows) {
        db.deleteRequirements(row.id);
        int order = 0;
        for (Requirement r : rows) db.insertRequirement(row.id, r.criterionType().name(), r.requirementCode(),
                write(r.payload()), r.mandatory(), r.publicVisible(), r.assessmentIncluded(), r.reviewed(),
                r.evidenceExcerpt(), r.evidenceLocation(), write(r.warnings()), order++);
    }

    private List<Requirement> requirements(JobVersion row) {
        return db.requirements(row.id).stream().map(value -> {
            try { return json.readValue(value, Requirement.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new JobException(503, "Stored job requirements are invalid."); }
        }).toList();
    }

    private Validation validation(JobVersion row) {
        return rules.validate(row, requirements(row), read(row.activeCriteriaJson), weights(row));
    }

    private Weights weights(JobVersion row) {
        return new Weights(row.weightSkills, row.weightExperience, row.weightEducation, row.weightLanguages, row.weightProjects);
    }

    private void setWeights(JobVersion row, Weights values) {
        row.weightSkills = values.skills(); row.weightExperience = values.experience();
        row.weightEducation = values.education(); row.weightLanguages = values.languages(); row.weightProjects = values.projects();
    }

    private List<JsonNode> publicRequirements(JobVersion row) {
        return requirements(row).stream().filter(Requirement::publicVisible).map(r -> {
            ObjectNode result = json.createObjectNode().put("criterionType", r.criterionType().name())
                    .put("requirementCode", r.requirementCode()).put("mandatory", r.mandatory());
            result.set("payload", r.payload());
            return (JsonNode) result;
        }).toList();
    }

    private JsonNode publicDetail(JobVersion row) {
        ObjectNode result = (ObjectNode) summary(row, true);
        result.put("description", row.description);
        result.set("requirements", read(row.publicRequirementsJson));
        return result;
    }

    private JsonNode summary(JobVersion row, boolean candidate) {
        ObjectNode result = json.createObjectNode().put("jobId", row.jobPublicId)
                .put("jobVersionId", row.publicId).put("title", row.title)
                .put("version", row.versionNo).put("currentVersion", row.versionNo)
                .put("summary", row.description.substring(0, Math.min(300, row.description.length())));
        result.set("publishedAt", json.valueToTree(utc(row.publishedAt)));
        if (!candidate) {
            result.put("status", row.status).put("revision", row.revisionNo);
            result.set("createdAt", json.valueToTree(utc(row.createdAt)));
        }
        return result;
    }

    private JsonNode detail(JobVersion row) {
        ObjectNode result = (ObjectNode) summary(row, false);
        result.put("description", row.description).put("matchingRuleVersion", row.matchingRuleVersion);
        result.set("requirements", json.valueToTree(requirements(row)));
        result.set("activeCriteria", read(row.activeCriteriaJson));
        result.set("weights", json.valueToTree(weights(row)));
        result.set("extraction", extraction(row));
        return result;
    }

    private JsonNode extraction(JobVersion row) {
        ObjectNode result = json.createObjectNode().put("jobId", row.jobPublicId).put("revision", row.revisionNo)
                .put("status", row.extractionStatus).put("stage", "REQUIREMENT_EXTRACTION")
                .put("retryable", "FAILED_RETRYABLE".equals(row.extractionStatus));
        result.set("errorCode", json.valueToTree(row.extractionError));
        result.set("message", json.valueToTree(row.extractionError == null ? null : "Extraction did not finish. Retry or review manually."));
        result.set("updatedAt", json.valueToTree(utc(row.updatedAt)));
        return result;
    }

    private String utc(LocalDateTime value) { return value == null ? null : value.atOffset(ZoneOffset.UTC).toString(); }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new JobException(503, "Stored job data is invalid."); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalArgumentException("Invalid job data."); }
    }
}
