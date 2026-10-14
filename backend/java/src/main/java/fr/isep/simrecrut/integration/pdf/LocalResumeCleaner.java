package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.gateway.extraction.ResumeCleaningGateway;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Conservative rules shared by upload processing and research dataset preparation. */
@Component
public final class LocalResumeCleaner implements ResumeCleaningGateway {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT = 500_000;
    private static final List<String> SKILLS = List.of(
        "Java", "JavaScript", "TypeScript", "Spring Boot", "MySQL", "PostgreSQL",
        "SQL", "Python", "Git", "Docker", "HTML", "CSS", "Vue", "React", "Linux");

    @Override
    public ObjectNode clean(String filename, byte[] content) throws IOException {
        if (filename == null || content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("A non-empty resume up to 10 MiB is required.");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", "resume-draft-v1");
        result.put("cleanerVersion", "local-rules-v1");
        result.put("status", "REVIEW_REQUIRED");
        ObjectNode professional = result.putObject("professional");
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            professional.putArray(group);
        }
        ArrayNode warnings = result.putArray("warnings");
        String name = filename.toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        if (name.endsWith(".pdf")) {
            try (var pdf = Loader.loadPDF(content)) {
                if (pdf.isEncrypted() || pdf.getNumberOfPages() > 20) {
                    throw new IllegalArgumentException("PDF must be unencrypted and at most 20 pages.");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                int total = 0;
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(pdf);
                    total += text.length();
                    if (total > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                    extract(professional, text, page, seen);
                }
                if (total < 20) throw new IllegalArgumentException("PDF has no usable text; OCR is required.");
            }
        } else if (name.endsWith(".docx")) {
            try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
                StringBuilder text = new StringBuilder();
                for (var paragraph : document.getParagraphs()) text.append(paragraph.getText()).append('\n');
                for (var table : document.getTables()) {
                    for (var row : table.getRows()) {
                        for (var cell : row.getTableCells()) text.append(cell.getText()).append('\n');
                    }
                }
                extract(professional, text.toString(), null, seen);
            }
        } else if (name.endsWith(".txt")) {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            extract(professional, text, null, seen);
        } else {
            throw new IllegalArgumentException("Supported formats: PDF, DOCX and UTF-8 TXT.");
        }
        warnings.add("Finite skill dictionary only. Other professional groups require manual review.");
        warnings.add("Draft codes are provisional. Do not use these drafts as confirmed training labels.");
        return result;
    }

    private void extract(ObjectNode professional, String text, Integer page, Set<String> seen) {
        if (text.length() > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
        for (String line : text.split("\\R")) {
            // Evidence contains only the matched skill, never arbitrary CV lines or personal identifiers.
            for (String skill : SKILLS) {
                var pattern = java.util.regex.Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(skill)
                        + "(?![\\p{L}\\p{N}])", java.util.regex.Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(line).find() && seen.add(skill)) {
                    ObjectNode fact = ((ArrayNode) professional.get("skills")).addObject();
                    fact.put("skillCode", skill);
                    ObjectNode evidence = fact.putObject("evidence");
                    evidence.put("text", skill);
                    if (page == null) evidence.putNull("page"); else evidence.put("page", page);
                    evidence.put("source", "EXTRACTED");
                }
            }
        }
    }
}
