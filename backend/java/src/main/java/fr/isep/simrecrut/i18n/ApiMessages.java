package fr.isep.simrecrut.i18n;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.springframework.context.i18n.LocaleContextHolder;

/** Presentation-only messages: never translate persisted business data or codes. */
public final class ApiMessages {
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private ApiMessages() { }

    public static Locale resolveLocale(String header) {
        if (header == null || header.isBlank() || header.length() > 1024) return Locale.ENGLISH;
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                if (range.getWeight() == 0) continue;
                String language = Locale.forLanguageTag(range.getRange()).getLanguage();
                if (language.equals("zh")) return Locale.SIMPLIFIED_CHINESE;
                if (language.equals("fr")) return Locale.FRENCH;
                if (language.equals("en") || range.getRange().equals("*")) return Locale.ENGLISH;
            }
        } catch (IllegalArgumentException ignored) { /* A malformed preference is not a failed request. */ }
        return Locale.ENGLISH;
    }

    public static Locale currentLocale() {
        return LocaleContextHolder.getLocaleContext() == null ? Locale.ENGLISH
                : resolveLocale(LocaleContextHolder.getLocale().toLanguageTag());
    }

    public static String translate(String source, Locale locale) {
        if (source == null || source.isBlank() || locale.getLanguage().equals("en")) return source;
        ResourceBundle bundle = bundle(locale);
        String key = keyFor(source);
        if (bundle.containsKey(key)) return bundle.getString(key);
        // This legacy validation message contains a stable criterion code.
        for (String criterion : new String[]{"SKILLS", "EXPERIENCE", "EDUCATION", "LANGUAGES", "PROJECTS"}) {
            if (source.equals("Add an assessed requirement for " + criterion + ".")) {
                return format("validation.missingArea", locale,
                        Map.of("category", text("criterion." + criterion, locale)));
            }
            if (source.equals(criterion + ": confirm the facts or explicitly confirm their absence.")) {
                return format("validation.confirmFacts", locale,
                        Map.of("category", text("criterion." + criterion, locale)));
            }
        }
        for (String field : new String[]{"skillCodes", "roleFamilyCodes", "degreeLevelCodes", "subjectCodes",
                "languageCodes", "languageLevelCodes", "conditionCodes", "degreeOrder", "languageLevelOrder"}) {
            if (source.equals("Resolve unsupported " + field + " values.")) {
                return format("validation.unsupportedValues", locale, Map.of("field", text("field." + field, locale)));
            }
        }
        return source;
    }

    public static String text(String key, Locale locale) { return bundle(locale).getString(key); }
    public static String format(String key, Locale locale, Map<String, String> parameters) {
        String value = text(key, locale);
        // One pass: inserted values are never interpreted as another template.
        var matcher = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result,
                java.util.regex.Matcher.quoteReplacement(parameters.getOrDefault(matcher.group(1), matcher.group())));
        matcher.appendTail(result);
        return result.toString();
    }

    public static String keyFor(String source) {
        try {
            return "api." + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ResourceBundle bundle(Locale locale) {
        try { return ResourceBundle.getBundle("i18n.messages", locale, CONTROL); }
        catch (MissingResourceException exception) { throw new IllegalStateException("API message catalogue is unavailable", exception); }
    }
}
