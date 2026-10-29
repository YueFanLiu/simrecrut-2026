package fr.isep.simrecrut.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verify fixed deadlines, immutable confirmation and durable deletion retries. */
class ResumeRetentionTest {
    @TempDir Path directory;
    private String create(ResumeService service) throws Exception {
        var body = Facts.JSON.createObjectNode().put("filename", "fixture.txt")
            .put("content", Base64.getEncoder().encodeToString("Java technical skills".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return (String) ((Map<?, ?>) service.importFile(body)).get("id");
    }
    @Test void unconfirmedExpiryCannotBeExtendedAndLeavesOnlyReceipt() throws Exception {
        ResumeService service = new ResumeService(directory);
        try {
            service.clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
            String id = create(service);
            service.clock = Clock.fixed(Instant.parse("2026-10-01T23:00:00Z"), ZoneOffset.UTC);
            var body = Facts.JSON.createObjectNode().set("draft", Facts.empty()); service.save(id, body);
            assertEquals("2026-10-02T00:00:00Z", service.detail(id).path("expires_at").asText());
            service.clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
            assertThrows(IllegalArgumentException.class, () -> service.detail(id)); assertTrue(service.list().isEmpty());
            service.sweep(); assertTrue(service.query("SELECT * FROM resumes").isEmpty());
            assertEquals(1, service.query("SELECT * FROM deletion_receipts").size());
            try (var files = Files.list(directory.resolve("originals"))) { assertEquals(0, files.count()); }
        } finally { service.shutdown(); }
    }
    @Test void confirmationLocksFactsAndCloudFailureRetriesWithoutRestoringAccess() throws Exception {
        ResumeService service = new ResumeService(directory) {
            @Override java.sql.Connection cloud() throws Exception { throw new java.io.IOException("Unavailable"); }
        };
        try {
            service.clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
            String id = create(service); var draft = Facts.empty();
            draft.withObject("professional").withArray("skills").addObject().put("skillCode", "Java");
            var body = Facts.JSON.createObjectNode().put("confirmed", true); body.set("draft", draft);
            assertThrows(IllegalArgumentException.class, () -> service.save(id, body));
            body.put("confirmed", false); service.save(id, body); body.put("confirmed", true);
            service.save(id, body);
            assertEquals("2026-10-31T00:00:00Z", service.detail(id).path("expires_at").asText());
            body.put("confirmed", false); assertThrows(IllegalArgumentException.class, () -> service.save(id, body));
            assertThrows(IllegalArgumentException.class, () -> service.clean(id, Facts.JSON.createObjectNode()));
            service.patch(id, ResumeService.fields("cloud_revision", 2));
            assertEquals(false, ((Map<?, ?>) service.delete(id)).get("deleted"));
            assertThrows(IllegalArgumentException.class, () -> service.detail(id));
            service.sweep(); assertEquals("PURGING", service.storedRow(id).path("status").asText());
            service.patch(id, ResumeService.fields("cloud_revision", null)); service.sweep();
            assertTrue(service.query("SELECT * FROM resumes").isEmpty());
        } finally { service.shutdown(); }
    }
    @Test void preservesLegacyResearchRecordsAndStopsDeletedWorkers() throws Exception {
        ResumeService service = new ResumeService(directory);
        try {
            String id = create(service);
            service.patch(id, ResumeService.fields("temporary_original", 0, "expires_at", null));
            service.clock = Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC);
            service.sweep(); assertTrue(service.detail(id).path("originalAvailable").asBoolean());
            service.clean(id, Facts.JSON.createObjectNode()); service.delete(id);
            for (int i = 0; i < 10; i++) { Thread.sleep(20); assertTrue(service.query("SELECT * FROM resumes").isEmpty()); }
        } finally { service.shutdown(); }
    }
}
