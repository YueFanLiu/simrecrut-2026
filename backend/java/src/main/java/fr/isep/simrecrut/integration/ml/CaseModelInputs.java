package fr.isep.simrecrut.integration.ml;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.ModelInputRequest;
import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper;
import fr.isep.simrecrut.service.assessment.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Keep optional model media separate from the original PDF and public result DTOs. */
@Component
@Profile("!resume-workspace")
public class CaseModelInputs {
    private final ObjectMapper json;
    private final CandidateCaseMapper db;
    private final Path root;
    private final String base, token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public CaseModelInputs(ObjectMapper json, CandidateCaseMapper db,
            @Value("${simrecrut.storage.model-inputs:runtime/model-inputs}") String directory,
            @Value("${simrecrut.ml.base-url:}") String base,
            @Value("${simrecrut.ml.internal-token:}") String token) {
        this.json = json; this.db = db; this.root = Path.of(directory).toAbsolutePath().normalize();
        this.base = base; this.token = token;
    }

    /** Render only two bounded pages outside a transaction; detection failure permits manual upload. */
    public ObjectNode prepare(byte[] pdf, JsonNode professional) throws IOException {
        ObjectNode value = json.createObjectNode().put("biography", biography(professional)).put("name", "")
                .put("useText", false).put("usePortrait", false).put("portraitIndex", -1);
        value.putArray("candidates");
        try (var document = Loader.loadPDF(pdf)) {
            ArrayNode pages = json.createArrayNode();
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < Math.min(2, document.getNumberOfPages()); page++) {
                var box = document.getPage(page).getCropBox();
                float dpi = (float) Math.min(120, 72 * Math.sqrt(2_000_000.0
                        / Math.max(1, box.getWidth() * box.getHeight())));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(renderer.renderImageWithDPI(page, dpi), "png", out);
                pages.add(Base64.getEncoder().encodeToString(out.toByteArray()));
            }
            if (!base.isBlank() && !token.isBlank()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "")
                        + "/internal/media/portraits")).timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.createObjectNode().set("pages", pages).toString())).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    byte[] bytes = body.readNBytes(2_000_001);
                    if (response.statusCode() == 200 && bytes.length <= 2_000_000) {
                        JsonNode candidates = json.readTree(bytes).path("candidates");
                        if (candidates.isArray() && candidates.size() <= 4) {
                            for (JsonNode candidate : candidates) crop(candidate.asText());
                            value.set("candidates", candidates);
                        }
                    }
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Portrait detection interrupted.");
        } catch (Exception error) {
            value.put("portraitNotice", "Automatic portrait detection is unavailable. Select a local portrait or omit it.");
        }
        return value;
    }

    /** Persist immutable payloads before committing their digest pointer; rollback leaves no readable revision. */
    public void prepared(AssessmentCase row, ObjectNode value) {
        store(row, value, false);
    }

    public ObjectNode preview(AssessmentCase row) {
        ObjectNode value = read(row, false);
        if (value == null) throw CaseException.dependency();
        return value;
    }

    public void refresh(AssessmentCase row, JsonNode professional) {
        ObjectNode value = preview(row); value.put("biography", biography(professional));
        store(row, value, false);
    }

    public void select(AssessmentCase row, ModelInputRequest body) {
        ObjectNode value = preview(row).deepCopy();
        String text = Objects.requireNonNullElse(body.biography(), "").strip();
        String name = Objects.requireNonNullElse(body.name(), "").strip();
        String withoutDates = text.replaceAll("\\b\\d{4}-\\d{2}(?:-\\d{2})?\\b", "");
        if (withoutDates.matches("(?s).*([\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}|https?://|\\d[\\d ()+.-]{7,}\\d).*")) {
            throw new IllegalArgumentException("Remove email addresses, links and phone numbers from model text.");
        }
        if (name.matches("(?s).*[\\r\\n@:/\\\\].*") || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter a name without contact details.");
        }
        if (name.codePoints().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("A name cannot contain a phone number.");
        }
        JsonNode previousPortrait = value.get("portrait");
        int selected = body.portraitIndex() == null ? -1 : body.portraitIndex();
        value.remove("portrait");
        if (body.usePortrait()) {
            String encoded = body.portraitBase64();
            if (encoded == null || encoded.isBlank()) {
                if (selected == -1 && previousPortrait != null) encoded = previousPortrait.path("dataBase64").asText();
                else if (selected >= 0 && selected < value.path("candidates").size()) {
                    encoded = value.path("candidates").get(selected).asText();
                } else throw new IllegalArgumentException("Choose a portrait or omit it.");
            }
            value.set("portrait", json.createObjectNode().put("mimeType", "image/jpeg").put("dataBase64", crop(encoded)));
        }
        value.put("biography", text).put("name", name).put("useText", body.useText())
                .put("usePortrait", body.usePortrait()).put("portraitIndex", selected);
        store(row, value, true);
    }

    public void confirm(AssessmentCase row) {
        var state = db.modelInput(row.id);
        if (state == null || !state.reviewed()) {
            throw new IllegalArgumentException("Save the optional model inputs before confirmation.");
        }
        ObjectNode value = preview(row); value.remove(List.of("candidates", "portraitNotice"));
        if (!value.path("useText").asBoolean()) value.remove(List.of("name", "biography"));
        if (!value.path("usePortrait").asBoolean()) value.remove("portrait");
        store(row, value, true);
        if (db.confirmModelInput(row.id) != 1) {
            throw new IllegalArgumentException("Review and save the optional model inputs, including any omissions.");
        }
    }

    /** Read the exact confirmed digest; a retry cannot silently replace missing media with zero inputs. */
    public ObjectNode forPrediction(AssessmentCase row) {
        ObjectNode value = read(row, true), result = json.createObjectNode();
        if (value == null) throw CaseException.dependency();
        if (value.path("useText").asBoolean()) {
            String name = value.path("name").asText("");
            result.put("biography", (name.isBlank() ? "" : "Name: " + name + ". ") + value.path("biography").asText(""));
        }
        if (value.path("usePortrait").asBoolean()) result.set("portrait", value.path("portrait"));
        return result;
    }

    public void prune(AssessmentCase row) {
        var input = db.modelInput(row.id);
        if (input == null || !input.confirmed() || input.purged()) return;
        Path directory = directory(row.publicId);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                safe(path);
                if (!path.getFileName().toString().equals(input.payloadSha256() + ".json")) Files.delete(path);
            }
        } catch (IOException error) { throw CaseException.dependency(); }
    }

    public void purge(String caseId) throws IOException {
        Path directory = directory(caseId);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        safe(directory);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                safe(path); Files.deleteIfExists(path);
            }
        }
    }

    public static String biography(JsonNode professional) {
        Set<String> lines = new LinkedHashSet<>();
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            for (JsonNode fact : professional.path(group)) {
                String excerpt = fact.path("evidence").path("text").asText("").strip();
                if (!excerpt.isBlank()) lines.add(excerpt.replaceAll("[\\r\\n]+", " "));
            }
        }
        return String.join(". ", lines).replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "")
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\+\\d[\\d ().-]{7,}\\d|(?<![\\d-])\\d{10,}(?![\\d-])", "");
    }

    private void store(AssessmentCase row, ObjectNode value, boolean reviewed) {
        try {
            byte[] bytes = json.writeValueAsBytes(value);
            if (bytes.length > 4_000_000) throw new IllegalArgumentException("Model input exceeds its size limit.");
            String hash = CandidateCaseService.hash(bytes);
            Path directory = directory(row.publicId);
            Files.createDirectories(directory); safe(directory); restrict(directory, true);
            Path file = directory.resolve(hash + ".json");
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                Files.write(file, bytes, StandardOpenOption.CREATE_NEW); restrict(file, false);
            }
            safe(file);
            db.saveModelInput(row.id, hash, value.path("useText").asBoolean(),
                    value.path("usePortrait").asBoolean(), reviewed ? "REVIEWED" : "DRAFT");
        } catch (IOException error) { throw CaseException.dependency(); }
    }

    private ObjectNode read(AssessmentCase row, boolean confirmed) {
        CandidateCaseMapper.ModelInput input = db.modelInput(row.id);
        if (input == null || input.purged() || confirmed && !input.confirmed()) return null;
        try {
            if (input.payloadSha256() == null || !input.payloadSha256().matches("[0-9a-f]{64}")) throw new IOException();
            Path file = directory(row.publicId).resolve(input.payloadSha256() + ".json"); safe(file);
            if (Files.size(file) > 4_000_000) throw new IOException();
            byte[] bytes = Files.readAllBytes(file);
            if (!CandidateCaseService.hash(bytes).equals(input.payloadSha256())) throw new IOException();
            return (ObjectNode) json.readTree(bytes);
        } catch (IOException error) { throw CaseException.dependency(); }
    }

    private String crop(String encoded) {
        try { return checkedCrop(encoded); }
        catch (IOException error) { throw new IllegalArgumentException("The portrait image cannot be read."); }
    }
    private String checkedCrop(String encoded) throws IOException {
        if (encoded.length() > 2_000_000) throw new IllegalArgumentException("Portrait exceeds its size limit.");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Use a PNG or JPEG portrait.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT))
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 8_000_000) {
                    throw new IllegalArgumentException("Use a PNG or JPEG portrait up to 8 million pixels.");
                }
                BufferedImage image = reader.read(0), square = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
                var graphics = square.createGraphics();
                int size = Math.min(image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, 224, 224, (image.getWidth()-size)/2, (image.getHeight()-size)/2,
                        (image.getWidth()+size)/2, (image.getHeight()+size)/2, null); graphics.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(square, "jpeg", out);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } finally { reader.dispose(); }
        }
    }
    private Path directory(String id) {
        if (id == null || !id.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw CaseException.missing();
        return root.resolve(id);
    }
    private void safe(Path path) throws IOException {
        if (!path.startsWith(root) || Files.isSymbolicLink(path)
                || !path.toRealPath().startsWith(root.toRealPath())) throw new IOException("Invalid model input path.");
    }
    private void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }
}
