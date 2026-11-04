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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Conservative rules shared by upload processing and research dataset preparation. */
@Component
public final class LocalResumeCleaner implements ResumeCleaningGateway {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT = 500_000;


    @Override
    public ObjectNode clean(String filename, byte[] content) throws IOException {
        if (filename == null || content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("A non-empty resume up to 10 MiB is required.");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", "resume-draft-v1");
        result.put("cleanerVersion", "local-rules-v2");
        result.put("status", "REVIEW_REQUIRED");
        ObjectNode professional = result.putObject("professional");
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            professional.putArray(group);
        }
        ArrayNode warnings = result.putArray("warnings");
        String name = filename.toLowerCase(Locale.ROOT);
        ProfessionalFactExtractor extractor = new ProfessionalFactExtractor();
        if (name.endsWith(".pdf")) {
            try (var pdf = Loader.loadPDF(content)) {
                if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 20) {
                    throw new IllegalArgumentException("PDF must be unencrypted and at most 20 pages.");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                int total = 0;
                long started = System.nanoTime();
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(120)) throw new IOException("Extraction timed out or was cancelled.");
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(pdf);
                    total += text.length();
                    if (total > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                    if (text.replaceAll("[^\\p{L}\\p{N}]", "").length() < 20) {
                        var box = pdf.getPage(page - 1).getCropBox();
                        float dpi = (float) Math.min(200, 72 * Math.sqrt(8_000_000.0 / Math.max(1, box.getWidth() * box.getHeight())));
                        var image = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(page - 1, dpi);
                        long remaining = 120_000 - java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                        text = new TesseractOcr().read(image, remaining);
                        total += text.length();
                        warnings.add("Local OCR used on page " + page + "; verify the evidence.");
                    }
                    if (total > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                    extractor.extract(professional, text, page);
                }
                if (total < 20) throw new IllegalArgumentException("PDF has no usable text after extraction. Use a readable scan or manual entry.");
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
                if (text.length() > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                extractor.extract(professional, text.toString(), null);
            }
        } else if (name.endsWith(".txt")) {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
            extractor.extract(professional, text, null);
        } else {
            throw new IllegalArgumentException("Supported formats: PDF, DOCX and UTF-8 TXT.");
        }
        warnings.add("Conservative English and French rules. Unknown dates, subjects and language levels require manual review.");
        warnings.add("Draft codes are provisional. Do not use these drafts as confirmed training labels.");
        return result;
    }

}
