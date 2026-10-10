package fr.isep.simrecrut.integration.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

class LocalResumeCleanerTest {
    @Test
    void deduplicatesAndDoesNotExposeContactDetails() throws Exception {
        var cleaner = new LocalResumeCleaner();
        byte[] input = "Name: Test Person\nJava JavaScript Java\ntest@example.com".getBytes(StandardCharsets.UTF_8);
        var result = cleaner.clean("resume.txt", input);
        assertEquals(2, result.path("professional").path("skills").size());
        assertFalse(result.toString().contains("example.com"));
        assertFalse(result.toString().contains("Test Person"));
        assertEquals("REVIEW_REQUIRED", result.path("status").asText());
    }

    @Test
    void rejectsUnsupportedFilesAndScannedPdf() throws Exception {
        var cleaner = new LocalResumeCleaner();
        assertThrows(IllegalArgumentException.class, () -> cleaner.clean("resume.exe", new byte[]{1}));
        try (var pdf = new PDDocument(); var bytes = new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(bytes);
            assertThrows(IllegalArgumentException.class, () -> cleaner.clean("scan.pdf", bytes.toByteArray()));
        }
    }
    @Test
    void handlesPdfAndDocxUsingTheSameRules() throws Exception {
        var cleaner = new LocalResumeCleaner();
        try (var pdf = new PDDocument(); var bytes = new java.io.ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(30, 700);
                stream.showText("Technical skills: Java and Docker");
                stream.endText();
            }
            pdf.save(bytes);
            var result = cleaner.clean("resume.pdf", bytes.toByteArray());
            assertEquals(2, result.path("professional").path("skills").size());
            assertEquals(1, result.path("professional").path("skills").get(0).path("evidence")
                .path("page").asInt());
        }
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var bytes = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Java and Docker");
            doc.write(bytes);
            var result = cleaner.clean("resume.docx", bytes.toByteArray());
            assertEquals(2, result.path("professional").path("skills").size());
        }
    }
}
