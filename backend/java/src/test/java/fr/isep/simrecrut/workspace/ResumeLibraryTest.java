package fr.isep.simrecrut.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Library names, the personal and other groups, and deletion of the original as soon as facts are confirmed. */
class ResumeLibraryTest {
    @TempDir Path directory;

    private String create(ResumeService service, String filename, String displayName) throws Exception {
        var body = Facts.JSON.createObjectNode().put("filename", filename)
            .put("content", Base64.getEncoder().encodeToString(("Java technical skills " + filename).getBytes(StandardCharsets.UTF_8)));
        if (displayName != null) body.put("displayName", displayName);
        return (String) ((Map<?, ?>) service.importFile(body)).get("id");
    }

    @Test void displayNameDefaultsToTheFileNameAndRenamingKeepsTheFileName() throws Exception {
        ResumeService service = new ResumeService(directory);
        try {
            String named = create(service, "cv-it.txt", "  CV for   IT ");
            String unnamed = create(service, "Product Manager.pdf.txt", null);
            assertEquals("CV for IT", service.detail(named).path("display_name").asText());
            assertEquals("Product Manager.pdf.txt", service.detail(unnamed).path("display_name").asText());
            assertEquals("Marie Dupont", ResumeService.displayName("", "Marie Dupont.PDF"));
            assertEquals(120, ResumeService.displayName("x".repeat(300), "cv.pdf").length());
            int revision = service.detail(named).path("revision").asInt();
            service.rename(named, Facts.JSON.createObjectNode().put("displayName", "CV for product manager"));
            var record = service.detail(named);
            assertEquals("CV for product manager", record.path("display_name").asText());
            assertEquals("cv-it.txt", record.path("filename").asText());
            assertEquals(revision, record.path("revision").asInt());
            service.rename(named, Facts.JSON.createObjectNode().put("displayName", " "));
            assertEquals("cv-it.txt", service.detail(named).path("display_name").asText());
            assertThrows(IllegalArgumentException.class,
                () -> service.rename(named, Facts.JSON.createObjectNode().put("owner", "someone")));
            assertTrue(service.list().stream().allMatch(row -> row.has("display_name")));
        } finally { service.shutdown(); }
    }

    @Test void confirmationDeletesTheTemporaryOriginalImmediately() throws Exception {
        ResumeService service = new ResumeService(directory);
        try {
            String id = create(service, "fixture.txt", "Fixture");
            assertTrue(service.detail(id).path("originalAvailable").asBoolean());
            var draft = Facts.empty();
            draft.withObject("professional").withArray("skills").addObject().put("skillCode", "Java");
            var body = Facts.JSON.createObjectNode().put("confirmed", false);
            body.set("draft", draft);
            service.save(id, body);
            assertTrue(service.detail(id).path("originalAvailable").asBoolean());
            body.put("confirmed", true);
            assertEquals(true, ((Map<?, ?>) service.save(id, body)).get("originalRemoved"));
            assertFalse(service.detail(id).path("originalAvailable").asBoolean());
            assertEquals("", service.storedRow(id).path("original_text").asText());
            try (var files = Files.list(directory.resolve("originals"))) { assertEquals(0, files.count()); }
        } finally { service.shutdown(); }
    }

    @Test void personalGroupMayHoldContactDetailsButProfessionalFactsMayNot() throws Exception {
        var draft = Facts.empty();
        var professional = draft.withObject("professional");
        professional.withArray("personal").addObject().put("fieldCode", "EMAIL").put("value", "person@example.test");
        professional.withArray("personal").addObject().put("fieldCode", "LINKEDIN").put("value", "https://linkedin.com/in/person");
        professional.withArray("personal").addObject().put("fieldCode", "PHOTO").put("value", "YES").put("imageBase64", "QUJD");
        professional.withArray("other").addObject().put("categoryCode", "INTEREST").put("value", "Chess");
        var valid = Facts.validate(draft);
        assertEquals("EXTRACTED", valid.at("/professional/personal/0/evidence/source").asText());
        assertFalse(Facts.hasFacts(valid));
        var wrongField = draft.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongField.at("/professional/personal/0")).put("fieldCode", "SALARY");
        assertThrows(IllegalArgumentException.class, () -> Facts.validate(wrongField));
        var imageOnEmail = draft.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) imageOnEmail.at("/professional/personal/0")).put("imageBase64", "QUJD");
        assertThrows(IllegalArgumentException.class, () -> Facts.validate(imageOnEmail));
        var leakedEmail = draft.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) leakedEmail.at("/professional/skills")).addObject()
            .put("skillCode", "person@example.test");
        assertThrows(IllegalArgumentException.class, () -> Facts.validate(leakedEmail));
        assertFalse(ResumeService.publishedProfessional(valid).has("personal"));
        assertTrue(ResumeService.publishedProfessional(valid).has("other"));
    }
}
