package fr.isep.simrecrut.assessment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.dto.assessment.ReviewDtos.*;
import fr.isep.simrecrut.entity.assessment.*;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.mapper.assessment.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

class ReviewCaseWorkflowTest {
    @TempDir Path directory;
    CaseTestSupport f;
    ReviewCaseMapper db;
    ReviewCaseService service;
    AssessmentCase row;
    AccountPrincipal actor;
    Map<String, HumanReview> reviews;
    Map<String, Receipt> receipts;
    String caseId = "01DDDDDDDDDDDDDDDDDDDDDDDD";
    String reviewerId = "01EEEEEEEEEEEEEEEEEEEEEEEE";
    @BeforeEach void setup() throws Exception {
        f = new CaseTestSupport(directory); db = mock(ReviewCaseMapper.class);
        actor = new AccountPrincipal(2, "01FFFFFFFFFFFFFFFFFFFFFFFF", UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(3600));
        Role hr = new Role(); hr.setRoleCode(AccountRole.HR); Role research = new Role(); research.setRoleCode(AccountRole.RESEARCHER);
        when(f.roles.findByUserId(2)).thenReturn(List.of(hr, research));
        when(f.roles.findPermissionCodesByUserId(2)).thenReturn(List.of("simrecrut:hr-case:list", "simrecrut:hr-case:detail",
                "simrecrut:review:list", "simrecrut:review:detail", "simrecrut:review:submit", "simrecrut:research:review:assign"));
        when(f.db.lockActor(2, 0)).thenReturn(2L);
        row = new AssessmentCase(); row.id = 10L; row.publicId = caseId; row.candidateUserId = 1L;
        row.jobVersionId = row.protocolVersionId = 1L; row.processingStatus = "AUTOMATED_COMPLETE";
        row.humanStatus = "NOT_ASSIGNED"; row.privacyStatus = "ACTIVE"; row.confirmedAt = LocalDateTime.now();
        row.purgeAfter = LocalDateTime.now().plusDays(30); f.cases.put(caseId, row);
        f.snapshots.put(row.id, "{\"skills\":[{\"skillCode\":\"Java\",\"evidence\":{\"text\":\"Private name\"}}],\"experience\":[],\"education\":[],\"languages\":[],\"projects\":[]}");
        f.job.publicRequirementsJson = "[{\"criterionType\":\"SKILLS\",\"payload\":{\"assessedSkillCodes\":[\"Java\"]}}]";
        f.job.matchingRuleVersion = "RULE_1";
        ObjectNode protocol = ((ObjectNode) f.bundle.schema()).putObject("reviewProtocol");
        protocol.putObject("standardRubric").put("guidance", "Assess professional qualifications.");
        protocol.putObject("instructedBiasRubric").put("guidance", "Assess the complete experimental scenario.");
        protocol.put("instructedBiasInstruction", "Research simulation only: follow the frozen experimental instruction.");
        when(db.hrAccess(10, 2)).thenReturn(true); when(db.researchAccess(10, 2)).thenReturn(true);
        when(db.protocolReference(1)).thenReturn(new ReviewCaseMapper.ProtocolReference("PROTOCOL_1", "SCHEMA_1"));
        when(db.reviewerAccess(eq(10L), anyLong())).thenReturn(true); when(db.lockReviewer(reviewerId)).thenReturn(3L);
        reviews = new LinkedHashMap<>(); receipts = new HashMap<>();
        when(db.caseReviews(10)).thenAnswer(i -> new ArrayList<>(reviews.values()));
        when(db.findReview(anyString())).thenAnswer(i -> reviews.get(i.getArgument(0)));
        when(db.lockReview(anyString())).thenAnswer(i -> reviews.get(i.getArgument(0)));
        when(db.insertReview(any())).thenAnswer(i -> {
            HumanReview review = i.getArgument(0); review.id = (long) reviews.size() + 1;
            review.createdAt = LocalDateTime.now(); review.jobTitle = f.job.title;
            reviews.put(review.publicId, review); return 1;
        });
        when(db.refreshHumanStatus(10)).thenReturn(1);
        when(db.opened(anyLong())).thenReturn(1);
        when(db.submit(anyLong(), anyString(), anyString())).thenAnswer(i -> {
            HumanReview review = reviews.values().stream().filter(r -> r.id.equals(i.getArgument(0))).findFirst().orElseThrow();
            review.status = "SUBMITTED"; review.decision = i.getArgument(1); review.reason = i.getArgument(2);
            review.submittedAt = LocalDateTime.now(); return 1;
        });
        when(db.hasUnfinished(anyLong(), anyLong())).thenAnswer(i -> reviews.values().stream()
                .anyMatch(r -> r.caseId.equals(i.getArgument(0)) && r.reviewerUserId.equals(i.getArgument(1))
                        && Set.of("PENDING", "OPENED").contains(r.status)));
        when(f.db.receipt(eq(2L), anyString())).thenAnswer(i -> receipts.get(i.getArgument(1)));
        when(f.db.insertReceipt(eq(2L), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenAnswer(i -> {
            receipts.put(i.getArgument(1), new Receipt(i.getArgument(2), i.getArgument(3), i.getArgument(4), i.getArgument(5), i.getArgument(6))); return 1;
        });
        when(f.db.audit(eq(2L), anyString(), anyString())).thenReturn(1);
        service = new ReviewCaseService(db, f.db, f.roles, f.json, new PublicIdGenerator(), f.config, f.transactions);
    }
    @AfterEach void close() { f.worker.stop(); }

    @Test void hrDetailRequiresCurrentCaseAccess() {
        when(db.hrAccess(10, 2)).thenReturn(false);
        assertEquals(404, assertThrows(CaseException.class, () -> service.hrCase(actor, caseId)).status());
        verify(f.db, never()).snapshot(anyLong());
    }
    @Test void unfinishedReviewerCannotUseHrDetailAsResultBypass() throws Exception {
        ownReview("STANDARD");
        assertEquals(403, assertThrows(CaseException.class, () -> service.hrCase(actor, caseId)).status());
        verify(f.db, never()).savedResult(anyLong(), anyString());
    }
    @Test void hrDetailProjectsOnlyPermittedFieldsAndAuditsExposure() {
        f.results.put("10:PROFESSIONAL", "{\"score\":0.8,\"decision\":\"Accept\",\"professional\":{\"m\":[1,0,0,0,0],\"mw\":[1,0,0,0,0],\"g\":1},\"internal\":\"private\"}");
        f.results.put("10:NEUTRAL", "{\"acceptanceScore\":0.8,\"decision\":\"Accept\",\"logit\":999,\"threshold\":0.5}");
        String response = f.json.valueToTree(service.hrCase(actor, caseId)).toString();
        assertFalse(response.contains("Private name")); assertFalse(response.contains("evidence"));
        assertFalse(response.contains("logit")); assertFalse(response.contains("threshold"));
        assertFalse(response.contains("reviewerUserId")); assertFalse(response.contains("internal"));
        verify(f.db).audit(eq(2L), eq("HR_CASE_RESULTS_READ"), anyString());
        verify(f.transactions).getTransaction(argThat(definition -> definition.getIsolationLevel()
                == org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED));
        verify(f.db, never()).protocol(anyLong());
    }
    @Test void standardTaskOmitsScenarioAndAllEvaluationOutputs() throws Exception {
        HumanReview review = ownReview("STANDARD");
        JsonNode response = f.json.valueToTree(service.task(actor, review.publicId));
        assertTrue(response.path("scenario").isNull()); assertTrue(response.path("instruction").isNull());
        assertFalse(response.toString().contains("Private name"));
        assertFalse(response.has("objective")); assertFalse(response.has("weights")); assertFalse(response.has("human"));
        assertEquals("OPENED", response.path("status").asText());
        verify(db).opened(review.id);
    }
    @Test void biasedTaskGetsFrozenScenarioAndInstruction() throws Exception {
        HumanReview review = ownReview("INSTRUCTED_BIAS");
        TaskDetail detail = service.task(actor, review.publicId);
        assertEquals("YES", detail.scenario().path("REFERRAL").asText());
        assertEquals("Frozen scenario instruction", detail.instruction());
        assertNull(detail.decision());
    }
    @Test void otherReviewerAndRevokedAccessCannotOpenOrSubmit() throws Exception {
        HumanReview review = ownReview("STANDARD"); review.reviewerUserId = 3L;
        assertEquals(404, assertThrows(CaseException.class, () -> service.task(actor, review.publicId)).status());
        review.reviewerUserId = 2L; when(db.reviewerAccess(10, 2)).thenReturn(false);
        assertEquals(404, assertThrows(CaseException.class, () -> service.submit(actor, review.publicId, key(), new SubmitReviewRequest("ACCEPT", "Reason"))).status());
        verify(db, never()).submit(anyLong(), anyString(), anyString());
    }
    @Test void submissionIsAtomicInServiceBoundaryAndSameKeyReplaysWithoutResaving() throws Exception {
        HumanReview review = ownReview("STANDARD"); String key = key(); var body = new SubmitReviewRequest("ACCEPT", "  Fits the requirements.  ");
        var response = service.submit(actor, review.publicId, key, body);
        assertEquals("SUBMITTED", response.data().path("status").asText());
        assertEquals("Accept", review.decision); assertEquals("Fits the requirements.", review.reason);
        assertEquals(response, service.submit(actor, review.publicId, key, body));
        verify(db, times(1)).submit(anyLong(), anyString(), anyString());
        verify(db).refreshHumanStatus(10);
        assertEquals(409, assertThrows(CaseException.class, () -> service.submit(actor, review.publicId, key(), new SubmitReviewRequest("REJECT", "Changed"))).status());
        assertFalse(receipts.get(key).responseJson().contains("Fits the requirements"));
    }
    @Test void conflictingOperationKeyAndInvalidReasonDoNotOverwriteAnswer() throws Exception {
        HumanReview review = ownReview("STANDARD"); String key = key();
        service.submit(actor, review.publicId, key, new SubmitReviewRequest("Reject", "Missing required experience."));
        assertEquals(409, assertThrows(CaseException.class, () -> service.submit(actor, review.publicId, key, new SubmitReviewRequest("Accept", "Different"))).status());
        assertThrows(IllegalArgumentException.class, () -> service.submit(actor, review.publicId, key(), new SubmitReviewRequest("ACCEPT", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.submit(actor, review.publicId, key(), new SubmitReviewRequest("MAYBE", "Reason")));
        assertEquals("Reject", review.decision);
    }
    @Test void dualConditionReviewerCannotRetrieveEarlierAnswerUntilBothSubmissions() throws Exception {
        HumanReview first = ownReview("STANDARD"), second = ownReview("INSTRUCTED_BIAS");
        service.submit(actor, first.publicId, key(), new SubmitReviewRequest("ACCEPT", "Fits."));
        TaskDetail hidden = service.task(actor, first.publicId);
        assertFalse(hidden.submissionVisible()); assertNull(hidden.decision()); assertNull(hidden.reason());
        assertEquals(403, assertThrows(CaseException.class, () -> service.hrCase(actor, caseId)).status());
        service.submit(actor, second.publicId, key(), new SubmitReviewRequest("REJECT", "Experimental scenario."));
        assertTrue(service.task(actor, first.publicId).submissionVisible());
        assertEquals("Accept", service.task(actor, first.publicId).decision());
        assertDoesNotThrow(() -> service.hrCase(actor, caseId));
    }
    @Test void deletedExpiredAndCancelledCasesCannotBeSubmittedEvenOnReplay() throws Exception {
        HumanReview review = ownReview("STANDARD"); String key = key(); var body = new SubmitReviewRequest("ACCEPT", "Fits.");
        service.submit(actor, review.publicId, key, body);
        row.privacyStatus = "PURGING";
        assertEquals(404, assertThrows(CaseException.class, () -> service.submit(actor, review.publicId, key, body)).status());
        row.privacyStatus = "ACTIVE"; row.purgeAfter = LocalDateTime.now().minusDays(1);
        assertEquals(404, assertThrows(CaseException.class, () -> service.task(actor, review.publicId)).status());
        row.purgeAfter = LocalDateTime.now().plusDays(1); review.status = "CANCELLED";
        assertEquals(404, assertThrows(CaseException.class, () -> service.task(actor, review.publicId)).status());
    }
    @Test void assignmentFreezesBothConditionsAndIsIdempotent() throws Exception {
        String key = key(); var request = new AssignReviewsRequest(List.of(new Assignment(reviewerId, "STANDARD"), new Assignment(reviewerId, "INSTRUCTED_BIAS")));
        var result = service.assign(actor, caseId, key, request);
        assertEquals(201, result.status()); assertEquals(2, result.data().path("tasks").size());
        assertEquals(result, service.assign(actor, caseId, key, request));
        verify(db, times(2)).insertReview(any());
        HumanReview standard = reviews.values().stream().filter(r -> r.conditionType.equals("STANDARD")).findFirst().orElseThrow();
        HumanReview biased = reviews.values().stream().filter(r -> r.conditionType.equals("INSTRUCTED_BIAS")).findFirst().orElseThrow();
        assertFalse(standard.protocolText.contains("scenario")); assertNull(standard.instruction);
        assertEquals(4, f.json.readTree(biased.protocolText).path("scenario").size());
        assertNotNull(biased.instruction);
        assertEquals(409, assertThrows(CaseException.class, () -> service.assign(actor, caseId, key(), request)).status());
    }
    @Test void assignmentRechecksEligibilityIncludingPriorResultExposure() {
        when(db.reviewerAccess(10, 3)).thenReturn(false);
        var request = new AssignReviewsRequest(List.of(new Assignment(reviewerId, "STANDARD")));
        assertEquals(422, assertThrows(CaseException.class, () -> service.assign(actor, caseId, key(), request)).status());
        when(db.reviewerAccess(10, 3)).thenReturn(true); when(db.hasResultExposure(10, 3)).thenReturn(true);
        assertEquals(422, assertThrows(CaseException.class, () -> service.assign(actor, caseId, key(), request)).status());
        verify(db, never()).insertReview(any());
    }
    @Test void researcherNeedsExplicitGrantAndFrozenReviewConfiguration() {
        var request = new AssignReviewsRequest(List.of(new Assignment(reviewerId, "STANDARD")));
        when(db.researchAccess(10, 2)).thenReturn(false);
        assertEquals(404, assertThrows(CaseException.class, () -> service.assign(actor, caseId, key(), request)).status());
        when(db.researchAccess(10, 2)).thenReturn(true); ((ObjectNode) f.bundle.schema()).remove("reviewProtocol");
        assertEquals(503, assertThrows(CaseException.class, () -> service.assign(actor, caseId, key(), request)).status());
        verify(db, never()).insertReview(any());
    }
    @Test void currentPermissionAndAccountAreRecheckedAtServiceBoundary() {
        when(f.roles.findPermissionCodesByUserId(2)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> service.hrCase(actor, caseId));
        verify(db, never()).hrAccess(anyLong(), anyLong());
    }
    @Test void auditFailureRollsBackSubmissionTransaction() throws Exception {
        HumanReview review = ownReview("STANDARD");
        when(f.db.audit(eq(2L), eq("REVIEW_SUBMIT"), anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Unavailable"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.submit(actor, review.publicId, key(), new SubmitReviewRequest("ACCEPT", "Fits.")));
        verify(f.transactions).rollback(any());
        verify(f.transactions, never()).commit(any());
    }
    @Test void listsValidateFiltersAndDoNotExposeDecisions() throws Exception {
        HumanReview review = ownReview("STANDARD");
        when(db.tasks(2, "PENDING", 0, 20)).thenReturn(List.of(review)); when(db.countTasks(2, "PENDING")).thenReturn(1L);
        var page = service.tasks(actor, "PENDING", 1, 20);
        assertEquals(1, page.total()); assertFalse(f.json.valueToTree(page).toString().contains("decision"));
        assertThrows(IllegalArgumentException.class, () -> service.tasks(actor, "INVALID", 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.hrCases(actor, "", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.eligible(actor, caseId, "", 101));
    }
    private HumanReview ownReview(String condition) throws Exception {
        HumanReview review = new HumanReview(); review.id = (long) reviews.size() + 1; review.publicId = new PublicIdGenerator().nextId();
        review.caseId = 10L; review.casePublicId = caseId; review.reviewerUserId = 2L; review.jobTitle = f.job.title;
        review.conditionType = condition; review.purpose = "PRIMARY"; review.status = "PENDING";
        review.protocolText = "{\"rubric\":{\"guidance\":\"Assess qualifications.\"},\"scenario\":{\"REFERRAL\":\"YES\"}}";
        review.instruction = "Frozen scenario instruction"; reviews.put(review.publicId, review); return review;
    }
    private String key() { return UUID.randomUUID().toString(); }
}
