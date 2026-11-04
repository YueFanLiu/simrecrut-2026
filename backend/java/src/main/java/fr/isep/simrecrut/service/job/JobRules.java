package fr.isep.simrecrut.service.job;

import static fr.isep.simrecrut.dto.job.JobDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.entity.job.JobVersion;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class JobRules {
    private final ObjectMapper json;
    private final Path root;

    public JobRules(ObjectMapper json, @Value("${simrecrut.jobs.rule-root:model_artifacts/rules}") String root) {
        this.json = json;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** Load a server-owned, immutable rule file; never accept artifact paths from HTTP input. */
    public JsonNode catalogue(JobVersion rule) {
        if (rule == null || !"FROZEN".equals(rule.ruleStatus)) {
            throw new JobException(503, "A frozen matching rule must be configured before creating or publishing jobs.");
        }
        try {
            Path path = root.resolve(rule.configPath).normalize();
            if (!path.startsWith(root) || !path.toRealPath().startsWith(root.toRealPath())
                    || Files.size(path) > 1_048_576) throw new IllegalArgumentException();
            byte[] bytes = Files.readAllBytes(path);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(rule.configSha256)) throw new IllegalArgumentException();
            JsonNode catalogue = json.readTree(bytes).path("catalogs");
            for (String key : List.of("skillCodes", "roleFamilyCodes", "degreeLevelCodes", "subjectCodes",
                    "languageCodes", "languageLevelCodes", "conditionCodes")) {
                JsonNode values = catalogue.path(key);
                if (!values.isArray() || values.isEmpty()) throw new IllegalArgumentException();
                Set<String> seen = new HashSet<>();
                for (JsonNode value : values) {
                    if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_+.#-]{1,64}")
                            || !seen.add(value.asText())) throw new IllegalArgumentException();
                }
            }
            return catalogue;
        } catch (Exception error) {
            throw new JobException(503, "The frozen matching rule file is unavailable or incompatible.");
        }
    }

    public void checkShape(Requirement row) {
        Set<String> allowed = switch (row.criterionType()) {
            case SKILLS -> Set.of("assessedSkillCodes");
            case EXPERIENCE -> Set.of("targetYears", "relevantRoleFamilyCodes", "minimumYears");
            case EDUCATION -> Set.of("minimumDegreeLevelCode", "acceptedSubjectCodes", "unrestrictedSubject");
            case LANGUAGES -> Set.of("requiredLanguages");
            case PROJECTS -> Set.of("assessedConditionCodes");
        };
        fields(row.payload(), allowed);
        if (row.payload().toString().length() > 16000) throw new IllegalArgumentException("Requirement is too large.");
        row.payload().fields().forEachRemaining(field -> {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (key.endsWith("Codes") && (!value.isArray() || value.size() > 100)) {
                throw new IllegalArgumentException("Expected a bounded code array.");
            }
            if ((key.equals("targetYears") || key.equals("minimumYears"))
                    && (!value.isNumber() || value.decimalValue().signum() < 0
                    || value.decimalValue().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalArgumentException("Invalid experience duration.");
            }
            if (key.equals("unrestrictedSubject") && !value.isBoolean()) throw new IllegalArgumentException();
            if (key.equals("minimumDegreeLevelCode") && !value.isTextual()) throw new IllegalArgumentException();
            if (key.endsWith("Codes")) for (JsonNode code : value) {
                if (!code.isTextual() || !code.asText().matches("[A-Za-z0-9_+.#-]{1,64}")) {
                    throw new IllegalArgumentException("Invalid requirement code.");
                }
            }
        });
        if (row.payload().has("requiredLanguages")) {
            JsonNode values = row.payload().path("requiredLanguages");
            if (!values.isArray() || values.size() > 100) throw new IllegalArgumentException();
            for (JsonNode value : values) {
                fields(value, Set.of("languageCode", "minimumLevelCode"));
                if (!value.path("languageCode").isTextual() || !value.path("minimumLevelCode").isTextual()) {
                    throw new IllegalArgumentException("Invalid language requirement.");
                }
            }
        }
    }

    private void fields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) throw new IllegalArgumentException("Expected a requirement object.");
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unsupported requirement payload field.");
        });
    }

    public Validation validate(JobVersion row, List<Requirement> requirements, JsonNode active, Weights weights) {
        JsonNode catalogue = catalogue(row);
        List<Issue> errors = new ArrayList<>();
        Set<String> categories = new HashSet<>();
        active.forEach(value -> categories.add(value.asText()));
        if (categories.isEmpty()) issue(errors, "activeCriteria", "EMPTY", "Select at least one assessment area.");
        BigDecimal sum = BigDecimal.ZERO;
        for (Criterion criterion : Criterion.values()) {
            BigDecimal value = weights.values().get(criterion.ordinal());
            sum = sum.add(value);
            if (!categories.contains(criterion.name()) && value.signum() != 0) {
                issue(errors, "weights." + criterion.name(), "INACTIVE_WEIGHT", "An inactive area must have zero weight.");
            }
            if (categories.contains(criterion.name()) && requirements.stream().noneMatch(r ->
                    r.criterionType() == criterion && r.assessmentIncluded())) {
                issue(errors, "requirements", "MISSING_AREA", "Add an assessed requirement for " + criterion + ".");
            }
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) issue(errors, "weights", "INVALID_TOTAL", "Weights must total exactly one.");
        for (Requirement requirement : requirements) {
            String field = "requirements." + requirement.requirementCode();
            if (!requirement.reviewed() || !requirement.warnings().isEmpty()) {
                issue(errors, field, "REVIEW_REQUIRED", "Review the source and resolve all warnings.");
            }
            if (requirement.mandatory() && !requirement.assessmentIncluded()
                    || requirement.assessmentIncluded() && !categories.contains(requirement.criterionType().name())) {
                issue(errors, field, "INACTIVE_REQUIREMENT", "Assessed and mandatory requirements need an active area.");
            }
            if (requirement.assessmentIncluded() && !complete(requirement, catalogue)) {
                issue(errors, field + ".payload", "UNRESOLVED_PAYLOAD", "Complete the structured requirement using frozen rule codes.");
            }
        }
        if ("RUNNING".equals(row.extractionStatus)) issue(errors, "extractionStatus", "RUNNING", "Wait for extraction.");
        return new Validation(errors.isEmpty(), List.copyOf(errors), requirements.stream().anyMatch(r -> !r.publicVisible())
                ? List.of("Hidden requirements are omitted from Candidate responses.") : List.of());
    }

    private boolean complete(Requirement row, JsonNode catalogue) {
        JsonNode p = row.payload();
        return switch (row.criterionType()) {
            case SKILLS -> codes(p.path("assessedSkillCodes"), catalogue.path("skillCodes"));
            case EXPERIENCE -> p.path("targetYears").isNumber() && p.path("targetYears").decimalValue().signum() > 0
                    && codes(p.path("relevantRoleFamilyCodes"), catalogue.path("roleFamilyCodes"))
                    && (!row.mandatory() || p.path("minimumYears").isNumber()
                    && p.path("minimumYears").decimalValue().signum() >= 0);
            case EDUCATION -> contains(catalogue.path("degreeLevelCodes"), p.path("minimumDegreeLevelCode"))
                    && (p.path("unrestrictedSubject").asBoolean(false)
                    ? !p.has("acceptedSubjectCodes") || p.path("acceptedSubjectCodes").isEmpty()
                    : codes(p.path("acceptedSubjectCodes"), catalogue.path("subjectCodes")));
            case LANGUAGES -> languages(p.path("requiredLanguages"), catalogue);
            case PROJECTS -> codes(p.path("assessedConditionCodes"), catalogue.path("conditionCodes"));
        };
    }

    private boolean languages(JsonNode items, JsonNode catalogue) {
        if (!items.isArray() || items.isEmpty()) return false;
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            if (!contains(catalogue.path("languageCodes"), item.path("languageCode"))
                    || !contains(catalogue.path("languageLevelCodes"), item.path("minimumLevelCode"))
                    || !seen.add(item.path("languageCode").asText())) return false;
        }
        return true;
    }

    private boolean codes(JsonNode items, JsonNode allowed) {
        if (!items.isArray() || items.isEmpty()) return false;
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) if (!contains(allowed, item) || !seen.add(item.asText())) return false;
        return true;
    }

    private boolean contains(JsonNode allowed, JsonNode value) {
        if (!value.isTextual()) return false;
        for (JsonNode item : allowed) if (item.equals(value)) return true;
        return false;
    }

    private void issue(List<Issue> errors, String field, String code, String message) {
        errors.add(new Issue(field, code, message));
    }
}
