package fr.isep.simrecrut.service.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                || protocol.matchingRuleVersionId() != job.matchingRuleVersionId || protocol.dimensionCount() != 32) {
            throw CaseException.dependency();
        }
        JsonNode rule = read(rules, job.configPath, job.configSha256);
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
        for (String type : List.of("SCHOOL_CATEGORY", "REFERRAL", "GENDER", "ETHNICITY")) {
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
    public static boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) if (item.isTextual() && item.asText().equals(value)) return true;
        return false;
    }
    public record Bundle(JsonNode rule, JsonNode schema, Protocol protocol) { }
}
