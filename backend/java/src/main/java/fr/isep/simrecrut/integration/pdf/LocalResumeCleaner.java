package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.gateway.extraction.ResumeCleaningGateway;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
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
        return clean(filename, content, false);
    }

    /** Candidate review also needs the personal header and catch-all sections; research datasets do not. */
    public ObjectNode cleanWithProfile(String filename, byte[] content) throws IOException {
        return clean(filename, content, true);
    }

    /** Professional rules plus local personal and other-information extraction for already-read pages. */
    public ObjectNode cleanPages(List<PdfPage> pages, byte[] pdf) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", "resume-draft-v1");
        result.put("cleanerVersion", "local-rules-v2");
        result.put("status", "REVIEW_REQUIRED");
        ObjectNode professional = result.putObject("professional");
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            professional.putArray(group);
        }
        ArrayNode warnings = result.putArray("warnings");
        ProfessionalFactExtractor extractor = new ProfessionalFactExtractor();
        for (PdfPage page : pages) {
            if (page.ocr()) warnings.add("Local OCR used on page " + page.number() + "; verify the evidence.");
            extractor.extract(professional, page.text(), page.number());
        }
        professional.setAll(new CvProfileExtractor().extract(pages, pdf));
        warnings.add("Conservative English and French rules. Unknown dates, subjects and language levels require manual review.");
        warnings.add("Draft codes are provisional. Do not use these drafts as confirmed training labels.");
        return result;
    }

    private ObjectNode clean(String filename, byte[] content, boolean profile) throws IOException {
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
            List<PdfPage> pages = readPdf(content);
            for (PdfPage page : pages) {
                if (page.ocr()) warnings.add("Local OCR used on page " + page.number() + "; verify the evidence.");
                extractor.extract(professional, page.text(), page.number());
            }
            if (profile) professional.setAll(new CvProfileExtractor().extract(pages, content));
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
                if (profile) professional.setAll(new CvProfileExtractor().extract(text.toString()));
            }
        } else if (name.endsWith(".txt")) {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
            extractor.extract(professional, text, null);
            if (profile) professional.setAll(new CvProfileExtractor().extract(text));
        } else {
            throw new IllegalArgumentException("Supported formats: PDF, DOCX and UTF-8 TXT.");
        }
        warnings.add("Conservative English and French rules. Unknown dates, subjects and language levels require manual review.");
        warnings.add("Draft codes are provisional. Do not use these drafts as confirmed training labels.");
        return result;
    }

    public List<PdfPage> readPdf(byte[] content) throws IOException {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("A non-empty resume up to 10 MiB is required.");
        }
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 20) {
                throw new IllegalArgumentException("PDF must be unencrypted and at most 20 pages.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<PdfPage> pages = new ArrayList<>();
            int total = 0;
            long started = System.nanoTime();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                long remaining = 120_000 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                if (Thread.currentThread().isInterrupted() || remaining <= 0) {
                    throw new IOException("Extraction timed out or was cancelled.");
                }
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                if (total + text.length() > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                boolean ocr = text.replaceAll("[^\\p{L}\\p{N}]", "").length() < 20;
                if (ocr) {
                    var box = pdf.getPage(page - 1).getCropBox();
                    float dpi = (float) Math.min(200, 72 * Math.sqrt(8_000_000.0 / Math.max(1, box.getWidth() * box.getHeight())));
                    var image = new PDFRenderer(pdf).renderImageWithDPI(page - 1, dpi);
                    remaining = 120_000 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    text = new TesseractOcr().read(image, remaining);
                }
                total += text.length();
                if (total > MAX_TEXT) throw new IllegalArgumentException("Extracted text is too large.");
                pages.add(new PdfPage(page, text, ocr));
            }
            if (total < 20) throw new IllegalArgumentException("PDF has no usable text after extraction. Use a readable scan or manual entry.");
            return pages;
        }
    }

    public record PdfPage(int number, String text, boolean ocr) { }
}
