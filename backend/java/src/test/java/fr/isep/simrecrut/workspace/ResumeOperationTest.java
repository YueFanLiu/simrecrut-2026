package fr.isep.simrecrut.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Check durable replay, payload conflicts and deletion boundaries. */
class ResumeOperationTest {
    @TempDir Path directory;
    @Test void replaysCanonicalPayloadAcrossRestartAndRejectsKeyReuse() throws Exception {
        String key = "repeat-operation-1234";
        var body = Facts.JSON.createObjectNode().put("a", 1).put("b", 2);
        AtomicInteger calls = new AtomicInteger();
        ResumeService service = new ResumeService(directory);
        try {
            ResumeOperation.perform(service, key, "test", "", body, ignored -> { calls.incrementAndGet(); return Map.of("saved", true); });
        } finally { service.shutdown(); }
        service = new ResumeService(directory);
        try {
            var reordered = Facts.JSON.createObjectNode().put("b", 2).put("a", 1);
            ResumeOperation.perform(service, key, "test", "", reordered, ignored -> { calls.incrementAndGet(); return Map.of(); });
            assertEquals(1, calls.get());
            ResumeService current = service;
            assertThrows(IllegalArgumentException.class, () -> ResumeOperation.perform(current, key, "test", "", body.put("a", 3), ignored -> Map.of()));
        } finally { service.shutdown(); }
    }
    @Test void cachedImportCannotRestoreDeletedResumeAndDeleteRemainsIdempotent() throws Exception {
        ResumeService service = new ResumeService(directory);
        var body = Facts.JSON.createObjectNode().put("filename", "test.txt").put("content", Base64.getEncoder().encodeToString("Java skills".getBytes()));
        try {
            Object result = ResumeOperation.perform(service, "import-operation-1234", "import", "", body, current -> current.importFile(body));
            String id = (String) ((Map<?, ?>) result).get("id");
            ResumeOperation.perform(service, "delete-operation-1234", "delete", id, null, current -> current.delete(id));
            var again = ResumeOperation.perform(service, "delete-operation-1234", "delete", id, null, current -> { fail("Deletion must not execute twice."); return null; });
            assertTrue(Facts.JSON.valueToTree(again).path("deleted").asBoolean());
            assertThrows(IllegalArgumentException.class, () -> ResumeOperation.perform(service, "import-operation-1234", "import", "", body, current -> current.importFile(body)));
            assertTrue(service.query("SELECT * FROM resumes").isEmpty());
        } finally { service.shutdown(); }
    }
    @Test void savingManualCorrectionsChangesOnlyTheirProvenance() throws Exception {
        ResumeService service = new ResumeService(directory);
        try {
            var body = Facts.JSON.createObjectNode().put("filename", "test.txt").put("content", Base64.getEncoder().encodeToString("Java Docker".getBytes()));
            String id = (String) ((Map<?, ?>) service.importFile(body)).get("id");
            var extracted = new fr.isep.simrecrut.integration.pdf.LocalResumeCleaner().clean("test.txt", "Java Docker".getBytes());
            extracted.remove(List.of("schemaVersion", "cleanerVersion", "status"));
            service.patch(id, ResumeService.fields("status", "REVIEW_REQUIRED", "draft", extracted.toString()));
            var edited = extracted.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) edited.path("professional").path("skills").get(0)).put("skillCode", "Python");
            var save = Facts.JSON.createObjectNode().put("confirmed", false).put("expectedRevision", 1); save.set("draft", edited);
            service.save(id, save);
            var skills = service.detail(id).path("draft").path("professional").path("skills");
            assertEquals("USER_SUPPLIED", skills.get(0).path("evidence").path("source").asText());
            assertEquals("EXTRACTED", skills.get(1).path("evidence").path("source").asText());
        } finally { service.shutdown(); }
    }
}
