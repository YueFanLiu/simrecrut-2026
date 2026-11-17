package fr.isep.simrecrut.assessment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.ModelInputRequest;
import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.integration.ml.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.service.assessment.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic contract checks: no model files, corpus data or remote services. */
class Model39IntegrationTest {
    @TempDir Path directory;
    final ObjectMapper json = new ObjectMapper();
    JsonNode schema() throws Exception {
        Path contract = Path.of("../../contracts/feature-schemas/professional-research-v2.json");
        if (Files.exists(contract)) return json.readTree(contract.toFile());
        // The standalone Java Docker context excludes the repository-level contracts.
        ObjectNode schema = json.createObjectNode().put("schema_version", "professional-research-v2").put("input_width", 39);
        var order = schema.putArray("input_order");
        for (String prefix : java.util.List.of("match", "weight", "contribution")) {
            for (String area : java.util.List.of("skills", "experience", "education", "languages", "projects")) {
                order.add(prefix + "_" + area);
            }
        }
        order.add("mandatory_pass"); var families = schema.putArray("research_families");
        var research = schema.putObject("research_order");
        String[] names = {"school", "referral", "gender", "ethnicity", "name_cue"};
        String[][] values = {{"prestigious", "ordinary", "unknown"}, {"yes", "no", "unknown"},
                {"woman", "man", "unknown"}, {"east_asian", "south_asian", "black", "white",
                "middle_eastern_or_north_african", "mixed_or_other", "unknown"}, {"east_asian", "south_asian",
                "black", "white", "middle_eastern_or_north_african", "mixed_or_other", "unknown"}};
        for (int i = 0; i < names.length; i++) {
            families.add(names[i]); var entries = research.putArray(names[i]);
            for (String value : values[i]) { entries.add(value); order.add(names[i] + "_" + value); }
        }
        return schema;
    }
    JsonNode professional() throws Exception {
        return json.readTree("""
            {"m":[1,0.5,0,0,0],"w":[0.5,0.5,0,0,0],"mw":[0.5,0.25,0,0,0],"g":1}
            """);
    }
    @Test void canonicalOrderIncludesNameCueAndNeutralMasksAllResearch() throws Exception {
        JsonNode research = json.readTree("{\"referral\":\"YES\",\"nameCue\":\"SOUTH_ASIAN\"}");
        var x = ModelFeatureEncoder.encode(json, professional(), research, schema(), false);
        assertEquals(39, x.size()); assertEquals(1, x.get(19).asInt()); assertEquals(1, x.get(33).asInt());
        assertEquals(0.25, x.get(11).asDouble());
        var neutral = ModelFeatureEncoder.encode(json, professional(), research, schema(), true);
        for (int index = 16; index < 39; index++) assertEquals(0, neutral.get(index).asInt());
    }
    @Test void invalidContributionsCategoriesAndMandatoryFlagsAreRejected() throws Exception {
        var p = (ObjectNode) professional(); p.withArray("mw").set(0, json.getNodeFactory().numberNode(0.6));
        assertThrows(IllegalArgumentException.class, () -> ModelFeatureEncoder.encode(json, p, json.createObjectNode(), schema(), false));
        assertThrows(IllegalArgumentException.class, () -> ModelFeatureEncoder.encode(json, professional(),
                json.createObjectNode().put("gender", "INFERRED"), schema(), false));
        var flag = (ObjectNode) professional(); flag.put("g", 2);
        assertThrows(IllegalArgumentException.class, () -> ModelFeatureEncoder.encode(json, flag, json.createObjectNode(), schema(), false));
    }
    @Test void schemaAdapterDoesNotChangeCanonicalBytes() throws Exception {
        byte[] bytes = json.writeValueAsBytes(schema()); Files.write(directory.resolve("schema.json"), bytes);
        var rules = json.createObjectNode(); var catalogs = rules.putObject("catalogs");
        for (String group : java.util.List.of("skillCodes", "roleFamilyCodes", "degreeLevelCodes", "subjectCodes",
                "languageCodes", "languageLevelCodes", "conditionCodes")) catalogs.putArray(group).add("X");
        byte[] rule = json.writeValueAsBytes(rules);
        Files.write(directory.resolve("rule.json"), rule);
        var job = new fr.isep.simrecrut.entity.job.JobVersion(); job.ruleStatus = "FROZEN";
        job.matchingRuleVersionId = 1L; job.configPath = "rule.json"; job.configSha256 = CandidateCaseService.hash(rule);
        var protocol = new Protocol(1, 1, 1, "P", new BigDecimal(".7"), "professional-research-v2",
                "schema.json", CandidateCaseService.hash(bytes), 39);
        var bundle = new CaseConfiguration(json, directory.toString(), directory.toString()).load(job, protocol);
        assertEquals(5, CaseConfiguration.types(bundle.schema()).size());
        assertEquals("UNKNOWN", bundle.schema().at("/researchAttributes/NAME_CUE/6").asText());
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("schema.json")));
    }
    @Test void gatewaySends39ValuesAndChecksDigestAndHardGateAtZeroCutoff() throws Exception {
        byte[] bytes = json.writeValueAsBytes(schema()); Files.write(directory.resolve("schema.json"), bytes);
        var protocol = new Protocol(1, 1, 1, "P", new BigDecimal(".7"), "professional-research-v2",
                "schema.json", CandidateCaseService.hash(bytes), 39);
        var observed = new AtomicReference<JsonNode>(); var wrongDigest = new java.util.concurrent.atomic.AtomicBoolean();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            JsonNode input = json.readTree(exchange.getRequestBody()); observed.set(input);
            ObjectNode response = json.createObjectNode();
            for (String key : java.util.List.of("requestId", "caseId", "modelVersion", "featureSchemaVersion", "modelSha256")) {
                response.set(key, input.path(key));
            }
            if (wrongDigest.get()) response.put("modelSha256", "b".repeat(64));
            boolean mandatory = input.path("features").path(15).asInt() == 1;
            response.put("status", "SUCCESS").put("logit", 0).put("acceptanceScore", mandatory ? .8 : 0)
                    .put("modelThreshold", 0).put("commonThreshold", 0)
                    .put("decision", mandatory ? "Accept" : "Reject")
                    .put("commonThresholdDecision", mandatory ? "Accept" : "Reject");
            byte[] data = json.writeValueAsBytes(response); exchange.sendResponseHeaders(200, data.length);
            try (var output = exchange.getResponseBody()) { output.write(data); }
        });
        server.start();
        try {
            var gateway = new HttpCasePredictionGateway(json, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-only-internal-token", "/predict", "/predict");
            org.springframework.test.util.ReflectionTestUtils.setField(gateway, "artifactRoot", directory.toString());
            var model = new Model(1, 1, 1, "B1", "BIASED", 0, 0, "ACTIVE", "a".repeat(64));
            var p = (ObjectNode) professional(); p.put("g", 0);
            var media = json.createObjectNode().put("biography", "Java development.");
            assertEquals("Reject", gateway.predict("CASE", model, protocol, p, json.createObjectNode(), media)
                    .path("decision").asText());
            assertEquals(39, observed.get().path("features").size());
            assertFalse(observed.get().has("professional")); assertFalse(observed.get().has("research"));
            assertEquals("Java development.", observed.get().path("biography").asText());
            var neutral = new Model(2, 1, 1, "N1", "NEUTRAL", 0, 0, "ACTIVE", "a".repeat(64));
            gateway.predict("CASE", neutral, protocol, p, json.createObjectNode(), media);
            assertFalse(observed.get().has("biography"));
            wrongDigest.set(true);
            assertThrows(CaseException.class, () -> gateway.predict("CASE", model, protocol, p, json.createObjectNode(), media));
        } finally { server.stop(0); }
    }
    @Test void optionalInputsRequireReviewPreserveConfirmedDigestAndPurgeAllVersions() throws Exception {
        var db = mock(CandidateCaseMapper.class); var pointer = new AtomicReference<ModelInput>();
        when(db.modelInput(1)).thenAnswer(i -> pointer.get());
        when(db.saveModelInput(eq(1L), anyString(), anyBoolean(), anyBoolean(), anyString())).thenAnswer(i -> {
            pointer.set(new ModelInput(i.getArgument(1), false, false, "REVIEWED".equals(i.getArgument(4)))); return 1;
        });
        when(db.confirmModelInput(1)).thenAnswer(i -> {
            pointer.set(new ModelInput(pointer.get().payloadSha256(), true, false, true)); return 1;
        });
        var inputs = new CaseModelInputs(json, db, directory.toString(), "", "");
        var row = new AssessmentCase(); row.id = 1L; row.publicId = "01AAAAAAAAAAAAAAAAAAAAAAAA";
        ObjectNode prepared = json.createObjectNode().put("biography", "Java backend development.");
        prepared.putArray("candidates"); inputs.prepared(row, prepared);
        assertThrows(IllegalArgumentException.class, () -> inputs.confirm(row));
        inputs.select(row, new ModelInputRequest(1L, true, false, "Example Name", "Java development.", -1, null));
        inputs.confirm(row); inputs.prune(row);
        assertEquals("Name: Example Name. Java development.", inputs.forPrediction(row).path("biography").asText());
        assertFalse(inputs.forPrediction(row).has("portrait"));
        try (var files = Files.list(directory.resolve(row.publicId))) { assertEquals(1, files.count()); }
        inputs.purge(row.publicId); assertFalse(Files.exists(directory.resolve(row.publicId)));
        assertThrows(CaseException.class, () -> inputs.forPrediction(row));
    }
    @Test void newRuleRoundsTheFinalRatioLikeOfflineWhileLegacyRemainsUnchanged() throws Exception {
        CaseTestSupport support = new CaseTestSupport(directory);
        try {
            support.job.activeCriteriaJson = "[\"EXPERIENCE\"]";
            support.job.weightSkills = BigDecimal.ZERO; support.job.weightExperience = BigDecimal.ONE;
            ObjectNode draft = fr.isep.simrecrut.workspace.Facts.empty(); draft.putArray("confirmedAbsent");
            draft.withObject("professional").withArray("experience").addObject()
                    .put("roleFamilyCode", "SOFTWARE_ENGINEERING").put("startMonth", "2020-01").put("endMonth", "2021-06");
            JsonNode requirements = json.readTree("""
                [{"criterionType":"EXPERIENCE","assessmentIncluded":true,"mandatory":true,
                "payload":{"targetYears":2,"minimumYears":1,"relevantRoleFamilyCodes":["SOFTWARE_ENGINEERING"]}}]
                """);
            ObjectNode rule = support.bundle.rule().deepCopy(); rule.put("version", "objective-curated-v8-online-v1");
            var bundle = new CaseConfiguration.Bundle(rule, support.bundle.schema(), support.protocol);
            var value = support.engine.calculate(draft, support.job, requirements, bundle, "2026-11");
            assertEquals(0.7083333, value.at("/professional/m/1").asDouble());
            assertEquals(1, value.at("/professional/g").asInt());
        } finally { support.worker.stop(); }
    }

    @Test void inputTextIsBuiltOnlyFromProfessionalEvidence() throws Exception {
        JsonNode facts = json.readTree("""
            {"skills":[{"skillCode":"JAVA","evidence":{"text":"Java"}}],
             "experience":[{"evidence":{"text":"Engineer. mail@example.test https://example.test"}}],
             "name":"Hidden Name"}
            """);
        String biography = CaseModelInputs.biography(facts);
        assertTrue(biography.startsWith("Java. Engineer."));
        assertTrue(CaseModelInputs.biography(json.readTree("{\"experience\":[{\"evidence\":{\"text\":\"Engineer 2020-01 2023-01\"}}]}"))
                .contains("2020-01 2023-01"));
        assertFalse(biography.contains("@")); assertFalse(biography.contains("Hidden Name"));
    }
}
