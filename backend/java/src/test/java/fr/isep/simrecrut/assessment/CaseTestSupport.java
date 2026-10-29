package fr.isep.simrecrut.assessment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.domain.assessment.CaseMatchingEngine;
import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.gateway.ml.CasePredictionGateway;
import fr.isep.simrecrut.integration.pdf.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.*;
import fr.isep.simrecrut.worker.assessment.CandidateCaseWorker;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Isolated database doubles; tests never connect to a configured application database. */
class CaseTestSupport {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final CandidateCaseMapper db = mock(CandidateCaseMapper.class);
    final RoleMapper roles = mock(RoleMapper.class);
    final CaseConfiguration config = mock(CaseConfiguration.class);
    final CasePredictionGateway predictions = mock(CasePredictionGateway.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final Map<String, AssessmentCase> cases = new LinkedHashMap<>();
    final Map<String, Receipt> receipts = new HashMap<>();
    final Map<Long, String> snapshots = new HashMap<>(), artifacts = new HashMap<>();
    final Map<String, String> results = new HashMap<>();
    final Map<Long, List<Attribute>> attributes = new HashMap<>();
    final Map<Long, Task> tasks = new LinkedHashMap<>();
    final AccountPrincipal actor = new AccountPrincipal(1, "01AAAAAAAAAAAAAAAAAAAAAAAA",
            UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(3600));
    final JobVersion job = new JobVersion();
    final Protocol protocol = new Protocol(1, 1, 1, "PROTOCOL_1", new BigDecimal("0.7"), "SCHEMA_1", "schema.json", "hash", 32);
    final Model neutral = new Model(1, 1, 1, "NEUTRAL_1", "NEUTRAL", 0.5, 0.5, "ACTIVE");
    final Model biased = new Model(2, 1, 1, "BIASED_1", "BIASED", 0.5, 0.5, "ACTIVE");
    final CaseMatchingEngine engine = new CaseMatchingEngine(json);
    final CaseFileStore files;
    final CandidateCaseService service;
    final CandidateCaseWorker worker;
    final CaseConfiguration.Bundle bundle;
    long caseSequence, taskSequence;

    CaseTestSupport(Path directory) throws Exception {
        files = new CaseFileStore(directory.toString());
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        job.id = 1L; job.jobId = 1L; job.matchingRuleVersionId = 1L; job.publicId = "01BBBBBBBBBBBBBBBBBBBBBBBB";
        job.jobPublicId = "01CCCCCCCCCCCCCCCCCCCCCCCC"; job.title = "Software Engineer"; job.versionNo = 1;
        job.ruleStatus = "FROZEN"; job.activeCriteriaJson = "[\"SKILLS\"]";
        job.weightSkills = BigDecimal.ONE; job.weightExperience = job.weightEducation = job.weightLanguages = job.weightProjects = BigDecimal.ZERO;
        JsonNode rule = json.readTree("""
            {"catalogs":{"skillCodes":["Java","Python"],"roleFamilyCodes":["SOFTWARE_ENGINEERING"],
            "degreeLevelCodes":["BACHELOR","MASTER"],"subjectCodes":["COMPUTER_SCIENCE"],
            "languageCodes":["EN"],"languageLevelCodes":["B1","C1"],"conditionCodes":["API_DEVELOPMENT"]},
            "degreeOrder":["BACHELOR","MASTER"],"languageLevelOrder":["B1","C1"]}
            """);
        JsonNode schema = json.readTree("""
            {"researchAttributes":{"SCHOOL_CATEGORY":["UNKNOWN"],"REFERRAL":["UNKNOWN","YES","NO"],
            "GENDER":["UNKNOWN","FEMALE","MALE"],"ETHNICITY":["UNKNOWN","MENA"]}}
            """);
        bundle = new CaseConfiguration.Bundle(rule, schema, protocol);
        when(config.load(any(), any())).thenReturn(bundle);
        when(db.published(job.publicId)).thenReturn(job); when(db.job(1)).thenReturn(job);
        when(db.protocol(1)).thenReturn(protocol); when(db.protocols(eq(1L), anyString())).thenReturn(List.of(protocol));
        when(db.activeModels(1)).thenReturn(List.of(neutral, biased));
        when(db.model(1)).thenReturn(neutral); when(db.model(2)).thenReturn(biased);
        when(predictions.available()).thenReturn(true);
        when(db.requirements(1)).thenReturn(List.of("""
            {"criterionType":"SKILLS","requirementCode":"JAVA","assessmentIncluded":true,
            "mandatory":false,"payload":{"assessedSkillCodes":["Java"]}}
            """));
        when(db.lockActor(1, 0)).thenReturn(1L);
        Role role = new Role(); role.setRoleCode(AccountRole.CANDIDATE);
        when(roles.findByUserId(1)).thenReturn(List.of(role));
        when(roles.findPermissionCodesByUserId(1)).thenReturn(List.of("list", "create", "upload", "edit", "confirm", "run", "results", "delete")
                .stream().map(s -> "simrecrut:case:" + s).toList());
        when(db.insert(any())).thenAnswer(i -> {
            AssessmentCase row = i.getArgument(0); row.id = ++caseSequence; row.createdAt = row.updatedAt = now();
            cases.put(row.publicId, copy(row)); attributes.put(row.id, new ArrayList<>()); return 1;
        });
        when(db.find(anyString())).thenAnswer(i -> copy(cases.get(i.getArgument(0))));
        when(db.lock(anyString())).thenAnswer(i -> copy(cases.get(i.getArgument(0))));
        when(db.retained(anyLong())).thenAnswer(i -> cases.values().stream().anyMatch(r -> r.id.equals(i.getArgument(0))
                && r.privacyStatus.equals("ACTIVE") && r.purgeAfter.isAfter(now())));
        when(db.update(any())).thenAnswer(i -> {
            AssessmentCase row = i.getArgument(0), old = cases.get(row.publicId);
            if (old == null || !old.revisionNo.equals(row.revisionNo)) return 0;
            AssessmentCase next = copy(row); next.revisionNo++; next.updatedAt = now(); cases.put(next.publicId, next); return 1;
        });
        when(db.receipt(eq(1L), anyString())).thenAnswer(i -> receipts.get(i.getArgument(1)));
        when(db.insertReceipt(eq(1L), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenAnswer(i -> {
            receipts.put(i.getArgument(1), new Receipt(i.getArgument(2), i.getArgument(3), i.getArgument(4), i.getArgument(5), i.getArgument(6))); return 1;
        });
        when(db.insertArtifact(anyLong(), anyString(), anyLong(), anyString(), any())).thenAnswer(i -> {
            artifacts.put(i.getArgument(0), i.getArgument(1)); return 1;
        });
        when(db.artifact(anyLong())).thenAnswer(i -> artifacts.get(i.getArgument(0)));
        when(db.insertSnapshot(anyLong(), anyString(), anyString())).thenAnswer(i -> { snapshots.put(i.getArgument(0), i.getArgument(2)); return 1; });
        when(db.snapshot(anyLong())).thenAnswer(i -> snapshots.get(i.getArgument(0)));
        when(db.attributes(anyLong())).thenAnswer(i -> new ArrayList<>(attributes.getOrDefault(i.getArgument(0), List.of())));
        when(db.deleteAttributes(anyLong())).thenAnswer(i -> { attributes.put(i.getArgument(0), new ArrayList<>()); return 1; });
        when(db.insertAttribute(anyLong(), anyString(), anyString())).thenAnswer(i -> {
            String value = i.getArgument(2); attributes.computeIfAbsent(i.getArgument(0), k -> new ArrayList<>())
                    .add(new Attribute(i.getArgument(1), value, value.equals("UNKNOWN") ? "UNKNOWN" : "USER_SUPPLIED", false)); return 1;
        });
        when(db.confirmAttributes(anyLong())).thenAnswer(i -> {
            long id = i.getArgument(0); attributes.put(id, attributes.get(id).stream()
                    .map(a -> new Attribute(a.type(), a.value(), a.source(), true)).toList()); return 1;
        });
        when(db.enqueue(anyString(), anyString(), anyString())).thenAnswer(i -> {
            long id = ++taskSequence; tasks.put(id, new Task(id, i.getArgument(1), i.getArgument(2), "QUEUED", 0, null, null, 3)); return 1;
        });
        when(db.lockTask(anyLong())).thenAnswer(i -> tasks.get(i.getArgument(0)));
        when(db.claim(anyLong(), anyString(), anyString())).thenAnswer(i -> {
            Task t = tasks.get(i.getArgument(0)); if (t == null || t.attemptCount() >= t.maxAttempts()
                    || !t.status().equals("QUEUED") && !(t.status().equals("RUNNING") && t.leaseUntil().isBefore(now()))) return 0;
            tasks.put(t.id(), new Task(t.id(), t.resourcePublicId(), t.taskType(), "RUNNING", t.attemptCount() + 1,
                    i.getArgument(1), now().plusMinutes(3), t.maxAttempts())); return 1;
        });
        when(db.heartbeat(anyLong(), anyString())).thenAnswer(i -> {
            Task t = tasks.get(i.getArgument(0)); return t != null && Objects.equals(t.leaseToken(), i.getArgument(1))
                    && t.status().equals("RUNNING") && t.leaseUntil().isAfter(now()) ? 1 : 0;
        });
        when(db.finishTask(anyLong(), anyString(), anyString())).thenAnswer(i -> {
            Task t = tasks.get(i.getArgument(0)); if (t == null || !Objects.equals(t.leaseToken(), i.getArgument(1))) return 0;
            tasks.put(t.id(), new Task(t.id(), t.resourcePublicId(), t.taskType(), i.getArgument(2), t.attemptCount(), null, null, 3)); return 1;
        });
        when(db.requeue(anyString(), anyString())).thenAnswer(i -> {
            for (Task t : new ArrayList<>(tasks.values())) if (t.resourcePublicId().equals(i.getArgument(0))
                    && t.taskType().equals(i.getArgument(1)) && t.status().equals("RETRYABLE") && t.attemptCount() < 3) {
                tasks.put(t.id(), new Task(t.id(), t.resourcePublicId(), t.taskType(), "QUEUED", t.attemptCount(), null, null, 3)); return 1;
            }
            return 0;
        });
        when(db.cancelTasks(anyString())).thenAnswer(i -> {
            tasks.replaceAll((k,t) -> t.resourcePublicId().equals(i.getArgument(0))
                    ? new Task(t.id(), t.resourcePublicId(), t.taskType(), "CANCELLED", t.attemptCount(), null, null, 3) : t); return 1;
        });
        when(db.savedResult(anyLong(), anyString())).thenAnswer(i -> results.get(i.getArgument(0) + ":" + i.getArgument(1)));
        when(db.saveResult(anyLong(), anyString(), anyString())).thenAnswer(i -> { results.put(i.getArgument(0) + ":" + i.getArgument(1), i.getArgument(2)); return 1; });
        when(db.writeMatching(anyLong(), anyLong())).thenReturn(1);
        when(db.writeObjective(anyLong(), anyLong(), any())).thenReturn(1);
        when(db.writeMl(anyLong(), anyString(), any())).thenReturn(1);
        when(db.audit(anyLong(), anyString(), anyString())).thenReturn(1);
        when(db.humans(anyLong())).thenReturn(List.of());
        when(db.lockExperiments(anyLong())).thenReturn(List.of());
        when(db.deleteCase(anyLong())).thenAnswer(i -> { cases.values().removeIf(r -> r.id.equals(i.getArgument(0))); return 1; });
        when(db.redactOperations(anyString())).thenAnswer(i -> {
            receipts.replaceAll((key,r) -> r.casePublicId().equals(i.getArgument(0)) && !r.action().equals("DELETE")
                    ? new Receipt("0".repeat(64), r.casePublicId(), r.action(), 410, "{\"deleted\":true}") : r); return 1;
        });
        when(db.completeDeletionReceipts(anyString(), anyString())).thenAnswer(i -> {
            receipts.replaceAll((key,r) -> r.casePublicId().equals(i.getArgument(0)) && r.action().equals("DELETE")
                    ? new Receipt(r.requestHash(), r.casePublicId(), r.action(), 200, i.getArgument(1)) : r); return 1;
        });
        when(db.deleteTasks(anyString())).thenAnswer(i -> { tasks.values().removeIf(t -> t.resourcePublicId().equals(i.getArgument(0))); return 1; });
        when(predictions.predict(anyString(), any(), any(), any(), any())).thenAnswer(i -> prediction(i.getArgument(0), i.getArgument(1)));
        service = new CandidateCaseService(db, roles, json, new PublicIdGenerator(), files, config, engine, predictions, transactions, "");
        worker = new CandidateCaseWorker(db, json, files, new LocalResumeCleaner(), engine, config, predictions, new PublicIdGenerator(), transactions);
    }
    AssessmentCase copy(AssessmentCase row) { return row == null ? null : json.convertValue(row, AssessmentCase.class); }
    LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    Task task(String caseId, String type) { return tasks.values().stream().filter(t -> t.resourcePublicId().equals(caseId) && t.taskType().equals(type)).findFirst().orElseThrow(); }
    ObjectNode prediction(String caseId, Model model) {
        return json.createObjectNode().put("requestId", UUID.randomUUID().toString()).put("caseId", caseId)
                .put("modelVersion", model.versionCode()).put("featureSchemaVersion", protocol.schemaVersion())
                .put("logit", 1).put("acceptanceScore", 0.8).put("modelThreshold", model.threshold())
                .put("commonThreshold", model.commonThreshold()).put("decision", "Accept")
                .put("commonThresholdDecision", "Accept").put("status", "SUCCESS");
    }
}
