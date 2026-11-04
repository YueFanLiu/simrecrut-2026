package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public final class Facts {
  static final ObjectMapper JSON = new ObjectMapper();
  static final List<String> GROUPS =
      List.of("skills", "experience", "education", "languages", "projects");
  static final Map<String, List<String>> CODES =
      Map.of(
          "skills",
          List.of("skillCode"),
          "experience",
          List.of("roleFamilyCode"),
          "education",
          List.of("degreeLevelCode", "subjectCode"),
          "languages",
          List.of("languageCode", "levelCode"),
          "projects",
          List.of("projectId"));

  public static ObjectNode empty() {
    ObjectNode d = JSON.createObjectNode(), p = d.putObject("professional");
    GROUPS.forEach(p::putArray);
    d.putArray("warnings");
    return d;
  }

  static void require(boolean b, String msg) {
    if (!b) throw new IllegalArgumentException(msg);
  }

  static void keys(JsonNode n, Set<String> allowed) {
    require(n != null && n.isObject(), "Expected an object.");
    n.fieldNames().forEachRemaining(k -> require(allowed.contains(k), "Unsupported field: " + k));
  }

  static void text(JsonNode n, int max, boolean required, String label) {
    require(
        n != null
            && n.isTextual()
            && n.asText().length() <= max
            && (!required || !n.asText().isBlank()),
        "Invalid " + label + ".");
  }

  static void array(JsonNode n, String label) {
    require(
        n != null && n.isArray() && n.size() <= 100, "Invalid " + label + " array (maximum 100).");
  }

  static void month(JsonNode n) {
    require(
        n == null || n.isNull() || (n.isTextual() && n.asText().matches("\\d{4}-(0[1-9]|1[0-2])")),
        "Months must use YYYY-MM.");
  }

  public static ObjectNode validate(JsonNode source) {
    keys(source, Set.of("professional", "warnings"));
    ObjectNode d = source.deepCopy();
    JsonNode p = d.get("professional");
    keys(p, new HashSet<>(GROUPS));
    if (!d.has("warnings")) d.putArray("warnings");
    array(d.get("warnings"), "warnings");
    for (JsonNode w : d.get("warnings")) text(w, 1000, false, "warning");
    for (String group : GROUPS) {
      array(p.get(group), group);
      for (JsonNode raw : p.get(group)) {
        require(raw.isObject(), "Each fact must be an object.");
        ObjectNode item = (ObjectNode) raw;
        Set<String> allowed = new HashSet<>(CODES.get(group));
        allowed.add("evidence");
        if (group.equals("experience"))
          allowed.addAll(
              List.of(
                  "startMonth",
                  "endMonth",
                  "isCurrent",
                  "supportedSkillCodes",
                  "reviewedRelevantYears"));
        if (group.equals("projects"))
          allowed.addAll(List.of("supportedConditionCodes", "supportedSkillCodes"));
        keys(item, allowed);
        for (String code : CODES.get(group)) text(item.get(code), 100, true, code);
        if (!item.has("evidence")) {
          ObjectNode e = item.putObject("evidence");
          e.put("text", "");
          e.putNull("page");
          e.put("source", "EXTRACTED");
        }
        keys(item.get("evidence"), Set.of("text", "page", "source"));
        ObjectNode e = (ObjectNode) item.get("evidence");
        if (!e.has("text")) e.put("text", "");
        text(e.get("text"), 300, false, "evidence");
        if (!e.has("page")) e.putNull("page");
        require(
            e.get("page").isNull()
                || (e.get("page").isIntegralNumber()
                    && e.get("page").asInt() >= 1
                    && e.get("page").asInt() <= 20),
            "Evidence page must be 1–20.");
        if (!e.has("source")) e.put("source", "EXTRACTED");
        require(
            e.get("source").isTextual()
                && Set.of("EXTRACTED", "USER_SUPPLIED").contains(e.get("source").asText()),
            "Invalid evidence source.");
        for (String key : List.of("supportedSkillCodes", "supportedConditionCodes"))
          if (allowed.contains(key)) {
            if (!item.has(key)) item.putArray(key);
            array(item.get(key), key);
            for (JsonNode c : item.get(key)) text(c, 1000, false, key);
          }
        if (group.equals("experience")) {
          for (String key : List.of("startMonth", "endMonth", "reviewedRelevantYears"))
            if (!item.has(key)) item.putNull(key);
          if (!item.has("isCurrent")) item.put("isCurrent", false);
          require(item.get("isCurrent").isBoolean(), "isCurrent must be true or false.");
          month(item.get("startMonth"));
          month(item.get("endMonth"));
          require(
              item.get("startMonth").isNull()
                  || item.get("endMonth").isNull()
                  || item.get("startMonth").asText().compareTo(item.get("endMonth").asText()) <= 0,
              "Experience start month must not be after the end month.");
          require(
              !item.get("isCurrent").asBoolean() || item.get("endMonth").isNull(),
              "A current role must not have an end month.");
          JsonNode years = item.get("reviewedRelevantYears");
          require(
              years.isNull()
                  || (years.isNumber() && years.asDouble() >= 0 && years.asDouble() <= 80),
              "Relevant years must be 0–80.");
        }
      }
    }
    require(
        !d.toString().matches("(?s).*([\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|https?://).*"),
        "Remove email addresses or URLs from the structured data before saving.");
    return d;
  }

  public static boolean hasFacts(JsonNode d) {
    for (String g : GROUPS) if (!d.path("professional").path(g).isEmpty()) return true;
    return false;
  }

  private Facts() {}
}
