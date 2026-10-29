package fr.isep.simrecrut.assessment;

import static fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.Task;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.CaseException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

class CandidateCaseWorkflowTest {
    @TempDir Path temporary;
    CaseTestSupport f;
    @BeforeEach void setup() throws Exception { f = new CaseTestSupport(temporary); }
    @AfterEach void close() { f.worker.stop(); }

    @Test void creationFreezesPublishedJobAndProtocolAndReplaysWithoutDuplicates() {
        String key = key();
        var first = f.service.create(f.actor, key, new CreateCaseRequest(f.job.publicId));
        var second = f.service.create(f.actor, key, new CreateCaseRequest(f.job.publicId));
        assertEquals(201, first.status()); assertEquals(first.data().toString(), second.data().toString());
        assertEquals(1, f.cases.size());
        var row = f.cases.values().iterator().next();
        assertEquals(1L, row.jobVersionId); assertEquals(1L, row.protocolVersionId);
        assertEquals("AWAITING_UPLOAD", row.processingStatus);
        assertTrue(row.purgeAfter.isAfter(f.now().plusHours(23)));
        verify(f.db, times(1)).insert(any());
    }

    @Test void unavailableJobOrAmbiguousProtocolCannotCreateCase() {
        when(f.db.published(f.job.publicId)).thenReturn(null);
        assertThrows(CaseException.class, () -> f.service.create(f.actor, key(), new CreateCaseRequest(f.job.publicId)));
        when(f.db.published(f.job.publicId)).thenReturn(f.job);
        when(f.db.protocols(anyLong(), anyString())).thenReturn(List.of(f.protocol, f.protocol));
        assertThrows(CaseException.class, () -> f.service.create(f.actor, key(), new CreateCaseRequest(f.job.publicId)));
        assertTrue(f.cases.isEmpty());
    }

    @Test void revokedPermissionIsRecheckedEvenForAnIdempotentReplay() {
        String key = key(); f.service.create(f.actor, key, new CreateCaseRequest(f.job.publicId));
        when(f.roles.findPermissionCodesByUserId(1)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> f.service.create(f.actor, key, new CreateCaseRequest(f.job.publicId)));
    }

    @Test void otherOwnersAndExpiredCasesAreNotReadable() {
        String id = create();
        var other = new AccountPrincipal(2, "01DDDDDDDDDDDDDDDDDDDDDDDD", UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(600));
        assertEquals(404, assertThrows(CaseException.class, () -> f.service.status(other, id)).status());
        f.cases.get(id).purgeAfter = f.now().minusSeconds(1);
        assertEquals(404, assertThrows(CaseException.class, () -> f.service.status(f.actor, id)).status());
    }

    @Test void uploadQueuesRealExtractionAndDoesNotDuplicateWorkOnReplay() throws Exception {
        String id = create(), key = key(); byte[] pdf = pdf("Skills Java");
        assertEquals(202, f.service.upload(f.actor, id, key, "resume.pdf", pdf).status());
        f.service.upload(f.actor, id, key, "resume.pdf", pdf);
        assertEquals(1, f.tasks.size());
        f.worker.execute(f.task(id, "CASE_EXTRACT"));
        assertEquals("REVIEW_REQUIRED", f.service.status(f.actor, id).processingStatus());
        var draft = f.service.draft(f.actor, id);
        assertTrue(draft.unresolvedRequiredFields().isEmpty());
        assertEquals("Java", draft.professional().path("skills").get(0).path("skillCode").asText());
        String encoded = f.json.writeValueAsString(draft);
        assertFalse(encoded.contains("relative_path")); assertFalse(encoded.contains("original_text"));
    }

    @Test void invalidOrEncryptedPdfDoesNotChangeCaseState() throws Exception {
        String id = create();
        assertThrows(IllegalArgumentException.class, () -> f.service.upload(f.actor, id, key(), "resume.pdf", new byte[]{1,2,3}));
        assertThrows(IllegalArgumentException.class, () -> f.service.upload(f.actor, id, key(), "resume.txt", pdf("Java")));
        assertEquals("AWAITING_UPLOAD", f.cases.get(id).processingStatus);
        verify(f.db, never()).insertArtifact(anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test void correctionsUseServerProvenanceAndStaleDraftCannotOverwrite() throws Exception {
        String id = reviewed(); var current = f.service.draft(f.actor, id);
        JsonNode professional = current.professional().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) professional.path("skills").get(0)).put("skillCode", "Python");
        var saved = f.service.saveDraft(f.actor, id, key(), new UpdateDraftRequest(professional, current.revision(), List.of()));
        assertTrue(saved.data().path("revision").asLong() > current.revision());
        assertEquals("USER_SUPPLIED", f.service.draft(f.actor, id).professional().path("skills").get(0).path("evidence").path("source").asText());
        assertThrows(CaseException.class, () -> f.service.saveDraft(f.actor, id, key(),
                new UpdateDraftRequest(professional, current.revision(), List.of())));
    }

    @Test void researchAttributesStaySeparateAndUnknownIsNeverGuessed() throws Exception {
        String id = reviewed(); long revision = f.service.draft(f.actor, id).revision();
        f.service.saveAttributes(f.actor, id, key(), new AttributesRequest(List.of(new AttributeInput("GENDER", "FEMALE")), revision));
        var values = f.service.attributes(f.actor, id).attributes();
        assertEquals(4, values.size()); assertEquals(3, values.stream().filter(a -> a.value().equals("UNKNOWN")).count());
        assertFalse(f.service.draft(f.actor, id).professional().toString().contains("FEMALE"));
        assertThrows(IllegalArgumentException.class, () -> f.service.saveAttributes(f.actor, id, key(),
                new AttributesRequest(List.of(new AttributeInput("GENDER", "INFERRED")), revision + 1)));
    }

    @Test void confirmationLocksFactsBeforeAsynchronousOriginalCleanup() throws Exception {
        String id = reviewed(); long revision = f.service.draft(f.actor, id).revision();
        String key = key(); var body = new ConfirmRequest(revision, true);
        f.service.confirm(f.actor, id, key, body); f.service.confirm(f.actor, id, key, body);
        assertEquals("CONFIRMED", f.cases.get(id).processingStatus);
        assertNull(f.cases.get(id).draftJson); assertEquals(1, f.snapshots.size());
        assertTrue(f.cases.get(id).purgeAfter.isAfter(f.now().plusDays(29)));
        assertThrows(CaseException.class, () -> f.service.draft(f.actor, id));
        f.worker.execute(f.task(id, "CASE_CLEANUP"));
        assertEquals("COMPLETE", f.cases.get(id).cleanupStatus);
        assertFalse(java.nio.file.Files.exists(temporary.resolve(id)));
    }

    @Test void missingModelTransportBlocksRunWithoutDiscardingConfirmedFacts() throws Exception {
        String id = confirmed(); when(f.predictions.available()).thenReturn(false);
        assertEquals(503, assertThrows(CaseException.class, () -> f.service.run(f.actor, id, key())).status());
        assertEquals("CONFIRMED", f.cases.get(id).processingStatus);
        assertNotNull(f.snapshots.get(f.cases.get(id).id));
        verify(f.db, never()).activeModels(anyLong());
    }

    @Test void completeAssessmentSavesRealStagesAndReturnsOnlyPublicResults() throws Exception {
        String id = confirmed(); f.service.run(f.actor, id, key());
        f.worker.execute(f.task(id, "CASE_ASSESS"));
        var result = f.service.results(f.actor, id);
        assertEquals("AUTOMATED_COMPLETE", result.automatedStatus());
        assertEquals(100, result.objective().path("displayScore").asInt());
        assertEquals(0.8, result.neutralMl().path("acceptanceScore").asDouble());
        assertEquals("Not assigned", result.human().get("standard").status());
        assertNull(result.human().get("standard").decision());
        String data = f.json.writeValueAsString(result);
        assertFalse(data.contains("modelThreshold")); assertFalse(data.contains("logit"));
        assertFalse(data.contains("\"professional\"")); assertFalse(data.contains("\"reviewer"));
        verify(f.db).writeMatching(anyLong(), eq(1L)); verify(f.db).writeObjective(anyLong(), eq(1L), any());
    }

    @Test void retryPreservesSuccessfulStagesAndOriginalModelVersions() throws Exception {
        String id = confirmed(); AtomicBoolean fail = new AtomicBoolean(true);
        doAnswer(i -> {
            var model = (CandidateCaseMapper.Model) i.getArgument(1);
            if (model.modelType().equals("BIASED") && fail.getAndSet(false)) throw CaseException.dependency();
            return f.prediction(i.getArgument(0), model);
        }).when(f.predictions).predict(anyString(), any(), any(), any(), any());
        f.service.run(f.actor, id, key()); f.worker.execute(f.task(id, "CASE_ASSESS"));
        assertEquals("FAILED_RETRYABLE", f.cases.get(id).processingStatus);
        var partial = f.service.results(f.actor, id);
        assertNotNull(partial.objective()); assertNotNull(partial.neutralMl()); assertNull(partial.biasedMl());
        f.service.retry(f.actor, id, key()); f.worker.execute(f.task(id, "CASE_ASSESS"));
        assertEquals("AUTOMATED_COMPLETE", f.cases.get(id).processingStatus);
        verify(f.predictions, times(1)).predict(eq(id), eq(f.neutral), any(), any(), any());
        verify(f.db, times(1)).activeModels(1); verify(f.db, times(1)).writeMatching(anyLong(), anyLong());
    }

    @Test void deletionDuringInferenceRevokesWritesAndPurgeRemovesRelatedContent() throws Exception {
        String id = confirmed(); String deleteKey = key();
        doAnswer(i -> {
            f.service.delete(f.actor, id, deleteKey); return f.prediction(i.getArgument(0), i.getArgument(1));
        }).when(f.predictions).predict(anyString(), any(), any(), any(), any());
        f.service.run(f.actor, id, key()); f.worker.execute(f.task(id, "CASE_ASSESS"));
        assertEquals("PURGING", f.cases.get(id).privacyStatus);
        assertThrows(CaseException.class, () -> f.service.results(f.actor, id));
        assertNull(f.results.get(f.cases.get(id).id + ":NEUTRAL"));
        f.worker.execute(f.task(id, "CASE_PURGE"));
        assertFalse(f.cases.containsKey(id));
        assertEquals("DELETED", f.service.delete(f.actor, id, deleteKey).data().path("status").asText());
        verify(f.db).deleteVariantReviews(anyLong()); verify(f.db).deleteSubjects(anyLong());
        verify(f.db).deleteSnapshots(anyLong()); verify(f.db).deleteMl(anyLong());
    }

    @Test void lostLeaseCannotSavePrediction() throws Exception {
        String id = confirmed(); f.service.run(f.actor, id, key());
        doAnswer(i -> {
            Task task = f.task(id, "CASE_ASSESS");
            f.tasks.put(task.id(), new Task(task.id(), id, task.taskType(), "RUNNING", task.attemptCount(),
                    "replacement-token", f.now().plusMinutes(3), 3));
            return f.prediction(i.getArgument(0), i.getArgument(1));
        }).when(f.predictions).predict(anyString(), any(), any(), any(), any());
        f.worker.execute(f.task(id, "CASE_ASSESS"));
        assertNull(f.results.get(f.cases.get(id).id + ":NEUTRAL"));
        verify(f.db, never()).writeMl(anyLong(), anyString(), any());
    }

    @Test void retentionMaintenanceHidesExpiredContentAndQueuesPurge() {
        String id = create(); f.cases.get(id).purgeAfter = f.now().minusSeconds(1);
        when(f.db.maintenanceCases()).thenReturn(List.of(f.copy(f.cases.get(id))));
        f.worker.maintenance();
        assertEquals("PURGING", f.cases.get(id).privacyStatus);
        assertEquals("RETENTION_EXPIRY", f.cases.get(id).deletionReason);
        assertEquals("CASE_PURGE", f.task(id, "CASE_PURGE").taskType());
    }

    @Test void purgedCreationKeyCannotRecreateDeletedContent() {
        String creationKey = key();
        String id = f.service.create(f.actor, creationKey, new CreateCaseRequest(f.job.publicId)).data().path("caseId").asText();
        f.service.delete(f.actor, id, key()); f.worker.execute(f.task(id, "CASE_PURGE"));
        assertEquals(404, assertThrows(CaseException.class,
                () -> f.service.create(f.actor, creationKey, new CreateCaseRequest(f.job.publicId))).status());
        assertTrue(f.cases.isEmpty()); assertEquals("0".repeat(64), f.receipts.get(creationKey).requestHash());
        assertEquals("{\"deleted\":true}", f.receipts.get(creationKey).responseJson());
    }

    @Test void expiredWorkerLeaseCanBeReclaimedWithoutReupload() throws Exception {
        String id = create(); f.service.upload(f.actor, id, key(), "resume.pdf", pdf("Skills Java"));
        Task pending = f.task(id, "CASE_EXTRACT");
        f.tasks.put(pending.id(), new Task(pending.id(), id, pending.taskType(), "RUNNING", 1,
                UUID.randomUUID().toString(), f.now().minusSeconds(10), 3));
        f.worker.execute(f.tasks.get(pending.id()));
        assertEquals("REVIEW_REQUIRED", f.cases.get(id).processingStatus);
        assertEquals(2, f.tasks.get(pending.id()).attemptCount());
        verify(f.db, times(1)).insertArtifact(anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test void mapperStatementsParseWithVersionedResultAndDeletionDependencies() throws Exception {
        var config = new org.apache.ibatis.session.Configuration();
        String resource = "mapper/assessment/CandidateCaseMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        assertTrue(config.hasStatement(CandidateCaseMapper.class.getName() + ".writeMatching"));
        assertTrue(config.hasStatement(CandidateCaseMapper.class.getName() + ".deleteVariantReviews"));
    }

    private String create() { return f.service.create(f.actor, key(), new CreateCaseRequest(f.job.publicId)).data().path("caseId").asText(); }
    private String reviewed() throws Exception {
        String id = create(); f.service.upload(f.actor, id, key(), "resume.pdf", pdf("Skills Java"));
        f.worker.execute(f.task(id, "CASE_EXTRACT")); return id;
    }
    private String confirmed() throws Exception {
        String id = reviewed(); f.service.confirm(f.actor, id, key(), new ConfirmRequest(f.service.draft(f.actor, id).revision(), true)); return id;
    }
    private String key() { return UUID.randomUUID().toString(); }
    static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 750); content.showText(text + " professional software engineering resume"); content.endText();
            }
            document.save(output); return output.toByteArray();
        }
    }
}
