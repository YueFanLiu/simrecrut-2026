package fr.isep.simrecrut.integration.pdf;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.workspace.Facts;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

class CvProfileExtractorTest {
    private static final String CV = String.join("\n",
            "Marie DUPONT",
            "Software engineer",
            "marie.dupont@example.test | +33 6 12 34 56 78 | linkedin.com/in/marie-dupont",
            "github.com/mdupont",
            "12 rue de Vaugirard, 75006 Paris, France",
            "Nationality: French",
            "Experience",
            "Backend developer 2019 - 2021",
            "Certifications",
            "AWS Certified Cloud Practitioner",
            "Interests",
            "• Chess",
            "• Trail running",
            "Volunteering: Red Cross first-aid volunteer",
            "Skills",
            "Java, Spring");

    private static Map<String, String> personal(JsonNode result) {
        Map<String, String> values = new HashMap<>();
        result.path("personal").forEach(item -> values.put(item.path("fieldCode").asText(), item.path("value").asText()));
        return values;
    }

    @Test void extractsTheHeaderContactDetailsAndCatchAllSections() {
        JsonNode result = new CvProfileExtractor().extract(CV);
        Map<String, String> values = personal(result);
        assertEquals("Marie DUPONT", values.get("FULL_NAME"));
        assertEquals("Marie", values.get("FIRST_NAME"));
        assertEquals("DUPONT", values.get("LAST_NAME"));
        assertEquals("marie.dupont@example.test", values.get("EMAIL"));
        assertEquals("+33 6 12 34 56 78", values.get("PHONE"));
        assertEquals("linkedin.com/in/marie-dupont", values.get("LINKEDIN"));
        assertEquals("github.com/mdupont", values.get("GITHUB"));
        assertTrue(values.get("ADDRESS").contains("75006 Paris"));
        assertEquals("France", values.get("COUNTRY"));
        assertEquals("French", values.get("NATIONALITY"));
        List<String> other = new java.util.ArrayList<>();
        result.path("other").forEach(item -> other.add(item.path("categoryCode").asText() + ":" + item.path("value").asText()));
        assertEquals(List.of("CERTIFICATION:AWS Certified Cloud Practitioner", "INTEREST:Chess", "INTEREST:Trail running",
                "VOLUNTEERING:Red Cross first-aid volunteer"), other);
        assertFalse(other.stream().anyMatch(value -> value.contains("Java") || value.contains("Backend")));
    }

    @Test void localCleanerAddsTheGroupsOnlyForCandidateReview() throws Exception {
        var cleaner = new LocalResumeCleaner();
        byte[] text = CV.getBytes(StandardCharsets.UTF_8);
        assertFalse(cleaner.clean("resume.txt", text).path("professional").has("personal"));
        var draft = cleaner.cleanWithProfile("resume.txt", text);
        draft.remove(List.of("schemaVersion", "cleanerVersion", "status"));
        var valid = Facts.validate(draft);
        assertEquals("Marie DUPONT", personal(valid.path("professional")).get("FULL_NAME"));
        assertFalse(valid.path("professional").path("skills").isEmpty());
    }

    @Test void pdfPhotoIsExtractedAndAbsenceIsReported() throws Exception {
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            BufferedImage portrait = new BufferedImage(120, 150, BufferedImage.TYPE_INT_RGB);
            var graphics = portrait.createGraphics();
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, 120, 150);
            graphics.dispose();
            var image = LosslessFactory.createFromImage(pdf, portrait);
            try (var stream = new PDPageContentStream(pdf, page)) {
                stream.drawImage(image, 450, 650, 90, 112);
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(30, 700);
                stream.showText("Jean Martin");
                stream.newLineAtOffset(0, -20);
                stream.showText("Technical skills: Java and Docker");
                stream.endText();
            }
            pdf.save(bytes);
            var draft = new LocalResumeCleaner().cleanWithProfile("resume.pdf", bytes.toByteArray());
            JsonNode photo = null;
            for (JsonNode item : draft.path("professional").path("personal")) {
                if (item.path("fieldCode").asText().equals("PHOTO")) photo = item;
            }
            assertNotNull(photo);
            assertEquals("YES", photo.path("value").asText());
            assertFalse(photo.path("imageBase64").asText().isBlank());
            assertEquals("Jean Martin", personal(draft.path("professional")).get("FULL_NAME"));
        }
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(bytes);
            assertNull(CvProfileExtractor.photo(bytes.toByteArray()));
        }
    }
}
