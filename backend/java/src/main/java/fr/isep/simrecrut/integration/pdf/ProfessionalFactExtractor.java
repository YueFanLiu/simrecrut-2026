package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.node.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Extract conservative professional suggestions; evidence never copies identity lines. */
final class ProfessionalFactExtractor {
    private final Set<String> seen = new HashSet<>();
    private String section = "";
    private ObjectNode currentProject;
    private ObjectNode currentExperience;
    private static final List<String> SKILLS = List.of("Java", "JavaScript", "TypeScript", "Spring Boot",
        "MySQL", "PostgreSQL", "SQL", "Python", "Git", "Docker", "HTML", "CSS", "Vue", "React", "Linux", "Rust", "RocksDB", "WebAssembly");
    private static final Pattern MONTH = Pattern.compile("(?<!\\d)(20\\d{2}|19\\d{2})-(0[1-9]|1[0-2])(?!\\d)");

    void extract(ObjectNode facts, String text, Integer page) {
        currentProject = null; // A single evidence page must support every suggested project skill.
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim(), normalized = normalize(line);
            if (line.isEmpty()) currentProject = null;
            if (normalized.matches("(?:work |professional )?(?:experience|experiences)|experience professionnelle")) section = "experience";
            else if (normalized.matches("education|formation|formations")) section = "education";
            else if (normalized.matches("languages|language|langues")) section = "languages";
            else if (normalized.matches("projects|project|projets|projet|personal projects.*|open-source contributions")) { section = "projects"; currentProject = null; }
            else if (normalized.matches("skills|competences|technical skills")) section = "skills";
            else if (normalized.matches("research experience|selected blog writings.*")) section = "other";
            if (!section.equals("experience")) currentExperience = null;
            // A dated company heading starts a new employment block before its title line.
            if (section.equals("experience") && MONTH.matcher(normalizeDates(line)).find()) currentExperience = null;
            List<String> supported = new ArrayList<>();
            for (String skill : SKILLS) {
                String aliases = skill.equals("PostgreSQL") ? "PostgreSQL|Postgres" : skill.equals("WebAssembly") ? "WebAssembly|Web-Assembly" : Pattern.quote(skill);
                if (Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + aliases + ")(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE).matcher(line).find()
                    && !normalized.matches(".*(?:no |not |without |sans ).*")) {
                    supported.add(skill);
                    if (seen.add("skill:" + skill)) evidence(facts.withArray("skills").addObject().put("skillCode", skill), skill, page);
                }
            }
            // Adjacent dates support a role, but year-only dates remain unknown.
            String context = line + " " + (i + 1 < lines.length ? lines[i + 1] : "");
            if (section.equals("experience")) {
                String role = normalized.matches(".*(?:software engineer|software developer|member of technical staff|(?:database|storage) (?:system )?r&d intern|developpeur|ingenieur logiciel).*") ? "SOFTWARE_ENGINEERING"
                    : normalized.matches(".*(?:data analyst|analyste de donnees).*") ? "DATA_ANALYSIS" : null;
                if (role != null) {
                    String previous = i > 0 ? lines[i - 1].trim() : "";
                    // Prefer dates on the title, then a company heading immediately above it.
                    boolean previousHeading = !previous.startsWith("•") && !previous.startsWith("-")
                        && !normalize(previous).matches(".*(?:engineer|developer|intern|developpeur).*");
                    String dates = MONTH.matcher(normalizeDates(line)).find() ? line
                        : previousHeading && MONTH.matcher(normalizeDates(previous)).find() ? previous : context;
                    List<String> months = new ArrayList<>(); Matcher matcher = MONTH.matcher(normalizeDates(dates));
                    while (matcher.find() && months.size() < 2) months.add(matcher.group());
                    boolean current = normalize(dates).matches(".*\\b(?:present|current|today|aujourd'hui|actuel)\\b.*");
                    String key = role + months;
                    if (facts.withArray("experience").size() < 100 && seen.add("experience:" + key)) {
                        ObjectNode fact = facts.withArray("experience").addObject().put("roleFamilyCode", role).put("isCurrent", current);
                        if (months.isEmpty()) fact.putNull("startMonth"); else fact.put("startMonth", months.get(0));
                        if (current || months.size() < 2 || months.get(1).compareTo(months.get(0)) < 0) fact.putNull("endMonth"); else fact.put("endMonth", months.get(1));
                        fact.putNull("reviewedRelevantYears"); fact.putArray("supportedSkillCodes");
                        evidence(fact, role + (months.isEmpty() ? "" : " " + String.join(" ", months)), page);
                        currentExperience = fact;
                    }
                }
                if (currentExperience != null) {
                    ArrayNode skills = currentExperience.withArray("supportedSkillCodes");
                    for (String skill : supported) {
                        boolean exists = false;
                        for (var value : skills) if (value.asText().equals(skill)) exists = true;
                        if (!exists) skills.add(skill);
                    }
                }
            }
            if (section.equals("education")) {
                String degree = normalized.matches(".*\\b(?:phd|doctorat|doctorate)\\b.*") ? "PHD"
                    : normalized.matches(".*\\b(?:master|msc)\\b.*") ? "MASTER"
                    : normalized.matches(".*\\b(?:bachelor|licence|bsc)\\b.*") ? "BACHELOR" : null;
                if (degree != null) {
                    String subject = normalized.matches(".*(?:computer science|informatique).*") ? "COMPUTER_SCIENCE"
                        : normalized.contains("mathemat") ? "MATHEMATICS"
                        : normalized.matches(".*(?:engineering|ingenierie).*") ? "ENGINEERING"
                        : normalized.matches(".*(?:business|commerce).*") ? "BUSINESS" : "UNKNOWN";
                    if (seen.add("education:" + degree + subject)) evidence(facts.withArray("education").addObject()
                        .put("degreeLevelCode", degree).put("subjectCode", subject), degree + " " + subject, page);
                }
            }
            if (section.equals("languages")) {
                Map<String, String> names = Map.of("EN", "english|anglais", "FR", "french|francais", "DE", "german|allemand", "ES", "spanish|espagnol", "ZH", "chinese|chinois");
                for (var entry : names.entrySet()) {
                    if (Pattern.compile("\\b(?:" + entry.getValue() + ")\\b").matcher(normalized).find() && seen.add("language:" + entry.getKey())) {
                        Matcher level = Pattern.compile("\\b[A-C][12]\\b", Pattern.CASE_INSENSITIVE).matcher(line);
                        String code = level.find() ? level.group().toUpperCase(Locale.ROOT) : "UNKNOWN";
                        evidence(facts.withArray("languages").addObject().put("languageCode", entry.getKey()).put("levelCode", code), entry.getKey() + " " + code, page);
                    }
                }
            }
            if (section.equals("projects") && !supported.isEmpty() && facts.withArray("projects").size() < 100) {
                if (currentProject == null || normalized.matches("^(?:project|projet)\\b.*")) {
                    currentProject = facts.withArray("projects").addObject().put("projectId", "PROJECT_" + facts.withArray("projects").size());
                    currentProject.putArray("supportedSkillCodes"); currentProject.putArray("supportedConditionCodes");
                }
                ArrayNode skills = currentProject.withArray("supportedSkillCodes");
                for (String skill : supported) if (!skills.toString().contains("\"" + skill + "\"")) skills.add(skill);
                ArrayNode conditions = currentProject.withArray("supportedConditionCodes");
                if (normalized.contains("api") && !conditions.toString().contains("API_DEVELOPMENT")) conditions.add("API_DEVELOPMENT");
                if (normalized.contains("web") && !conditions.toString().contains("WEB_APPLICATION")) conditions.add("WEB_APPLICATION");
                evidence(currentProject, skills.toString(), page);
            }
        }
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll(":$", "").trim();
    }
    private static String normalizeDates(String text) {
        String value = normalize(text);
        String[] months = {"january|janvier|jan", "february|fevrier|feb|fev", "march|mars|mar", "april|avril|apr|avr", "may|mai", "june|juin|jun", "july|juillet|jul", "august|aout|aug", "september|septembre|sep|sept", "october|octobre|oct", "november|novembre|nov", "december|decembre|dec"};
        for (int i = 0; i < months.length; i++) value = value.replaceAll("\\b(?:" + months[i] + ")\\.?\\s+(19\\d{2}|20\\d{2})\\b", "$1-" + String.format(Locale.ROOT, "%02d", i + 1));
        return value.replaceAll("(?<!\\d)(19\\d{2}|20\\d{2})/(0[1-9]|1[0-2])(?!\\d)", "$1-$2")
            .replaceAll("(?<!\\d)(0[1-9]|1[0-2])/(19\\d{2}|20\\d{2})(?!\\d)", "$2-$1");
    }
    private static void evidence(ObjectNode fact, String text, Integer page) {
        ObjectNode evidence = fact.putObject("evidence").put("text", text).put("source", "EXTRACTED");
        if (page == null) evidence.putNull("page"); else evidence.put("page", page);
    }
}
