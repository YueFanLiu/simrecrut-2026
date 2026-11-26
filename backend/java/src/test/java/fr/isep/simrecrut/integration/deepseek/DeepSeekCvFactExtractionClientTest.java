package fr.isep.simrecrut.integration.deepseek;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.integration.pdf.LocalResumeCleaner;
import fr.isep.simrecrut.service.assessment.CaseException;
import fr.isep.simrecrut.workspace.Facts;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeepSeekCvFactExtractionClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<LocalResumeCleaner.PdfPage> PAGES = List.of(
            new LocalResumeCleaner.PdfPage(1, """
                    Avery Quill
                    55 Chestnut Avenue
                    Phone: 06 12 34 56 78
                    Email: avery@example.test
                    Skills: Java and SQL
                    Work Experience
                    Software Engineer at Elmstone 2020-01 - 2023-01
                    Education
                    Bachelor in Computer Science, Summit College
                    Languages: English B1, French C1
                    Projects: Built a REST API with Java
                    """, false),
            new LocalResumeCleaner.PdfPage(2, """
                    Languages
                    English B2
                    Contact: Avery Quill
                    """, true));
    private static final JsonNode CATALOGS = parse("""
            {"skillCodes":["JAVA","SQL","RUST"],"roleFamilyCodes":["SOFTWARE_ENGINEERING"],
             "degreeLevelCodes":["BACHELOR"],"subjectCodes":["COMPUTER_SCIENCE","UNKNOWN"],
             "languageCodes":["EN","FR"],"languageLevelCodes":["B1","B2","C1","C2"],
             "conditionCodes":["REST_API_PROJECT"]}
            """);

    @Test
    void sendsOnlySelectedProfessionalTextAndDoesNotResolveConflictingLanguageLevels() {
        var requests = new ArrayList<String>();
        ObjectNode draft = validDraft();
        evidence(draft.withObject("/professional").withArray("languages").addObject()
                .put("languageCode", "EN").put("levelCode", "B2"), 2, "English B2");
        var client = client(body -> {
            requests.add(body);
            return response(draft);
        });

        var result = client.extract(PAGES, CATALOGS);

        assertEquals("deepseek-cv-v1", result.path("extractionVersion").asText());
        assertEquals(1, result.at("/professional/languages").size());
        assertEquals("FR", result.at("/professional/languages/0/languageCode").asText());
        assertEquals("French C1", result.at("/professional/languages/0/evidence/text").asText());
        assertEquals(2, result.path("warnings").size());
        assertEquals(1, requests.size());
        JsonNode request = parse(requests.get(0));
        JsonNode payload = parse(request.at("/messages/1/content").asText());
        String text = payload.at("/pages/0/text").asText();
        assertTrue(text.contains("Java and SQL"));
        assertTrue(text.contains("2020-01 - 2023-01"));
        for (String detail : List.of("Avery", "Quill", "Chestnut", "12 34", "example.test", "Elmstone", "Summit")) {
            assertFalse(requests.get(0).contains(detail));
        }
        assertEquals("json_object", request.at("/response_format/type").asText());
    }

    @Test
    void usesOneTransportRetryAndOneRepairWithoutEchoingInvalidOutput() {
        var requests = new ArrayList<String>();
        var client = client(body -> {
            requests.add(body);
            return switch (requests.size()) {
                case 1 -> new DeepSeekCvFactExtractionClient.Response(429, new byte[0]);
                case 2 -> responseText("{\"providerDiagnostic\":\"DO_NOT_ECHO\"}");
                default -> response(validDraft());
            };
        });

        assertEquals(1, client.extract(PAGES, CATALOGS).at("/professional/experience").size());
        assertEquals(3, requests.size());
        assertEquals(requests.get(0), requests.get(1));
        assertTrue(requests.get(2).contains("failed server validation"));
        assertFalse(requests.get(2).contains("DO_NOT_ECHO"));
    }

    @Test
    void rejectsQuotesThatDoNotSupportTheFieldAndRepeatedTrailingJson() {
        for (String badDraft : List.of(
                validDraft().toString().replaceFirst("\"skillCode\":\"JAVA\"", "\"skillCode\":\"RUST\""),
                validDraft().toString().replace("\"text\":\"Java\"", "\"text\":\"Java and Docker\""),
                validDraft().toString().replace("\"levelCode\":\"C1\"", "\"levelCode\":\"C2\""),
                validDraft().toString().replace("\"endMonth\":\"2023-01\"", "\"endMonth\":\"2024-01\""),
                validDraft().toString().replace("Built a REST API with Java", "REST API with Java"),
                validDraft() + "{}")) {
            var calls = new AtomicInteger();
            var client = client(body -> {
                calls.incrementAndGet();
                return responseText(badDraft);
            });
            var error = assertThrows(CaseException.class, () -> client.extract(PAGES, CATALOGS));
            assertEquals(DeepSeekCvFactExtractionClient.INVALID_DRAFT, error.code());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void stopsAfterTwoUnavailableResponsesAndSkipsTheApiForAnEmptyProjection() {
        var calls = new AtomicInteger();
        var client = client(body -> {
            calls.incrementAndGet();
            return new DeepSeekCvFactExtractionClient.Response(503, new byte[0]);
        });
        assertEquals(DeepSeekCvFactExtractionClient.UNAVAILABLE,
                assertThrows(CaseException.class, () -> client.extract(PAGES, CATALOGS)).code());
        assertEquals(DeepSeekCvFactExtractionClient.NO_SOURCE, assertThrows(CaseException.class,
                () -> client.extract(List.of(new LocalResumeCleaner.PdfPage(1, "Avery Quill\nEmail: avery@example.test", false)),
                        CATALOGS)).code());
        assertEquals(2, calls.get());
    }

    @Test
    void providerCannotReturnPersonalOrOtherInformation() {
        ObjectNode draft = validDraft();
        draft.withObject("/professional").withArray("personal").addObject().put("fieldCode", "FULL_NAME").put("value", "Avery Quill");
        var calls = new AtomicInteger();
        var client = client(body -> { calls.incrementAndGet(); return response(draft); });
        CaseException error = assertThrows(CaseException.class, () -> client.extract(PAGES, CATALOGS));
        assertEquals(DeepSeekCvFactExtractionClient.INVALID_DRAFT, error.code());
        assertEquals(2, calls.get());
    }

    private static DeepSeekCvFactExtractionClient client(DeepSeekCvFactExtractionClient.Exchange exchange) {
        return new DeepSeekCvFactExtractionClient(JSON, new LocalResumeCleaner(), "deepseek", "deepseek-flash", exchange);
    }

    private static ObjectNode validDraft() {
        var root = Facts.empty();
        root.remove("warnings");
        var professional = root.withObject("/professional");
        evidence(professional.withArray("skills").addObject().put("skillCode", "JAVA"), 1, "Java");
        evidence(professional.withArray("languages").addObject()
                .put("languageCode", "FR").put("levelCode", "C1"), 1, "French C1");
        String page = new CvSourceEvidence(PAGES, CATALOGS).pages().get(1);
        String experience = page.lines().filter(line -> line.startsWith("Software Engineer")).findFirst().orElseThrow();
        evidence(professional.withArray("experience").addObject().put("roleFamilyCode", "SOFTWARE_ENGINEERING")
                .put("startMonth", "2020-01").put("endMonth", "2023-01").put("isCurrent", false), 1, experience);
        var project = professional.withArray("projects").addObject().put("projectId", "PROJECT_1");
        project.putArray("supportedConditionCodes").add("REST_API_PROJECT");
        project.putArray("supportedSkillCodes").add("JAVA");
        evidence(project, 1, "Built a REST API with Java");
        return root;
    }

    private static void evidence(ObjectNode fact, int page, String text) {
        fact.putObject("evidence").put("page", page).put("text", text).put("source", "EXTRACTED");
    }

    private static DeepSeekCvFactExtractionClient.Response response(JsonNode draft) { return responseText(draft.toString()); }

    private static DeepSeekCvFactExtractionClient.Response responseText(String text) {
        var response = JSON.createObjectNode();
        response.putArray("choices").addObject().put("finish_reason", "stop").putObject("message").put("content", text);
        return new DeepSeekCvFactExtractionClient.Response(200, response.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode parse(String source) {
        try { return JSON.readTree(source); }
        catch (Exception error) { throw new IllegalArgumentException(error); }
    }
}
