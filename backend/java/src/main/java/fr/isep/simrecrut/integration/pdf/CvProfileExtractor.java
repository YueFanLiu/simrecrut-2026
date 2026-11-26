package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Local, rule-based extraction of the personal header and catch-all CV sections. It runs on the
 * server only: these details are never sent to a remote extraction provider. Every result is a draft
 * that the candidate reviews and can correct before confirmation.
 */
public final class CvProfileExtractor {
    private static final int MAX_ITEMS = 100;
    private static final int MAX_PER_CATEGORY = 30;
    private static final int PHOTO_SIZE = 240;
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w+])(\\+?\\(?\\d[\\d ().-]{7,}\\d)(?!\\w)");
    private static final Pattern YEAR_RANGE = Pattern.compile("(?:19|20)\\d{2}\\s*[-–/]\\s*(?:(?:19|20)\\d{2}|\\d{2})");
    private static final Pattern LINKEDIN = Pattern.compile("(?i)(?:https?://)?(?:[a-z]{2,3}\\.)?linkedin\\.com/[^\\s,;|]+");
    private static final Pattern GITHUB = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?github\\.com/[^\\s,;|]+");
    private static final Pattern WEBSITE = Pattern.compile("(?i)https?://[^\\s,;|]+|www\\.[^\\s,;|]+");
    private static final Pattern POSTAL = Pattern.compile("\\b\\d{4,5}\\s+(\\p{Lu}[\\p{L}'’ -]{1,40})");
    private static final Pattern STREET = Pattern.compile(
            "(?iu)\\b(?:\\d{1,4}\\s*(?:bis|ter)?,?\\s+)?(?:rue|avenue|av\\.|boulevard|bd|allée|chemin|impasse|place|"
                    + "quai|route|street|st\\.|road|rd\\.|lane|drive)\\b");
    private static final Pattern SEGMENT = Pattern.compile("\\s*(?:\\||•|·|▪|◦|\\s{3,}|\\t)\\s*");
    private static final Pattern BULLET = Pattern.compile("^[\\s•▪◦·*–—-]+");
    private static final Pattern NAME = Pattern.compile("^\\p{Lu}[\\p{L}\\p{M}'’-]+(?:\\s+\\p{L}[\\p{L}\\p{M}'’.-]*){1,3}$");
    private static final Pattern NOT_A_NAME = Pattern.compile("(?iu)\\b(?:curriculum|vitae|resume|résumé|cv|profil|profile|"
            + "contact|engineer|ingénieur|developer|développeur|student|étudiant|stage|internship|intern|manager|"
            + "analyst|analyste|designer|consultant|master|bachelor|licence|alternance|chef|assistant)\\b");
    private static final Map<String, Pattern> LABELS = new LinkedHashMap<>();
    private static final Map<String, Pattern> SECTIONS = new LinkedHashMap<>();
    private static final Pattern OTHER_HEADING = Pattern.compile("(?iu)^(?:(?:technical |core |key )?skills|compétences"
            + "(?: techniques)?|hard skills|soft skills|(?:work |professional |relevant )?experiences?|expériences?"
            + "(?: professionnelles?)?|employment(?: history)?|education(?: and training)?|formations?|diplômes|"
            + "qualifications|(?:personal |academic |selected )?projects?|projets?(?: personnels| académiques)?|"
            + "languages?|langues|profile|profil|summary|résumé|about me|à propos|contact|coordonnées|"
            + "personal (?:details|information)|informations personnelles|technologies|tools|outils)$");
    private static final List<String> COUNTRIES = List.of("France", "China", "Chine", "Germany", "Allemagne", "Spain",
            "Espagne", "Italy", "Italie", "United Kingdom", "Royaume-Uni", "UK", "USA", "United States", "États-Unis",
            "Belgium", "Belgique", "Switzerland", "Suisse", "Canada", "Morocco", "Maroc", "Tunisia", "Tunisie",
            "Algeria", "Algérie", "Senegal", "Sénégal", "India", "Inde", "Vietnam", "Japan", "Japon", "Portugal",
            "Netherlands", "Pays-Bas", "Luxembourg", "Ireland", "Irlande", "Brazil", "Brésil", "Lebanon", "Liban",
            "Cameroon", "Cameroun", "Côte d'Ivoire", "Ivory Coast", "Korea", "Corée", "Singapore", "Singapour");

    static {
        LABELS.put("FULL_NAME", label("full name|name|nom complet|nom et prénom|nom"));
        LABELS.put("FIRST_NAME", label("first name|given name|prénom"));
        LABELS.put("LAST_NAME", label("last name|surname|family name|nom de famille"));
        LABELS.put("GENDER", label("gender|sex|sexe|genre"));
        LABELS.put("NATIONALITY", label("nationality|nationalité|citizenship"));
        LABELS.put("DATE_OF_BIRTH", label("date of birth|birth date|birthday|born|date de naissance|née? le"));
        LABELS.put("ADDRESS", label("address|adresse|home address"));
        LABELS.put("CITY", label("city|ville|location|localisation"));
        LABELS.put("COUNTRY", label("country|pays"));
        LABELS.put("PHONE", label("phone|mobile|tel\\.?|tél\\.?|téléphone|portable"));
        LABELS.put("EMAIL", label("e-?mail|courriel|mail"));
        SECTIONS.put("INTEREST", section("interests?|hobbies|hobbies (?:and|&) interests|centres? d['’]intérêts?|"
                + "intérêts|loisirs|passions"));
        SECTIONS.put("VOLUNTEERING", section("volunteer(?:ing| work| experience)?|bénévolat|engagements?(?: associatifs?)?|"
                + "vie associative|associations?|associative (?:experience|life)|community (?:service|involvement)"));
        SECTIONS.put("CERTIFICATION", section("certifications?|certificates?|licen[cs]es?(?: (?:and|&) certifications?)?"));
        SECTIONS.put("AWARD", section("awards?|honou?rs(?: (?:and|&) awards)?|prix|distinctions?|achievements?|réalisations"));
        SECTIONS.put("ACTIVITY", section("activities|activités|extra-?curricular(?: activities)?|activités extra-?scolaires"));
        SECTIONS.put("PUBLICATION", section("publications?"));
        SECTIONS.put("REFERENCE", section("references?|références"));
        SECTIONS.put("OTHER", section("other|others|autres?|additional information|informations complémentaires|divers|misc"));
    }

    private static Pattern label(String names) {
        return Pattern.compile("(?iu)^(?:" + names + ")\\s*[:：]\\s*(.+)$");
    }

    private static Pattern section(String names) {
        return Pattern.compile("(?iu)^(?:" + names + ")\\s*[:：]?\\s*(.*)$");
    }

    public record Line(int page, String text) { }

    /** Extract from PDF pages; the photo is read from embedded images on the first two pages. */
    public ObjectNode extract(List<LocalResumeCleaner.PdfPage> pages, byte[] pdf) {
        List<Line> lines = new ArrayList<>();
        for (var page : pages) for (String raw : page.text().split("\\R")) lines.add(new Line(page.number(), raw));
        ObjectNode result = extract(lines);
        ObjectNode photo = JsonNodeFactory.instance.objectNode().put("fieldCode", "PHOTO");
        String image = pdf == null ? null : photo(pdf);
        photo.put("value", image == null ? "NO" : "YES");
        if (image != null) photo.put("imageBase64", image);
        photo.set("evidence", evidence(image == null ? "No photo found in the PDF" : "Photo embedded in the PDF",
                image == null ? null : 1));
        ArrayNode personal = (ArrayNode) result.get("personal");
        personal.insert(Math.min(personal.size(), 3), photo);
        return result;
    }

    /** Extract from plain text without page numbers (DOCX or TXT). */
    public ObjectNode extract(String text) {
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) lines.add(new Line(0, raw));
        return extract(lines);
    }

    ObjectNode extract(List<Line> lines) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode personal = result.putArray("personal");
        ArrayNode other = result.putArray("other");
        Map<String, ObjectNode> found = new LinkedHashMap<>();
        List<Line> segments = new ArrayList<>();
        for (Line line : lines) {
            for (String part : SEGMENT.split(line.text().strip())) {
                String value = part.strip();
                if (!value.isEmpty()) segments.add(new Line(line.page(), value));
            }
        }
        for (Line segment : segments) {
            for (var label : LABELS.entrySet()) {
                Matcher matcher = label.getValue().matcher(segment.text());
                if (matcher.find() && !found.containsKey(label.getKey())) {
                    String value = matcher.group(1).strip();
                    if (label.getKey().equals("EMAIL") && !EMAIL.matcher(value).find()) continue;
                    if (label.getKey().equals("PHONE") && !phone(value)) continue;
                    if (!value.isEmpty() && value.length() <= 200) put(found, label.getKey(), value, segment);
                }
            }
        }
        for (Line segment : segments) {
            String text = segment.text();
            Matcher email = EMAIL.matcher(text);
            if (email.find()) put(found, "EMAIL", email.group(), segment);
            Matcher linkedin = LINKEDIN.matcher(text);
            if (linkedin.find()) put(found, "LINKEDIN", linkedin.group(), segment);
            Matcher github = GITHUB.matcher(text);
            if (github.find()) put(found, "GITHUB", github.group(), segment);
            Matcher website = WEBSITE.matcher(text);
            while (website.find()) {
                String value = website.group();
                String lower = value.toLowerCase(Locale.ROOT);
                if (!lower.contains("linkedin.com") && !lower.contains("github.com") && !value.contains("@")) {
                    put(found, "WEBSITE", value, segment);
                    break;
                }
            }
            Matcher phone = PHONE.matcher(EMAIL.matcher(text).replaceAll(" "));
            while (phone.find()) {
                if (phone(phone.group(1))) { put(found, "PHONE", phone.group(1).strip(), segment); break; }
            }
        }
        List<Line> header = new ArrayList<>();
        for (Line segment : segments) {
            if (segment.page() > 1) break;
            if (SECTIONS.values().stream().anyMatch(p -> p.matcher(segment.text()).matches() && heading(segment.text()))
                    || OTHER_HEADING.matcher(trimHeading(segment.text())).matches()) {
                if (header.size() >= 3) break;
                continue;
            }
            header.add(segment);
            if (header.size() >= 15) break;
        }
        if (!found.containsKey("FULL_NAME")) {
            for (Line segment : header.subList(0, Math.min(8, header.size()))) {
                String text = segment.text().strip();
                if (text.length() >= 3 && text.length() <= 60 && NAME.matcher(text).matches()
                        && !NOT_A_NAME.matcher(text).find() && !OTHER_HEADING.matcher(trimHeading(text)).matches()) {
                    put(found, "FULL_NAME", text, segment);
                    break;
                }
            }
        }
        ObjectNode full = found.get("FULL_NAME");
        if (full != null && (!found.containsKey("FIRST_NAME") || !found.containsKey("LAST_NAME"))) {
            String[] names = splitName(full.get("value").asText());
            Line source = new Line(full.path("evidence").path("page").asInt(0), full.get("value").asText());
            if (names != null && !found.containsKey("FIRST_NAME")) put(found, "FIRST_NAME", names[0], source);
            if (names != null && !found.containsKey("LAST_NAME")) put(found, "LAST_NAME", names[1], source);
        }
        for (Line segment : header) {
            String text = segment.text();
            if (found.containsKey("ADDRESS")) break;
            if (EMAIL.matcher(text).find() || WEBSITE.matcher(text).find()) continue;
            Matcher postal = POSTAL.matcher(text);
            if (postal.find() || STREET.matcher(text).find()) {
                if (text.length() <= 200) put(found, "ADDRESS", text, segment);
                if (postal.find(0) && !found.containsKey("CITY")) {
                    String city = postal.group(1).split(",")[0].strip();
                    if (!city.isEmpty()) put(found, "CITY", city, segment);
                }
            }
        }
        if (!found.containsKey("COUNTRY")) {
            outer:
            for (Line segment : header) {
                for (String country : COUNTRIES) {
                    if (Pattern.compile("(?iu)(?<![\\p{L}])" + Pattern.quote(country) + "(?![\\p{L}])")
                            .matcher(segment.text()).find()
                            && (segment.text().length() <= 80 || segment.text().contains(","))) {
                        put(found, "COUNTRY", country, segment);
                        break outer;
                    }
                }
            }
        }
        for (String field : List.of("FULL_NAME", "FIRST_NAME", "LAST_NAME", "GENDER", "EMAIL", "PHONE", "ADDRESS",
                "CITY", "COUNTRY", "LINKEDIN", "GITHUB", "WEBSITE", "NATIONALITY", "DATE_OF_BIRTH")) {
            if (found.containsKey(field)) personal.add(found.get(field));
        }
        sections(lines, other);
        return result;
    }

    private void sections(List<Line> lines, ArrayNode other) {
        String current = null;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Line line : lines) {
            String text = BULLET.matcher(line.text()).replaceFirst("").strip();
            if (text.isEmpty()) continue;
            String heading = trimHeading(text);
            String matched = null;
            String inline = "";
            for (var section : SECTIONS.entrySet()) {
                Matcher matcher = section.getValue().matcher(text);
                // "Interests" or "Interests: chess, running" start a section; "Interests in cloud computing" does not.
                if (matcher.matches() && (matcher.group(1).isBlank() ? heading(text)
                        : text.matches("(?s)[^:：]{1,40}[:：].+"))) {
                    matched = section.getKey();
                    inline = matcher.group(1).strip();
                    break;
                }
            }
            if (matched != null) {
                current = matched;
                for (String value : inline.split("\\s*[;,•]\\s*")) add(other, counts, seen, current, value, line);
                continue;
            }
            if (OTHER_HEADING.matcher(heading).matches()) { current = null; continue; }
            if (current != null) add(other, counts, seen, current, text, line);
        }
    }

    private static boolean heading(String text) {
        String value = trimHeading(text);
        return value.length() <= 50 && value.split("\\s+").length <= 6 && !value.matches(".*[.;].*\\S.*");
    }

    private static String trimHeading(String text) {
        return text.strip().replaceAll("[:：]\\s*$", "").strip();
    }

    private static void add(ArrayNode other, Map<String, Integer> counts, Set<String> seen, String category,
            String value, Line line) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || other.size() >= MAX_ITEMS || counts.getOrDefault(category, 0) >= MAX_PER_CATEGORY) return;
        if (text.length() > 2000) text = text.substring(0, 2000);
        if (!seen.add(category + "\n" + text.toLowerCase(Locale.ROOT))) return;
        counts.merge(category, 1, Integer::sum);
        ObjectNode item = other.addObject().put("categoryCode", category).put("value", text);
        item.set("evidence", evidence(text, line.page() > 0 ? line.page() : null));
    }

    private static void put(Map<String, ObjectNode> found, String field, String value, Line source) {
        if (found.containsKey(field)) return;
        String text = value.strip();
        if (text.length() > 2000) text = text.substring(0, 2000);
        ObjectNode item = JsonNodeFactory.instance.objectNode().put("fieldCode", field).put("value", text);
        item.set("evidence", evidence(source.text(), source.page() > 0 ? source.page() : null));
        found.put(field, item);
    }

    private static ObjectNode evidence(String text, Integer page) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        String excerpt = text == null ? "" : text.strip();
        evidence.put("text", excerpt.length() > 300 ? excerpt.substring(0, 300) : excerpt);
        if (page == null || page < 1 || page > 20) evidence.putNull("page");
        else evidence.put("page", page);
        evidence.put("source", "EXTRACTED");
        return evidence;
    }

    private static boolean phone(String value) {
        String digits = value.replaceAll("\\D", "");
        return digits.length() >= 9 && digits.length() <= 15 && !YEAR_RANGE.matcher(value).find();
    }

    /** French CVs often write the family name in capitals: "Marie DUPONT". */
    static String[] splitName(String name) {
        String[] tokens = name.strip().split("\\s+");
        if (tokens.length < 2) return null;
        List<String> first = new ArrayList<>(), last = new ArrayList<>();
        for (String token : tokens) {
            boolean capitals = token.length() > 1 && token.equals(token.toUpperCase(Locale.ROOT))
                    && !token.equals(token.toLowerCase(Locale.ROOT));
            (capitals ? last : first).add(token);
        }
        if (first.isEmpty() || last.isEmpty()) {
            return new String[] {tokens[0], String.join(" ", List.of(tokens).subList(1, tokens.length))};
        }
        return new String[] {String.join(" ", first), String.join(" ", last)};
    }

    /** Choose the largest roughly portrait-shaped embedded image on the first two pages. */
    static String photo(byte[] pdf) {
        try (var document = Loader.loadPDF(pdf)) {
            BufferedImage best = null;
            long bestArea = 0;
            for (int index = 0; index < Math.min(2, document.getNumberOfPages()); index++) {
                PDPage page = document.getPage(index);
                PDResources resources = page.getResources();
                if (resources == null) continue;
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject object = resources.getXObject(name);
                    if (!(object instanceof PDImageXObject image)) continue;
                    int width = image.getWidth(), height = image.getHeight();
                    double ratio = height / (double) Math.max(1, width);
                    long area = (long) width * height;
                    if (width < 60 || height < 60 || ratio < 0.7 || ratio > 1.5 || area > 25_000_000L) continue;
                    if (image.isStencil()) continue;
                    if (area > bestArea) {
                        BufferedImage decoded = image.getImage();
                        if (decoded != null) { best = decoded; bestArea = area; }
                    }
                }
            }
            if (best == null) return null;
            double scale = Math.min(1.0, PHOTO_SIZE / (double) Math.max(best.getWidth(), best.getHeight()));
            int width = Math.max(1, (int) Math.round(best.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(best.getHeight() * scale));
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(best, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "jpeg", bytes)) return null;
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception | LinkageError error) {
            return null; // A missing photo is a reviewable draft value, never an extraction failure.
        }
    }
}
