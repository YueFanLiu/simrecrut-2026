package fr.isep.simrecrut.domain.assessment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.service.assessment.CaseConfiguration;
import fr.isep.simrecrut.service.assessment.CaseException;
import fr.isep.simrecrut.workspace.Facts;
import java.math.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

/** Deterministic professional-only calculations; no persistence or network access. */
@Component
public class CaseMatchingEngine {
    private static final List<String> GROUPS = List.of("SKILLS", "EXPERIENCE", "EDUCATION", "LANGUAGES", "PROJECTS");
    private final ObjectMapper json;
    public CaseMatchingEngine(ObjectMapper json) { this.json = json; }

    public ObjectNode draft(JsonNode professional, List<String> absent, JsonNode previous) {
        ObjectNode wrapper = json.createObjectNode(); wrapper.set("professional", professional);
        wrapper.putArray("warnings");
        ObjectNode validated = Facts.validate(wrapper);
        List<String> absence = absent == null ? List.of() : absent;
        if (new HashSet<>(absence).size() != absence.size() || !GROUPS.containsAll(absence)) {
            throw new IllegalArgumentException("Invalid confirmed-absence categories.");
        }
        for (String category : GROUPS) {
            String group = category.toLowerCase(Locale.ROOT);
            if (absence.contains(category) && !professional.path(group).isEmpty()) {
                throw new IllegalArgumentException("A category with facts cannot be confirmed absent.");
            }
            for (JsonNode fact : validated.path("professional").path(group)) {
                boolean same = false;
                if (previous != null) for (JsonNode old : previous.path("professional").path(group)) {
                    if (old.equals(fact)) same = true;
                }
                if (!same) {
                    ObjectNode evidence = ((ObjectNode) fact).putObject("evidence");
                    evidence.put("source", "USER_SUPPLIED").put("text", "").putNull("page");
                }
            }
        }
        validated.set("confirmedAbsent", json.valueToTree(absence));
        return validated;
    }

    public List<String> unresolved(JsonNode draft, JobVersion job, JsonNode requirements,
            CaseConfiguration.Bundle config, String referenceMonth) {
        List<String> errors = new ArrayList<>();
        try { calculate(draft, job, requirements, config, referenceMonth); }
        catch (CaseException exception) {
            if (exception.status() != 422) throw exception;
            errors.add(exception.getMessage());
        }
        return errors;
    }

    public ObjectNode calculate(JsonNode draft, JobVersion job, JsonNode requirements,
            CaseConfiguration.Bundle config, String referenceMonth) {
        try {
            JsonNode active = json.readTree(job.activeCriteriaJson);
            List<BigDecimal> weights = List.of(job.weightSkills, job.weightExperience, job.weightEducation,
                    job.weightLanguages, job.weightProjects);
            if (active.isEmpty() || weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE) != 0) {
                throw CaseException.dependency();
            }
            ObjectNode result = json.createObjectNode(), professional = result.putObject("professional");
            ArrayNode matches = professional.putArray("m"), w = professional.putArray("w"), mw = professional.putArray("mw");
            BigDecimal score = BigDecimal.ZERO;
            boolean mandatory = true;
            for (int i = 0; i < GROUPS.size(); i++) {
                String category = GROUPS.get(i);
                List<JsonNode> rows = new ArrayList<>();
                for (JsonNode requirement : requirements) if (category.equals(requirement.path("criterionType").asText())
                        && requirement.path("assessmentIncluded").asBoolean()) rows.add(requirement);
                BigDecimal match = BigDecimal.ZERO;
                boolean enabled = CaseConfiguration.contains(active, category);
                if (enabled) {
                    if (rows.isEmpty()) throw CaseException.dependency();
                    JsonNode facts = draft.path("professional").path(category.toLowerCase(Locale.ROOT));
                    if (facts.isEmpty() && !CaseConfiguration.contains(draft.path("confirmedAbsent"), category)) {
                        unresolved(category + ": confirm the facts or explicitly confirm their absence.");
                    }
                    match = match(category, facts, rows, config.rule(), referenceMonth);
                    for (JsonNode row : rows) if (row.path("mandatory").asBoolean()) {
                        BigDecimal value = match(category, facts, List.of(row), config.rule(), referenceMonth);
                        if (category.equals("EXPERIENCE")) {
                            BigDecimal years = years(facts, row.path("payload").path("relevantRoleFamilyCodes"),
                                    config.rule(), referenceMonth);
                            mandatory &= years.compareTo(row.path("payload").path("minimumYears").decimalValue()) >= 0;
                        } else mandatory &= value.compareTo(BigDecimal.ONE) == 0;
                    }
                } else if (weights.get(i).signum() != 0) throw CaseException.dependency();
                BigDecimal contribution = match.multiply(weights.get(i)).setScale(7, RoundingMode.HALF_UP);
                matches.add(match); w.add(weights.get(i)); mw.add(contribution); score = score.add(contribution);
            }
            professional.put("g", mandatory ? 1 : 0);
            result.put("score", score).put("displayScore", score.multiply(BigDecimal.valueOf(100)))
                    .put("decision", mandatory && score.compareTo(config.protocol().tRule()) >= 0 ? "Accept" : "Reject");
            result.putArray("strengths"); result.putArray("gaps");
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw CaseException.dependency(); }
    }

    private BigDecimal match(String category, JsonNode facts, List<JsonNode> rows, JsonNode rule, String month) {
        if (category.equals("EXPERIENCE")) {
            BigDecimal target = BigDecimal.ZERO; Set<String> families = new HashSet<>();
            for (JsonNode row : rows) {
                BigDecimal years = row.path("payload").path("targetYears").decimalValue();
                if (years.signum() <= 0) throw CaseException.dependency();
                target = target.max(years);
                row.path("payload").path("relevantRoleFamilyCodes").forEach(v -> families.add(v.asText()));
            }
            BigDecimal value = years(facts, json.valueToTree(families), rule, month);
            return value.divide(target, 7, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        if (category.equals("EDUCATION")) {
            for (JsonNode row : rows) {
                JsonNode payload = row.path("payload"); boolean met = false;
                int minimum = rank(rule, "degreeOrder", payload.path("minimumDegreeLevelCode").asText());
                for (JsonNode fact : facts) {
                    int degree = rank(rule, "degreeOrder", fact.path("degreeLevelCode").asText());
                    boolean subjectMatches = payload.path("unrestrictedSubject").asBoolean();
                    if (!subjectMatches) subjectMatches = CaseConfiguration.contains(payload.path("acceptedSubjectCodes"),
                            code(rule, "subjectCodes", fact.path("subjectCode").asText()));
                    met |= degree >= minimum && subjectMatches;
                }
                if (!met) return BigDecimal.ZERO;
            }
            return BigDecimal.ONE;
        }
        if (category.equals("LANGUAGES")) {
            Map<String, Integer> needed = new HashMap<>(), actual = new HashMap<>();
            for (JsonNode row : rows) for (JsonNode language : row.path("payload").path("requiredLanguages")) {
                needed.merge(code(rule, "languageCodes", language.path("languageCode").asText()),
                        rank(rule, "languageLevelOrder", language.path("minimumLevelCode").asText()), Math::max);
            }
            for (JsonNode fact : facts) {
                String language = code(rule, "languageCodes", fact.path("languageCode").asText());
                if (needed.containsKey(language)) actual.merge(language,
                        rank(rule, "languageLevelOrder", fact.path("levelCode").asText()), Math::max);
            }
            return fraction(needed.entrySet().stream().filter(e -> actual.getOrDefault(e.getKey(), -1) >= e.getValue()).count(), needed.size());
        }
        Set<String> needed = new HashSet<>(), actual = new HashSet<>();
        String field = category.equals("SKILLS") ? "assessedSkillCodes" : "assessedConditionCodes";
        String catalogue = category.equals("SKILLS") ? "skillCodes" : "conditionCodes";
        for (JsonNode row : rows) row.path("payload").path(field).forEach(v -> needed.add(code(rule, catalogue, v.asText())));
        for (JsonNode fact : facts) {
            if (category.equals("SKILLS")) actual.add(code(rule, catalogue, fact.path("skillCode").asText()));
            else fact.path("supportedConditionCodes").forEach(v -> actual.add(code(rule, catalogue, v.asText())));
        }
        return fraction(needed.stream().filter(actual::contains).count(), needed.size());
    }

    private BigDecimal years(JsonNode facts, JsonNode families, JsonNode rule, String month) {
        List<long[]> intervals = new ArrayList<>(); List<BigDecimal> durations = new ArrayList<>();
        YearMonth reference = YearMonth.parse(month);
        for (JsonNode fact : facts) {
            String family = code(rule, "roleFamilyCodes", fact.path("roleFamilyCode").asText());
            if (!CaseConfiguration.contains(families, family)) continue;
            JsonNode start = fact.path("startMonth"), end = fact.path("endMonth"), reviewed = fact.path("reviewedRelevantYears");
            if (start.isTextual() && (end.isTextual() || fact.path("isCurrent").asBoolean())) {
                if (reviewed.isNumber()) unresolved("EXPERIENCE: use dates or reviewed years, not both.");
                YearMonth from = YearMonth.parse(start.asText());
                YearMonth to = fact.path("isCurrent").asBoolean() ? reference : YearMonth.parse(end.asText());
                if (from.isAfter(to) || to.isAfter(reference)) unresolved("EXPERIENCE: resolve future or conflicting dates.");
                intervals.add(new long[]{from.getLong(java.time.temporal.ChronoField.PROLEPTIC_MONTH),
                        to.getLong(java.time.temporal.ChronoField.PROLEPTIC_MONTH)});
            } else if (reviewed.isNumber()) durations.add(reviewed.decimalValue());
            else unresolved("EXPERIENCE: resolve dates or supply reviewed relevant years.");
        }
        if (!durations.isEmpty()) {
            if (!intervals.isEmpty() || durations.size() > 1) unresolved("EXPERIENCE: resolve possible overlap using dated periods.");
            return durations.get(0);
        }
        intervals.sort(Comparator.comparingLong(a -> a[0]));
        long months = 0, from = 0, to = 0; boolean first = true;
        for (long[] interval : intervals) {
            if (first) { from = interval[0]; to = interval[1]; first = false; }
            else if (interval[0] <= to) to = Math.max(to, interval[1]);
            else { months += to - from; from = interval[0]; to = interval[1]; }
        }
        if (!first) months += to - from;
        return BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 7, RoundingMode.HALF_UP);
    }
    private String code(JsonNode rule, String catalogue, String value) {
        String normalized = rule.path("aliases").path(catalogue).path(value).asText(value);
        if (!CaseConfiguration.contains(rule.path("catalogs").path(catalogue), normalized)) {
            unresolved("Resolve unsupported " + catalogue + " values.");
        }
        return normalized;
    }
    private int rank(JsonNode rule, String order, String value) {
        JsonNode list = rule.path(order);
        if (!list.isArray() || list.isEmpty()) throw CaseException.dependency();
        for (int i = 0; i < list.size(); i++) if (list.get(i).asText().equals(value)) return i;
        unresolved("Resolve unsupported " + order + " values."); return -1;
    }
    private BigDecimal fraction(long numerator, int denominator) {
        if (denominator <= 0) throw CaseException.dependency();
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 7, RoundingMode.HALF_UP);
    }
    private void unresolved(String message) { throw new CaseException(422, "UNRESOLVED_FACTS", message); }
}
