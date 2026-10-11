package fr.isep.simrecrut.integration.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/** Evaluate explicit professional labels using synthetic English and French fixtures. */
class ProfessionalExtractionTest {
    @Test void evaluatesFiveGroupsAgainstKnownLabels() throws Exception {
        int matched = 0, expected = 0;
        for (String text : new String[]{
            "Name: Private Person\nSkills\nJava Docker\nExperience\nSoftware Engineer 2020-01 - 2022-06\nEducation\nMaster Computer Science\nLanguages\nEnglish C1\nProjects\nWeb API Java Docker",
            "Nom: Private Person\nCompetences\nJava Docker\nExperience professionnelle\nDeveloppeur 2020-01 - 2022-06\nFormation\nMaster Informatique\nLangues\nAnglais C1\nProjets\nWeb API Java Docker"}) {
            var result = new LocalResumeCleaner().clean("fixture.txt", text.getBytes(StandardCharsets.UTF_8));
            var p = result.path("professional");
            String[] groups = {"skills", "experience", "education", "languages", "projects"};
            int[] counts = {2, 1, 1, 1, 1};
            for (int i = 0; i < groups.length; i++) { assertEquals(counts[i], p.path(groups[i]).size()); matched += counts[i]; expected += counts[i]; }
            assertEquals("SOFTWARE_ENGINEERING", p.path("experience").get(0).path("roleFamilyCode").asText());
            assertEquals("2020-01", p.path("experience").get(0).path("startMonth").asText());
            assertEquals("2022-06", p.path("experience").get(0).path("endMonth").asText());
            assertEquals("MASTER", p.path("education").get(0).path("degreeLevelCode").asText());
            assertEquals("COMPUTER_SCIENCE", p.path("education").get(0).path("subjectCode").asText());
            assertEquals("EN", p.path("languages").get(0).path("languageCode").asText());
            assertEquals("C1", p.path("languages").get(0).path("levelCode").asText());
            assertFalse(result.toString().contains("Private Person"));
            result.remove(java.util.List.of("schemaVersion", "cleanerVersion", "status"));
            fr.isep.simrecrut.workspace.Facts.validate(result);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/extraction-accuracy.json"), "{\"scope\":\"two synthetic bilingual fixtures; not real-world accuracy\",\"expectedFacts\":" + expected + ",\"matchedFacts\":" + matched + ",\"unexpectedFacts\":0}");
    }
    @Test void preservesUnknownsAndRejectsNegativeSkillStatements() throws Exception {
        var result = new LocalResumeCleaner().clean("fixture.txt", "Skills\nNo Java experience\nExperience\nSoftware Engineer 2020 - 2022\nEducation\nBachelor Unknown subject\nLanguages\nFrench fluent".getBytes(StandardCharsets.UTF_8));
        var p = result.path("professional");
        assertEquals(0, p.path("skills").size());
        assertTrue(p.path("experience").get(0).path("startMonth").isNull());
        assertEquals("UNKNOWN", p.path("education").get(0).path("subjectCode").asText());
        assertEquals("UNKNOWN", p.path("languages").get(0).path("levelCode").asText());
    }
    @Test void recognizesEnglishFrenchAndNumericMonthFormats() throws Exception {
        for (String date : new String[]{"January 2020 - June 2022", "janvier 2020 - juin 2022", "01/2020 - 06/2022"}) {
            var result = new LocalResumeCleaner().clean("fixture.txt", ("Experience\nSoftware Engineer " + date).getBytes(StandardCharsets.UTF_8));
            var fact = result.path("professional").path("experience").get(0);
            assertEquals("2020-01", fact.path("startMonth").asText());
            assertEquals("2022-06", fact.path("endMonth").asText());
        }
    }
    @Test void groupsProjectSkillLinesAndKeepsSeparateExperienceDates() throws Exception {
        var result = new LocalResumeCleaner().clean("fixture.txt", "Experience\nSoftware Engineer 2020-01 - 2022-06\nSoftware Engineer 2023-01 - Present\nProjects\nJava\nDocker\n\nReact".getBytes(StandardCharsets.UTF_8));
        var p = result.path("professional");
        assertFalse(p.path("experience").get(0).path("isCurrent").asBoolean());
        assertTrue(p.path("experience").get(1).path("isCurrent").asBoolean());
        assertEquals(2, p.path("projects").size());
        assertEquals(2, p.path("projects").get(0).path("supportedSkillCodes").size());
        assertTrue(p.path("projects").get(0).path("evidence").path("text").asText().contains("Java"));
    }
    @Test void readsActualScannedPdfWithLocalOcr() throws Exception {
        Assumptions.assumeTrue(System.getenv("TESSDATA_PATH") != null, "Local OCR models are required for this integration test.");
        var image = new java.awt.image.BufferedImage(1600, 2000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 1600, 2000);
        graphics.setColor(java.awt.Color.BLACK); graphics.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 48));
        String[] lines = {"Skills", "Java Docker", "Experience", "Software Engineer 2020-01 - 2022-06", "Education", "Master Computer Science", "Languages", "English C1", "Projects", "Web API Java Docker"};
        for (int i = 0; i < lines.length; i++) graphics.drawString(lines[i], 60, 120 + i * 100); graphics.dispose();
        try (var pdf = new PDDocument(); var bytes = new java.io.ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf, page)) { stream.drawImage(LosslessFactory.createFromImage(pdf, image), 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight()); }
            pdf.save(bytes); var result = new LocalResumeCleaner().clean("scan.pdf", bytes.toByteArray());
            for (String group : java.util.List.of("experience", "education", "languages", "projects")) assertEquals(1, result.path("professional").path(group).size(), group);
            assertEquals(2, result.path("professional").path("skills").size());
            assertTrue(result.path("warnings").toString().contains("Local OCR"));
            assertEquals(1, result.path("professional").path("education").get(0).path("evidence").path("page").asInt());
        }
    }
}
