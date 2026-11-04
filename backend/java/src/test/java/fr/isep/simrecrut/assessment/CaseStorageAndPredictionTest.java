package fr.isep.simrecrut.assessment;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.integration.ml.HttpCasePredictionGateway;
import fr.isep.simrecrut.integration.pdf.CaseFileStore;
import fr.isep.simrecrut.service.assessment.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CaseStorageAndPredictionTest {
    @TempDir Path temporary;
    final ObjectMapper json = new ObjectMapper();
    final Protocol protocol = new Protocol(1, 1, 1, "P", new BigDecimal("0.7"), "S1", "schema.json", "hash", 32);
    final Model neutral = new Model(1, 1, 1, "N1", "NEUTRAL", 0.5, 0.5, "ACTIVE");
    final Model biased = new Model(2, 1, 1, "B1", "BIASED", 0.5, 0.5, "ACTIVE");

    @Test void pdfPageEncryptionSizeAndPathBoundariesAreEnforced() throws Exception {
        CaseFileStore files = new CaseFileStore(temporary.toString());
        assertThrows(IllegalArgumentException.class, () -> files.validate("resume.pdf", pdf(21, false)));
        assertThrows(IllegalArgumentException.class, () -> files.validate("resume.pdf", pdf(1, true)));
        assertThrows(IllegalArgumentException.class, () -> files.validate("resume.pdf", new byte[10 * 1024 * 1024 + 1]));
        files.validate("resume.pdf", pdf(20, false));
        String id = "01AAAAAAAAAAAAAAAAAAAAAAAA";
        String path = files.stage(id, pdf(1, false));
        assertTrue(files.read(path).length > 0);
        assertThrows(java.io.IOException.class, () -> files.read("../outside.pdf"));
        files.removeCase(id); assertFalse(Files.exists(temporary.resolve(id)));
    }

    @Test void frozenFilesRequireMatchingChecksumsAndContainedPaths() throws Exception {
        Path rules = Files.createDirectory(temporary.resolve("rules")), artifacts = Files.createDirectory(temporary.resolve("artifacts"));
        byte[] rule = """
            {"catalogs":{"skillCodes":["X"],"roleFamilyCodes":["X"],"degreeLevelCodes":["X"],
            "subjectCodes":["X"],"languageCodes":["X"],"languageLevelCodes":["X"],"conditionCodes":["X"]}}
            """.getBytes(StandardCharsets.UTF_8);
        byte[] schema = """
            {"researchAttributes":{"SCHOOL_CATEGORY":["UNKNOWN"],"REFERRAL":["UNKNOWN"],
            "GENDER":["UNKNOWN"],"ETHNICITY":["UNKNOWN"]}}
            """.getBytes(StandardCharsets.UTF_8);
        Files.write(rules.resolve("rule.json"), rule); Files.write(artifacts.resolve("schema.json"), schema);
        JobVersion job = new JobVersion(); job.ruleStatus = "FROZEN"; job.matchingRuleVersionId = 1L;
        job.configPath = "rule.json"; job.configSha256 = CandidateCaseService.hash(rule);
        Protocol frozen = new Protocol(1, 1, 1, "P", new BigDecimal("0.7"), "S1", "schema.json", CandidateCaseService.hash(schema), 32);
        CaseConfiguration configuration = new CaseConfiguration(json, rules.toString(), artifacts.toString());
        assertNotNull(configuration.load(job, frozen));
        job.configSha256 = "0".repeat(64);
        assertThrows(CaseException.class, () -> configuration.load(job, frozen));
        job.configPath = "../outside.json";
        assertThrows(CaseException.class, () -> configuration.load(job, frozen));
    }

    @Test void neutralOmitsResearchAndBiasedUsesOnlyExplicitStructuredValues() throws Exception {
        AtomicReference<JsonNode> observed = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(observed, authorization, new AtomicBoolean(false));
        try {
            var gateway = gateway(server);
            JsonNode professional = json.readTree("{\"m\":[1,0,0,0,0],\"w\":[1,0,0,0,0],\"mw\":[1,0,0,0,0],\"g\":1}");
            JsonNode research = json.readTree("{\"schoolPrestige\":\"UNKNOWN\",\"referral\":\"UNKNOWN\",\"gender\":\"UNKNOWN\",\"ethnicity\":\"UNKNOWN\"}");
            assertEquals(0.8, gateway.predict("CASE", neutral, protocol, professional, research).path("acceptanceScore").asDouble());
            assertFalse(observed.get().has("research")); assertEquals("Bearer private-internal-token", authorization.get());
            gateway.predict("CASE", biased, protocol, professional, research);
            assertEquals(research, observed.get().path("research"));
            assertFalse(observed.get().toString().contains("original"));
        } finally { server.stop(0); }
    }

    @Test void wrongVersionOrInconsistentDecisionIsTechnicalFailure() throws Exception {
        AtomicReference<JsonNode> observed = new AtomicReference<>(); AtomicBoolean corrupt = new AtomicBoolean(true);
        HttpServer server = server(observed, new AtomicReference<>(), corrupt);
        try {
            assertThrows(CaseException.class, () -> gateway(server).predict("CASE", neutral, protocol,
                    json.createObjectNode(), json.createObjectNode()));
        } finally { server.stop(0); }
        assertFalse(new HttpCasePredictionGateway(json, "", "", "", "").available());
    }

    private HttpServer server(AtomicReference<JsonNode> observed, AtomicReference<String> auth, AtomicBoolean corrupt) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            JsonNode body = json.readTree(exchange.getRequestBody()); observed.set(body);
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ObjectNode result = json.createObjectNode().put("requestId", body.path("requestId").asText())
                    .put("caseId", body.path("caseId").asText()).put("modelVersion", corrupt.get() ? "WRONG" : body.path("modelVersion").asText())
                    .put("featureSchemaVersion", "S1").put("status", "SUCCESS").put("logit", 1.386294)
                    .put("acceptanceScore", 0.8).put("modelThreshold", 0.5).put("commonThreshold", 0.5)
                    .put("decision", "Accept").put("commonThresholdDecision", "Accept");
            byte[] bytes = json.writeValueAsBytes(result); exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start(); return server;
    }
    private HttpCasePredictionGateway gateway(HttpServer server) {
        return new HttpCasePredictionGateway(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                "private-internal-token", "/predict", "/predict");
    }
    private byte[] pdf(int pages, boolean encrypted) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            if (encrypted) document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            document.save(output); return output.toByteArray();
        }
    }
}
