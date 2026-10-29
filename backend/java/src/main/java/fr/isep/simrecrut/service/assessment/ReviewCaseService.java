package fr.isep.simrecrut.service.assessment;

import static fr.isep.simrecrut.dto.assessment.ReviewDtos.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.OperationResult;
import fr.isep.simrecrut.entity.assessment.*;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.mapper.assessment.*;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!resume-workspace")
public class ReviewCaseService {
    private final ReviewCaseMapper reviews;
    private final CandidateCaseMapper cases;
    private final RoleMapper roles;
    private final ObjectMapper json;
    private final PublicIdGenerator ids;
    private final CaseConfiguration configuration;
    private final TransactionTemplate tx;
    public ReviewCaseService(ReviewCaseMapper reviews, CandidateCaseMapper cases, RoleMapper roles,
            ObjectMapper json, PublicIdGenerator ids, CaseConfiguration configuration,
            PlatformTransactionManager transactions) {
        this.reviews = reviews; this.cases = cases; this.roles = roles; this.json = json;
        this.ids = ids; this.configuration = configuration; this.tx = new TransactionTemplate(transactions);
        // Recheck blind-review guards after the parent lock using the latest committed assignments.
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @PreAuthorize("hasRole('HR') and hasAuthority('simrecrut:hr-case:list')")
    public Page<HrCaseSummary> hrCases(AccountPrincipal actor, String jobId, int page, int size) {
        pagination(page, size); if (!jobId.isEmpty()) validId(jobId);
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:hr-case:list", AccountRole.HR);
            List<HrCaseSummary> items = new ArrayList<>();
            for (AssessmentCase selected : reviews.hrCases(actor.userId(), jobId, (page - 1) * size, size)) {
                AssessmentCase row = retained(selected.publicId);
                if (!reviews.hrAccess(row.id, actor.userId())) continue;
                JobVersion job = job(row);
                items.add(new HrCaseSummary(row.publicId, job.jobPublicId, job.publicId, job.title,
                        row.processingStatus, row.humanStatus, reviews.hasUnfinished(row.id, actor.userId()), row.purgeAfter));
            }
            return new Page<>(items, reviews.countHrCases(actor.userId(), jobId), page, size);
        });
    }

    @PreAuthorize("hasRole('HR') and hasAuthority('simrecrut:hr-case:detail')")
    public HrCaseDetail hrCase(AccountPrincipal actor, String id) {
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:hr-case:detail", AccountRole.HR);
            AssessmentCase row = retained(id);
            if (!reviews.hrAccess(row.id, actor.userId())) throw CaseException.missing();
            if (reviews.hasUnfinished(row.id, actor.userId())) {
                throw new CaseException(403, "BLIND_REVIEW_REQUIRED", "Complete all assigned conditions before viewing case results.");
            }
            JobVersion job = job(row);
            JsonNode professionalResult = read(cases.savedResult(row.id, "PROFESSIONAL"));
            JsonNode matches = professionalResult.path("professional");
            ObjectNode matching = json.createObjectNode();
            matching.set("values", matches.path("m")); matching.set("mandatoryPass", matches.path("g"));
            var protocol = reviews.protocolReference(row.protocolVersionId);
            if (protocol == null) throw CaseException.dependency();
            ObjectNode versions = json.createObjectNode().put("jobVersionId", job.publicId)
                    .put("matchingRuleVersion", job.matchingRuleVersion).put("protocolVersion", protocol.protocolVersion())
                    .put("featureSchemaVersion", protocol.featureSchemaVersion());
            modelVersion(versions, "neutralModelVersion", row.neutralModelVersionId);
            modelVersion(versions, "biasedModelVersion", row.biasedModelVersionId);
            List<HumanSummary> human = new ArrayList<>();
            List<HumanReview> primary = reviews.caseReviews(row.id);
            for (String condition : List.of("STANDARD", "INSTRUCTED_BIAS")) {
                HumanReview review = primary.stream().filter(h -> "PRIMARY".equals(h.purpose)
                        && condition.equals(h.conditionType) && !Set.of("CANCELLED", "EXPIRED").contains(h.status)).findFirst().orElse(null);
                boolean submitted = review != null && "SUBMITTED".equals(review.status);
                human.add(new HumanSummary(condition, review == null ? "NOT_ASSIGNED" : review.status,
                        submitted ? review.decision : null, submitted ? review.reason : null));
            }
            HrCaseDetail result = new HrCaseDetail(id, professional(row), publicJob(job), matching,
                    json.valueToTree(List.of(job.weightSkills, job.weightExperience, job.weightEducation,
                            job.weightLanguages, job.weightProjects)), matches.path("mw"),
                    project(professionalResult, "score", "displayScore", "decision", "strengths", "gaps"),
                    project(read(cases.savedResult(row.id, "NEUTRAL")), "acceptanceScore", "decision"),
                    project(read(cases.savedResult(row.id, "BIASED")), "acceptanceScore", "decision"), human, versions, row.purgeAfter);
            changed(cases.audit(actor.userId(), "HR_CASE_RESULTS_READ", CandidateCaseService.hash(id.getBytes(StandardCharsets.UTF_8))));
            return result;
        });
    }

    @PreAuthorize("hasAnyRole('HR','RESEARCHER') and hasAuthority('simrecrut:review:list')")
    public Page<TaskSummary> tasks(AccountPrincipal actor, String status, int page, int size) {
        pagination(page, size);
        if (!status.isEmpty() && !Set.of("PENDING", "OPENED", "SUBMITTED", "CANCELLED", "EXPIRED").contains(status)) {
            throw new IllegalArgumentException("Invalid review status.");
        }
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:review:list", AccountRole.HR, AccountRole.RESEARCHER);
            return new Page<>(reviews.tasks(actor.userId(), status, (page - 1) * size, size).stream()
                    .map(this::summary).toList(), reviews.countTasks(actor.userId(), status), page, size);
        });
    }

    @PreAuthorize("hasAnyRole('HR','RESEARCHER') and hasAuthority('simrecrut:review:detail')")
    public TaskDetail task(AccountPrincipal actor, String id) {
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:review:detail", AccountRole.HR, AccountRole.RESEARCHER);
            HumanReview found = assigned(actor, id);
            AssessmentCase row = retained(found.casePublicId);
            HumanReview review = activeReview(actor, row, id);
            if ("PENDING".equals(review.status)) { changed(reviews.opened(review.id)); review.status = "OPENED"; }
            JsonNode frozen = read(review.protocolText);
            if (!frozen.path("rubric").isObject()) throw CaseException.dependency();
            boolean biased = "INSTRUCTED_BIAS".equals(review.conditionType);
            boolean visible = "SUBMITTED".equals(review.status) && !reviews.hasUnfinished(row.id, actor.userId());
            JobVersion job = job(row);
            ObjectNode versions = json.createObjectNode().put("jobVersionId", job.publicId)
                    .put("matchingRuleVersion", job.matchingRuleVersion)
                    .put("assessmentReferenceMonth", row.assessmentReferenceMonth);
            versions.set("protocolVersion", frozen.path("protocolVersion"));
            versions.set("featureSchemaVersion", frozen.path("featureSchemaVersion"));
            return new TaskDetail(id, row.publicId, review.status, review.conditionType, review.purpose,
                    professional(row), publicJob(job), frozen.path("rubric"),
                    biased ? frozen.path("scenario") : json.nullNode(), biased ? review.instruction : null,
                    versions, visible, visible ? review.decision : null, visible ? review.reason : null,
                    visible ? review.submittedAt : null);
        });
    }

    @PreAuthorize("hasAnyRole('HR','RESEARCHER') and hasAuthority('simrecrut:review:submit')")
    public OperationResult<JsonNode> submit(AccountPrincipal actor, String id, String key, SubmitReviewRequest body) {
        validId(id);
        if (body == null || body.reason() == null || body.reason().isBlank() || body.reason().length() > 2000
                || !Set.of("ACCEPT", "REJECT", "Accept", "Reject").contains(body.decision() == null ? "" : body.decision())) {
            throw new IllegalArgumentException("A valid decision and reason are required.");
        }
        String hash = requestHash(key, "REVIEW_SUBMIT", id, body);
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:review:submit", AccountRole.HR, AccountRole.RESEARCHER);
            HumanReview found = assigned(actor, id);
            AssessmentCase row = retained(found.casePublicId);
            HumanReview review = activeReview(actor, row, id);
            OperationResult<JsonNode> previous = replay(actor, key, hash, "REVIEW_SUBMIT", row.publicId);
            if (previous != null) return previous;
            if (!Set.of("PENDING", "OPENED").contains(review.status)) throw CaseException.conflict();
            String decision = body.decision().equalsIgnoreCase("ACCEPT") ? "Accept" : "Reject";
            changed(reviews.submit(review.id, decision, body.reason().strip()));
            changed(reviews.refreshHumanStatus(row.id));
            HumanReview saved = reviews.lockReview(id);
            return receipt(actor, key, hash, row, "REVIEW_SUBMIT", 200,
                    new SubmissionResult(id, "SUBMITTED", saved.submittedAt));
        });
    }

    @PreAuthorize("hasRole('RESEARCHER') and hasAuthority('simrecrut:research:review:assign')")
    public List<EligibleReviewer> eligible(AccountPrincipal actor, String id, String query, int limit) {
        if (query == null || query.length() > 100 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid reviewer search.");
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:research:review:assign", AccountRole.RESEARCHER);
            AssessmentCase row = retained(id); researchScope(actor, row);
            return reviews.eligible(row.id, query, limit);
        });
    }

    @PreAuthorize("hasRole('RESEARCHER') and hasAuthority('simrecrut:research:review:assign')")
    public OperationResult<JsonNode> assign(AccountPrincipal actor, String id, String key, AssignReviewsRequest body) {
        validId(id);
        if (body == null || body.assignments() == null || body.assignments().isEmpty() || body.assignments().size() > 2) {
            throw new IllegalArgumentException("Assign one or two primary conditions.");
        }
        Set<String> conditions = new HashSet<>();
        for (Assignment value : body.assignments()) {
            if (value == null || !Set.of("STANDARD", "INSTRUCTED_BIAS").contains(value.condition() == null ? "" : value.condition())
                    || !conditions.add(value.condition())) throw new IllegalArgumentException("Duplicate or unsupported condition.");
            validId(value.reviewerId());
        }
        String hash = requestHash(key, "REVIEW_ASSIGN", id, body);
        return tx.execute(ignored -> {
            authorize(actor, "simrecrut:research:review:assign", AccountRole.RESEARCHER);
            AssessmentCase row = retained(id); researchScope(actor, row);
            OperationResult<JsonNode> previous = replay(actor, key, hash, "REVIEW_ASSIGN", id);
            if (previous != null) return previous;
            for (HumanReview existing : reviews.caseReviews(row.id)) if ("PRIMARY".equals(existing.purpose)
                    && conditions.contains(existing.conditionType)) throw CaseException.conflict();
            JobVersion job = job(row);
            var protocol = cases.protocol(row.protocolVersionId);
            JsonNode instructions = configuration.load(job, protocol).schema().path("reviewProtocol");
            List<TaskSummary> tasks = new ArrayList<>();
            Map<String, Long> reviewers = new TreeMap<>();
            for (Assignment value : body.assignments()) reviewers.put(value.reviewerId(), null);
            for (String reviewerId : reviewers.keySet()) {
                Long account = reviews.lockReviewer(reviewerId);
                if (account == null || !reviews.reviewerAccess(row.id, account) || reviews.hasResultExposure(row.id, account)) {
                    throw new CaseException(422, "REVIEWER_INELIGIBLE", "The selected reviewer is not eligible for this case.");
                }
                reviewers.put(reviewerId, account);
            }
            for (Assignment value : body.assignments()) {
                boolean biased = "INSTRUCTED_BIAS".equals(value.condition());
                JsonNode rubric = instructions.path(biased ? "instructedBiasRubric" : "standardRubric");
                if (!rubric.isObject() || rubric.isEmpty()) throw CaseException.dependency();
                String instruction = biased ? instructions.path("instructedBiasInstruction").asText("") : null;
                if (biased && (instruction.isBlank() || instruction.length() > 8000)) throw CaseException.dependency();
                ObjectNode frozen = json.createObjectNode().put("protocolVersion", protocol.versionCode())
                        .put("featureSchemaVersion", protocol.schemaVersion());
                frozen.set("rubric", rubric);
                if (biased) {
                    ObjectNode scenario = frozen.putObject("scenario");
                    for (var a : cases.attributes(row.id)) scenario.put(a.type(), a.value());
                    for (String type : List.of("SCHOOL_CATEGORY", "REFERRAL", "GENDER", "ETHNICITY")) {
                        if (!scenario.has(type)) scenario.put(type, "UNKNOWN");
                    }
                }
                HumanReview review = new HumanReview(); review.caseId = row.id; review.casePublicId = id;
                review.reviewerUserId = reviewers.get(value.reviewerId()); review.publicId = ids.nextId();
                review.conditionType = value.condition(); review.purpose = "PRIMARY"; review.status = "PENDING";
                review.protocolText = write(frozen); review.instruction = instruction;
                changed(reviews.insertReview(review));
                HumanReview saved = reviews.findReview(review.publicId); tasks.add(summary(saved));
            }
            changed(reviews.refreshHumanStatus(row.id));
            return receipt(actor, key, hash, row, "REVIEW_ASSIGN", 201, new AssignmentResult(tasks));
        });
    }

    private void authorize(AccountPrincipal actor, String permission, AccountRole... allowed) {
        if (actor == null || cases.lockActor(actor.userId(), actor.securityVersion()) == null
                || roles.findByUserId(actor.userId()).stream().noneMatch(r -> Arrays.asList(allowed).contains(r.getRoleCode()))
                || !roles.findPermissionCodesByUserId(actor.userId()).contains(permission)) throw new AccessDeniedException("Access denied.");
    }
    private AssessmentCase retained(String id) {
        AssessmentCase row = cases.lock(validId(id));
        if (row == null || row.confirmedAt == null || !"ACTIVE".equals(row.privacyStatus) || !cases.retained(row.id)) {
            throw CaseException.missing();
        }
        return row;
    }
    private void researchScope(AccountPrincipal actor, AssessmentCase row) {
        if (!reviews.researchAccess(row.id, actor.userId())) throw CaseException.missing();
    }
    private HumanReview assigned(AccountPrincipal actor, String id) {
        HumanReview review = reviews.findReview(validId(id));
        if (review == null || review.reviewerUserId != actor.userId()) throw CaseException.missing();
        return review;
    }
    private HumanReview activeReview(AccountPrincipal actor, AssessmentCase row, String id) {
        HumanReview review = reviews.lockReview(id);
        if (review == null || review.reviewerUserId != actor.userId() || !row.id.equals(review.caseId)
                || !reviews.reviewerAccess(row.id, actor.userId()) || Set.of("CANCELLED", "EXPIRED").contains(review.status)) {
            throw CaseException.missing();
        }
        return review;
    }
    private JobVersion job(AssessmentCase row) {
        JobVersion job = cases.job(row.jobVersionId); if (job == null) throw CaseException.dependency(); return job;
    }
    private JsonNode professional(AssessmentCase row) {
        JsonNode snapshot = read(cases.snapshot(row.id));
        if (!snapshot.isObject()) throw CaseException.dependency();
        ObjectNode result = json.createObjectNode();
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            ArrayNode facts = result.putArray(group);
            for (JsonNode raw : snapshot.path(group)) {
                ObjectNode fact = raw.deepCopy();
                fact.remove("evidence");
                facts.add(fact);
            }
        }
        return result;
    }
    private JsonNode publicJob(JobVersion job) {
        ObjectNode value = json.createObjectNode().put("jobId", job.jobPublicId).put("jobVersionId", job.publicId)
                .put("version", job.versionNo).put("title", job.title);
        value.set("requirements", read(job.publicRequirementsJson)); return value;
    }
    private void modelVersion(ObjectNode value, String field, Long id) {
        if (id == null) { value.putNull(field); return; }
        var model = cases.model(id); if (model == null) throw CaseException.dependency(); value.put(field, model.versionCode());
    }
    private TaskSummary summary(HumanReview review) {
        return new TaskSummary(review.publicId, review.casePublicId, review.jobTitle, review.conditionType,
                review.purpose, review.status, review.createdAt);
    }
    private JsonNode project(JsonNode source, String... fields) {
        if (source.isNull()) return json.nullNode(); ObjectNode result = json.createObjectNode();
        for (String field : fields) result.set(field, source.path(field)); return result;
    }
    private OperationResult<JsonNode> replay(AccountPrincipal actor, String key, String hash, String action, String id) {
        var stored = cases.receipt(actor.userId(), key); if (stored == null) return null;
        if (!hash.equals(stored.requestHash()) || !action.equals(stored.action()) || !id.equals(stored.casePublicId())) {
            throw CaseException.conflict();
        }
        return new OperationResult<>(stored.httpStatus(), read(stored.responseJson()));
    }
    private OperationResult<JsonNode> receipt(AccountPrincipal actor, String key, String hash, AssessmentCase row,
            String action, int status, Object response) {
        JsonNode data = json.valueToTree(response);
        changed(cases.insertReceipt(actor.userId(), key, hash, row.publicId, action, status, write(data)));
        changed(cases.audit(actor.userId(), action, CandidateCaseService.hash(row.publicId.getBytes(StandardCharsets.UTF_8))));
        return new OperationResult<>(status, data);
    }
    private String requestHash(String key, String action, String id, Object body) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("Invalid operation key.");
        return CandidateCaseService.hash((action + ":" + id + ":" + write(body)).getBytes(StandardCharsets.UTF_8));
    }
    private String validId(String id) {
        if (id == null || !id.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw new IllegalArgumentException("Invalid public ID."); return id;
    }
    private void pagination(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination.");
    }
    private void changed(int rows) { if (rows != 1) throw CaseException.conflict(); }
    private JsonNode read(String value) {
        try { return value == null ? json.nullNode() : json.readTree(value); }
        catch (Exception error) { throw CaseException.dependency(); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception error) { throw CaseException.dependency(); }
    }
}
