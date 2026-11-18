package fr.isep.simrecrut.integration.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Assemble the frozen training order; never infer missing professional facts. */
public final class ModelFeatureEncoder {
    public static final Map<String, String> FAMILIES;
    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("SCHOOL_CATEGORY", "school"); names.put("REFERRAL", "referral");
        names.put("GENDER", "gender"); names.put("ETHNICITY", "ethnicity"); names.put("NAME_CUE", "name_cue");
        FAMILIES = Collections.unmodifiableMap(names);
    }
    private ModelFeatureEncoder() { }

    /** Reject reordered fields even when the advertised width remains 39. */
    public static void validateSchema(JsonNode schema) {
        List<String> expected = new ArrayList<>();
        for (String prefix : List.of("match", "weight", "contribution")) {
            for (String area : List.of("skills", "experience", "education", "languages", "projects")) {
                expected.add(prefix + "_" + area);
            }
        }
        expected.add("mandatory_pass");
        Map<String, List<String>> choices = new LinkedHashMap<>();
        choices.put("school", List.of("prestigious", "ordinary", "unknown"));
        choices.put("referral", List.of("yes", "no", "unknown"));
        choices.put("gender", List.of("woman", "man", "unknown"));
        List<String> origins = List.of("east_asian", "south_asian", "black", "white",
                "middle_eastern_or_north_african", "mixed_or_other", "unknown");
        choices.put("ethnicity", origins); choices.put("name_cue", origins);
        if (schema.path("research_families").size() != 5) throw new IllegalArgumentException("Expected five research families.");
        int familyIndex = 0;
        for (var family : choices.entrySet()) {
            if (!family.getKey().equals(schema.path("research_families").path(familyIndex++).asText())) {
                throw new IllegalArgumentException("Invalid research family order.");
            }
            JsonNode values = schema.path("research_order").path(family.getKey());
            if (values.size() != family.getValue().size()) throw new IllegalArgumentException("Invalid research width.");
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).asText().equals(family.getValue().get(i))) {
                    throw new IllegalArgumentException("Invalid research category order.");
                }
                expected.add(family.getKey() + "_" + values.get(i).asText());
            }
        }
        if (schema.path("input_width").asInt() != 39 || schema.path("input_order").size() != 39) {
            throw new IllegalArgumentException("Expected the 39-input schema.");
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(schema.path("input_order").path(i).asText())) {
                throw new IllegalArgumentException("Invalid model feature order.");
            }
        }
    }

    public static ArrayNode encode(ObjectMapper json, JsonNode professional, JsonNode research,
            JsonNode schema, boolean neutral) {
        validateSchema(schema);
        ArrayNode result = json.createArrayNode();
        for (String key : List.of("m", "w", "mw")) {
            JsonNode group = professional.path(key);
            if (!group.isArray() || group.size() != 5) throw new IllegalArgumentException("Missing professional values.");
            for (JsonNode value : group) {
                if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0 || value.asDouble() > 1) {
                    throw new IllegalArgumentException("Professional values must be finite fractions.");
                }
                result.add(value.decimalValue());
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            total = total.add(result.get(5 + i).decimalValue());
            BigDecimal product = result.get(i).decimalValue().multiply(result.get(5+i).decimalValue()).setScale(7, RoundingMode.HALF_UP);
            if (product.subtract(result.get(10+i).decimalValue()).abs().compareTo(new BigDecimal("0.0000001")) > 0) {
                throw new IllegalArgumentException("Invalid weighted contribution.");
            }
        }
        if (total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.0000001")) > 0
                || !professional.path("g").isInt() || !Set.of(0, 1).contains(professional.path("g").asInt())) {
            throw new IllegalArgumentException("Invalid weights or mandatory flag.");
        }
        result.add(professional.path("g").asInt());
        Map<String, String> transport = Map.of("school", "schoolPrestige", "referral", "referral",
                "gender", "gender", "ethnicity", "ethnicity", "name_cue", "nameCue");
        for (String family : List.of("school", "referral", "gender", "ethnicity", "name_cue")) {
            JsonNode choices = schema.path("research_order").path(family);
            String selected = research.path(transport.get(family)).asText("UNKNOWN").toLowerCase(Locale.ROOT);
            boolean known = false;
            for (JsonNode choice : choices) if (choice.asText().equals(selected)) known = true;
            if (!known) throw new IllegalArgumentException("Unsupported research category.");
            for (JsonNode choice : choices) result.add(!neutral && choice.asText().equals(selected) ? 1 : 0);
        }
        if (result.size() != 39) throw new IllegalArgumentException("The feature order is incompatible.");
        return result;
    }
}
