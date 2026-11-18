package fr.isep.simrecrut.service.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.Protocol;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class CaseConfiguration {
    private static final String LEGACY_DEV_RULE_SHA256 =
            "0a99d721e4a46d77bdd30be20af5643c10352fd547faa542f00f0e0bf6b79af7";
    private final ObjectMapper json;
    private final Path rules;
    private final Path artifacts;
    public CaseConfiguration(ObjectMapper json,
            @Value("${simrecrut.jobs.rule-root:model_artifacts/rules}") String rules,
            @Value("${simrecrut.storage.model-artifacts:model_artifacts}") String artifacts) {
        this.json = json; this.rules = Path.of(rules).toAbsolutePath().normalize();
        this.artifacts = Path.of(artifacts).toAbsolutePath().normalize();
    }
    public Bundle load(JobVersion job, Protocol protocol) {
        if (job == null || protocol == null || !"FROZEN".equals(job.ruleStatus)
                || protocol.matchingRuleVersionId() != job.matchingRuleVersionId || !Set.of(32, 39).contains(protocol.dimensionCount())) {
            throw CaseException.dependency();
        }
        JsonNode rule = read(rules, job.configPath, job.configSha256);
        if (protocol.dimensionCount() == 32 && "job-rule-dev-v1".equals(job.matchingRuleVersion)
                && LEGACY_DEV_RULE_SHA256.equals(job.configSha256)) {
            // The original frozen development rule lists these levels in ascending order.
            ObjectNode compatible = rule.deepCopy();
            compatible.set("degreeOrder", rule.path("catalogs").path("degreeLevelCodes"));
            compatible.set("languageLevelOrder", rule.path("catalogs").path("languageLevelCodes"));
            rule = compatible;
        }
        JsonNode schema = read(artifacts, protocol.schemaPath(), protocol.schemaSha256());
        for (String catalogue : List.of("skillCodes", "roleFamilyCodes", "degreeLevelCodes", "subjectCodes",
                "languageCodes", "languageLevelCodes", "conditionCodes")) {
            JsonNode values = rule.path("catalogs").path(catalogue);
            Set<String> unique = new HashSet<>();
            if (!values.isArray() || values.isEmpty()) throw CaseException.dependency();
            for (JsonNode value : values) if (!value.isTextual()
                    || !value.asText().matches("[A-Za-z0-9_+.#-]{1,64}") || !unique.add(value.asText())) {
                throw CaseException.dependency();
            }
        }
        if (protocol.dimensionCount() == 39) {
            try { fr.isep.simrecrut.integration.ml.ModelFeatureEncoder.validateSchema(schema); }
            catch (IllegalArgumentException error) { throw CaseException.dependency(); }
            if (schema.path("input_width").asInt() != 39 || schema.path("input_order").size() != 39
                    || !protocol.schemaVersion().equals(schema.path("schema_version").asText())) {
                throw CaseException.dependency();
            }
            var catalog = ((com.fasterxml.jackson.databind.node.ObjectNode) schema).putObject("researchAttributes");
            fr.isep.simrecrut.integration.ml.ModelFeatureEncoder.FAMILIES.forEach((type, family) -> {
                var values = catalog.putArray(type);
                schema.path("research_order").path(family).forEach(v -> values.add(v.asText().toUpperCase(Locale.ROOT)));
            });
        }
        Set<String> expectedTypes = new HashSet<>(List.of("SCHOOL_CATEGORY", "REFERRAL", "GENDER", "ETHNICITY"));
        if (protocol.dimensionCount() == 39) expectedTypes.add("NAME_CUE");
        if (!new HashSet<>(types(schema)).equals(expectedTypes)) throw CaseException.dependency();
        for (String type : types(schema)) {
            JsonNode values = schema.path("researchAttributes").path(type);
            if (!values.isArray() || values.isEmpty() || !contains(values, "UNKNOWN")) throw CaseException.dependency();
            Set<String> unique = new HashSet<>();
            for (JsonNode value : values) if (!value.isTextual() || !value.asText().matches("[A-Z0-9_]{1,64}")
                    || !unique.add(value.asText())) throw CaseException.dependency();
        }
        return new Bundle(rule, schema, protocol);
    }
    private JsonNode read(Path root, String relative, String hash) {
        try {
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath())
                    || Files.size(file) > 1_048_576) throw CaseException.dependency();
            byte[] data = Files.readAllBytes(file);
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(hash)) {
                throw CaseException.dependency();
            }
            JsonNode value = json.readTree(data);
            if (value == null || !value.isObject()) throw CaseException.dependency();
            return value;
        } catch (Exception exception) { throw CaseException.dependency(); }
    }
    public static List<String> types(JsonNode schema) {
        List<String> types = new ArrayList<>();
        schema.path("researchAttributes").fieldNames().forEachRemaining(types::add);
        return types;
    }
    public static boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) if (item.isTextual() && item.asText().equals(value)) return true;
        return false;
    }
    public record Bundle(JsonNode rule, JsonNode schema, Protocol protocol) { }
}
