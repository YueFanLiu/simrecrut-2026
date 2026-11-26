package fr.isep.simrecrut.integration.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.integration.pdf.LocalResumeCleaner;
import fr.isep.simrecrut.workspace.Facts;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the limited text view sent to the provider and verifies excerpts against it. */
final class CvSourceEvidence {
    private static final Pattern SECTION = Pattern.compile("(?iu)^\\s*(?:[•▪]\\s*)?"
            + "(technical skills|core skills|key skills|skills|compétences(?: techniques)?|technologies"
            + "|(?:work |professional |relevant )?experiences?|expériences?(?: professionnelles?)?|employment(?: history)?"
            + "|education(?: and training)?|formation|diplômes|qualifications"
            + "|(?:personal |academic |selected |technical )?projects?|projets?(?: personnels)?"
            + "|languages?|langues|certifications)(?:\\s*[:|]\\s*|\\s*$)");
    private static final Pattern PRIVATE_LINE = Pattern.compile("(?iu)\\b(?:e-?mail|phone|mobile|téléphone|"
            + "contact|address|adresse|born|birth|nationality|nationalité|gender|ethnicity|passport|"
            + "linkedin|github\\s*[:@]|portfolio\\s*[:@]|references?)\\b|@|https?://|www\\.");
    private static final Pattern PERSONAL_HEADING = Pattern.compile("(?iu)^\\s*"
            + "(?:personal (?:details|information)|coordonnées|état civil|references|références|interests|loisirs)\\s*[:|]?");
    private static final Pattern TOKEN = Pattern.compile(
            "(?iu)\\[redacted\\]|\\p{L}[\\p{L}\\p{M}\\p{N}+#]*|\\d+|\\p{Punct}|[–—•]");
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(?:19|20)\\d{2}[-/. ](?:0?[1-9]|1[0-2])(?!\\d)|"
            + "(?<!\\d)(?:0?[1-9]|1[0-2])[-/. ](?:19|20)\\d{2}(?!\\d)");
    private static final Pattern LANGUAGE_MARKER = Pattern.compile(
            "\\b(?:(?iu:english|anglais|french|français|german|allemand|spanish|espagnol|"
            + "chinese|chinois|[ABC][12])|EN|FR|DE|ES|ZH)\\b");
    private static final Pattern SEPARATOR = Pattern.compile("(?iu)\\s*(?:[-–—:()\\[\\],;]|level\\b|niveau\\b|"
            + "cefr\\b|and\\b|et\\b)\\s*|\\s+");
    private static final Pattern NEGATION = Pattern.compile(
            "(?iu)\\b(?:no|not|without|never|lack|learning|beginner|planned|sans|aucune?|non|jamais|prévu)\\b");
    private static final Pattern PRESENT = Pattern.compile("(?iu)\\b(present|current|today|aujourd'hui|actuel|"
            + "maintenant|now)\\b");
    private static final Pattern PROJECT = Pattern.compile(
            "(?iu)\\b(project|projet|built|developed|designed|implemented|créé|développé|conçu)\\b");
    private static final Set<String> PROJECT_CONDITIONS =
            Set.of("REST_API_PROJECT", "SOFTWARE_PROJECT", "API_DEVELOPMENT", "WEB_APPLICATION");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("POSTGRESQL", "Postgres"), Map.entry("SPRING_BOOT", "SpringBoot"),
            Map.entry("WEBASSEMBLY", "Web-Assembly,Wasm"), Map.entry("JAVASCRIPT", "JS"),
            Map.entry("TYPESCRIPT", "TS"), Map.entry("VUE", "Vue.js"),
            Map.entry("BACKEND_DEVELOPER", "back-end developer,backend engineer,développeur backend,ingénieur backend"),
            Map.entry("SOFTWARE_ENGINEERING", "software engineer,software developer,full-stack developer,"
                    + "full stack developer,frontend developer,front-end developer,web developer,développeur logiciel,"
                    + "ingénieur logiciel,développeur web,application developer,member of technical staff"),
            Map.entry("SOFTWARE_TESTING", "software tester,QA engineer,quality assurance engineer,test engineer,"
                    + "QA tester,testeur logiciel,ingénieur test,test analyst,SDET"),
            Map.entry("DATA_ANALYSIS", "data analyst,analyste de données,data analytics"),
            Map.entry("ASSOCIATE", "associate degree,BTS,DUT"),
            Map.entry("BACHELOR", "bachelor's,bachelors,BSc,BS,licence"),
            Map.entry("MASTER", "master's,masters,MSc,MS,ingénieur diplômé"),
            Map.entry("PHD", "doctorate,doctorat,Ph.D"),
            Map.entry("COMPUTER_SCIENCE", "informatics,informatique,computer engineering"),
            Map.entry("MATHEMATICS", "maths,math,mathématiques"),
            Map.entry("ENGINEERING", "ingénierie,génie"), Map.entry("BUSINESS", "commerce,management,gestion"),
            Map.entry("EN", "English,anglais"), Map.entry("FR", "French,français"),
            Map.entry("DE", "German,allemand"), Map.entry("ES", "Spanish,espagnol"),
            Map.entry("ZH", "Chinese,chinois"),
            Map.entry("REST_API_PROJECT", "REST API,RESTful API,API REST"),
            Map.entry("SOFTWARE_PROJECT", "software project,projet logiciel,developed an application,built an application"),
            Map.entry("DEPLOYMENT", "deployed,deployment,déployé,déploiement,release to production"),
            Map.entry("TEST_SUPPORT", "test support,supported testing,support for testing,assisted testing"),
            Map.entry("ENGINEER_MENTORING", "mentored engineers,mentored junior engineers,guided engineers,engineer mentoring"),
            Map.entry("SOLE_QA", "sole QA,only QA,sole tester,unique QA,seul testeur"),
            Map.entry("API_DEVELOPMENT", "developed APIs,developed an API,built an API,API design,"
                    + "REST API,RESTful API,API REST,développement API"),
            Map.entry("WEB_APPLICATION", "web app,web applications,applications web,application web"));
    private static final String[] MONTHS = {"january janvier jan", "february février feb fév fev",
        "march mars mar", "april avril apr avr", "may mai", "june juin jun", "july juillet jul",
        "august août aug", "september septembre sep sept", "october octobre oct",
        "november novembre nov", "december décembre dec déc"};
    private static final Map<String, String> FIELD_CATALOGS = Map.of(
            "skillCode", "skillCodes", "roleFamilyCode", "roleFamilyCodes", "degreeLevelCode", "degreeLevelCodes",
            "subjectCode", "subjectCodes", "languageCode", "languageCodes", "levelCode", "languageLevelCodes");
    private final JsonNode catalogs;
    private final Map<Integer, String> pages = new LinkedHashMap<>();
    private final Map<String, Set<String>> levels = new HashMap<>();
    private final Map<String, Pattern> terms = new HashMap<>();

    CvSourceEvidence(List<LocalResumeCleaner.PdfPage> source, JsonNode catalogs) {
        this.catalogs = catalogs;
        Set<String> words = new HashSet<>(Arrays.asList(("technical core key skills competences technologies work "
                + "professional relevant experience experiences professionnelle professionnelles employment history "
                + "education training formation diplomes qualifications personal academic selected projects project projet "
                + "projets personnels languages language langues certifications software engineer developer testing "
                + "integration qa quality assurance application applications system systems database web full stack "
                + "backend frontend role intern internship senior junior lead developer developpeur ingenieur logiciel "
                + "build built building developed developing designed implemented maintained worked using used with "
                + "test tests tested testing automation automated manual release production deploy deploying mentored "
                + "guided supported support assisted only sole unique seul scored testeur from until to since present "
                + "current today now actuel aujourd hui maintenant de du des au a en et and or the an for in on by "
                + "of year years month months ans annees mois degree diploma level niveau cefr no not without never "
                + "lack learning beginner planned sans aucun aucune non jamais prevu redacted").split(" ")));
        for (String month : MONTHS) for (String word : month.split(" ")) words.add(normalizeWord(word));
        catalogs.elements().forEachRemaining(array -> array.forEach(node -> {
            String code = node.asText();
            List<String> aliases = new ArrayList<>(List.of(code.replace('_', ' ')));
            if (ALIASES.containsKey(code.toUpperCase(Locale.ROOT))) {
                aliases.addAll(List.of(ALIASES.get(code.toUpperCase(Locale.ROOT)).split(",")));
            }
            List<String> patterns = new ArrayList<>();
            for (String alias : aliases) {
                Matcher tokens = TOKEN.matcher(alias);
                while (tokens.find()) words.add(normalizeWord(tokens.group()));
                patterns.add(Pattern.quote(alias));
            }
            terms.put(code, Pattern.compile("(?iu)(?<![\\p{L}\\p{N}+#])(?:"
                    + String.join("|", patterns) + ")(?![\\p{L}\\p{N}+#])"));
        }));
        boolean professional = false;
        for (var page : source) {
            List<String> lines = new ArrayList<>();
            for (String raw : page.text().split("\\R")) {
                if (PERSONAL_HEADING.matcher(raw).find()) professional = false;
                if (PRIVATE_LINE.matcher(raw).find()) continue;
                if (SECTION.matcher(raw).find()) professional = true;
                if (!professional) continue;
                String line = redact(raw, words);
                if (!line.isBlank() && !line.equals("[redacted]")) {
                    lines.add(line);
                    languageLevels(line).forEach((code, values) ->
                            levels.computeIfAbsent(code, ignored -> new HashSet<>()).addAll(values));
                }
            }
            if (!lines.isEmpty()) pages.put(page.number(), String.join("\n", lines));
        }
    }

    private static String redact(String raw, Set<String> words) {
        // Remove numbers outside the few date shapes needed for employment.
        raw = raw.replaceAll("\\b(?:\\d{1,2}[-/.]\\d{1,2}[-/.](?:19|20)\\d{2}|"
                + "(?:19|20)\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})\\b", " [redacted] ")
                .replaceAll("(?<!\\d)(?:\\+\\d(?:[ ()-]*\\d){7,14}|\\d{3}[-. ]\\d{3}[-. ]\\d{4}|"
                        + "0\\d(?:[. ]\\d{2}){4}|\\(\\d{3}\\)\\s*\\d{3}[-. ]\\d{4})(?!\\d)", " [redacted] ");
        StringBuilder result = new StringBuilder();
        Matcher tokens = TOKEN.matcher(raw);
        int end = 0;
        boolean masked = false;
        while (tokens.find()) {
            String token = tokens.group();
            boolean allowed = words.contains(normalizeWord(token))
                    || token.matches("(?:19|20)\\d{2}|0?[1-9]|1[0-2]|[-–—:;,./|()'’+#•]");
            if (!allowed) {
                if (!masked) result.append(" [redacted] ");
            } else {
                if (tokens.start() > end) result.append(' ');
                result.append(token);
            }
            end = tokens.end();
            masked = !allowed;
        }
        return flatten(result.toString());
    }

    Map<Integer, String> pages() { return Collections.unmodifiableMap(pages); }

    ObjectNode validate(JsonNode raw) {
        if (raw.has("warnings")) throw new IllegalArgumentException("The response must contain professional facts only.");
        // Personal and other information are extracted locally; the provider never sees or returns them.
        for (String group : Facts.PROFILE_GROUPS) {
            if (raw.path("professional").has(group)) {
                throw new IllegalArgumentException("The response must contain professional facts only.");
            }
        }
        ObjectNode draft = Facts.validate(raw);
        JsonNode professional = draft.path("professional");
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            var items = (com.fasterxml.jackson.databind.node.ArrayNode) professional.get(group);
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < items.size();) {
                ObjectNode fact = (ObjectNode) items.get(index);
                JsonNode evidence = fact.path("evidence");
                String page = pages.get(evidence.path("page").asInt());
                String excerpt = flatten(evidence.path("text").asText());
                require(page != null && !excerpt.isBlank() && "EXTRACTED".equals(evidence.path("source").asText())
                        && containsExcerpt(flatten(page), excerpt));
                // A genuine quote is necessary, but does not on its own support an unrelated field.
                require(!NEGATION.matcher(excerpt).find());
                if (group.equals("languages")) {
                    String code = fact.path("languageCode").asText();
                    require(matches("languageCode", code, excerpt));
                    Set<String> available = levels.getOrDefault(code, Set.of());
                    if (available.size() != 1) {
                        items.remove(index);
                        continue;
                    }
                    String level = available.iterator().next();
                    require(validCode("levelCode", level) && level.equals(fact.path("levelCode").asText())
                            && languageLevels(excerpt).getOrDefault(code, Set.of()).equals(Set.of(level)));
                } else {
                    for (String field : FIELD_CATALOGS.keySet()) {
                        if (!fact.has(field)) continue;
                        String code = fact.get(field).asText();
                        require(("subjectCode".equals(field) && "UNKNOWN".equals(code) && validCode(field, code))
                                || matches(field, code, excerpt));
                    }
                }
                if (group.equals("experience")) validateExperience(fact, excerpt);
                if (group.equals("projects")) {
                    require(fact.get("projectId").asText().matches("PROJECT_[1-9]\\d{0,2}"));
                    require(!fact.path("supportedConditionCodes").isEmpty() || !fact.path("supportedSkillCodes").isEmpty());
                    require(!fact.path("supportedConditionCodes").isEmpty() || PROJECT.matcher(excerpt).find());
                    for (JsonNode code : fact.path("supportedConditionCodes")) {
                        require(hasTerm("conditionCodes", code.asText(), excerpt));
                        require(!PROJECT_CONDITIONS.contains(code.asText()) || PROJECT.matcher(excerpt).find());
                    }
                }
                for (JsonNode code : fact.path("supportedSkillCodes")) {
                    require(hasTerm("skillCodes", code.asText(), excerpt));
                }
                if (seen.add(fact.toString()) && (!group.equals("projects") || seen.add(fact.get("projectId").asText()))) {
                    index++;
                } else {
                    items.remove(index);
                }
            }
        }
        if (levels.values().stream().anyMatch(values -> values.size() > 1)) {
            draft.withArray("warnings").add("The PDF gives conflicting language levels. Review and enter the correct level.");
        }
        return draft;
    }

    private boolean matches(String field, String code, String excerpt) {
        return hasTerm(FIELD_CATALOGS.get(field), code, excerpt);
    }

    private boolean validCode(String field, String code) {
        for (JsonNode value : catalogs.path(FIELD_CATALOGS.get(field))) if (value.asText().equals(code)) return true;
        return false;
    }

    private boolean hasTerm(String catalog, String code, String excerpt) {
        for (JsonNode value : catalogs.path(catalog)) {
            if (value.asText().equals(code)) return terms.get(code).matcher(excerpt).find();
        }
        return false;
    }

    private static boolean containsExcerpt(String page, String excerpt) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(excerpt) + "(?![\\p{L}\\p{N}])")
                .matcher(page).find();
    }

    private static void validateExperience(JsonNode fact, String excerpt) {
        List<YearMonth> evidence = months(excerpt);
        JsonNode start = fact.path("startMonth"), end = fact.path("endMonth");
        require(fact.path("reviewedRelevantYears").isNull());
        require(start.isNull() || (!evidence.isEmpty() && evidence.size() <= 2 && evidence.get(0).toString().equals(start.asText())));
        require(end.isNull() || (!start.isNull() && evidence.size() == 2 && evidence.get(1).toString().equals(end.asText())));
        require(!fact.path("isCurrent").asBoolean() || (!start.isNull() && PRESENT.matcher(excerpt).find()));
    }

    private static List<YearMonth> months(String text) {
        String dates = text;
        for (int index = 0; index < MONTHS.length; index++) {
            dates = dates.replaceAll("(?iu)\\b(?:" + MONTHS[index].replace(' ', '|')
                    + ")\\.?\\s+((?:19|20)\\d{2})\\b", "$1-" + (index + 1));
        }
        List<YearMonth> months = new ArrayList<>();
        Matcher matcher = DATE.matcher(dates);
        while (matcher.find()) {
            String[] parts = matcher.group().split("[-/. ]");
            int year = parts[0].length() == 4 ? 0 : 1;
            months.add(YearMonth.of(Integer.parseInt(parts[year]), Integer.parseInt(parts[1 - year])));
        }
        return months;
    }

    private static Map<String, Set<String>> languageLevels(String text) {
        Map<String, Set<String>> found = new HashMap<>();
        Matcher marker = LANGUAGE_MARKER.matcher(text);
        String language = null;
        int previousEnd = 0;
        while (marker.find()) {
            String value = marker.group().toUpperCase(Locale.ROOT);
            if (value.matches("[ABC][12]")) {
                if (language != null && SEPARATOR.matcher(text.substring(previousEnd, marker.start())).replaceAll("").isEmpty()) {
                    found.computeIfAbsent(language, ignored -> new HashSet<>()).add(value);
                }
            } else {
                language = switch (value) {
                    case "ENGLISH", "ANGLAIS" -> "EN";
                    case "FRENCH", "FRANÇAIS" -> "FR";
                    case "GERMAN", "ALLEMAND" -> "DE";
                    case "SPANISH", "ESPAGNOL" -> "ES";
                    case "CHINESE", "CHINOIS" -> "ZH";
                    default -> value;
                };
            }
            previousEnd = marker.end();
        }
        return found;
    }

    private static String normalizeWord(String word) {
        return java.text.Normalizer.normalize(word.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String flatten(String text) { return text.replaceAll("(?U)\\s+", " ").strip(); }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("A fact or its evidence failed source validation.");
    }
}
