package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline-only polling adapter; never uploads data or removes research source files. */
public final class ResumeDatasetWatcher {
    private final Path input;
    private final Path output;
    private final Map<Path, String> observed = new HashMap<>();
    private final LocalResumeCleaner cleaner = new LocalResumeCleaner();
    private final ObjectMapper json = new ObjectMapper();

    public ResumeDatasetWatcher(Path input, Path output) throws IOException {
        Files.createDirectories(input);
        this.input = input.toRealPath();
        Files.createDirectories(output);
        this.output = output.toRealPath();
        if (this.output.startsWith(this.input) || this.input.startsWith(this.output)) {
            throw new IllegalArgumentException("Input and output directories must be separate.");
        }
    }

    /** Two consecutive scans with unchanged size/time are required before reading a file. */
    public void scan() throws Exception {
        try (var files = Files.list(input)) {
            var present = files.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                .filter(p -> List.of(".pdf", ".docx", ".txt").stream()
                    .anyMatch(p.getFileName().toString().toLowerCase(Locale.ROOT)::endsWith)).sorted().toList();
            observed.keySet().retainAll(present);
            for (Path file : present) {
                String fingerprint = Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
                if (!fingerprint.equals(observed.put(file, fingerprint))) continue;
                Path destination = output.resolve(file.getFileName() + ".json");
                Path error = output.resolve(file.getFileName() + ".error.json");
                // An oversize input is never loaded into memory.
                if (Files.size(file) > LocalResumeCleaner.MAX_BYTES) {
                    var failure = json.createObjectNode().put("status", "FAILED")
                        .put("sourceFingerprint", fingerprint).put("error", "Input exceeds 10 MiB.");
                    atomicWrite(error, failure);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                if (!fingerprint.equals(Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis())) {
                    observed.remove(file);
                    continue;
                }
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (Files.exists(destination)
                    && hash.equals(json.readTree(destination.toFile()).path("sourceSha256").asText())) continue;
                if (Files.exists(error)
                    && hash.equals(json.readTree(error.toFile()).path("sourceSha256").asText())) continue;
                try {
                    var draft = cleaner.clean(file.getFileName().toString(), bytes);
                    draft.put("sourceSha256", hash);
                    draft.put("purpose", "OFFLINE_DATA_PREPARATION");
                    atomicWrite(destination, draft);
                    Files.deleteIfExists(error);
                } catch (Exception ex) {
                    // Parser exceptions can contain source content, so persist a bounded generic message.
                    var failure = json.createObjectNode().put("status", "FAILED").put("sourceSha256", hash)
                        .put("error", "Extraction failed. Check file format, text availability and resource limits.");
                    atomicWrite(error, failure);
                    // A previous successful JSON must not represent a newly failed source version.
                    Files.deleteIfExists(destination);
                }
            }
        }
    }

    private void atomicWrite(Path destination, com.fasterxml.jackson.databind.JsonNode value) throws IOException {
        Path temporary = Files.createTempFile(output, ".cleaning-", ".tmp");
        try {
            Files.writeString(temporary, json.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <raw-directory> <clean-directory>");
        var watcher = new ResumeDatasetWatcher(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Offline resume watcher running. Press Ctrl+C to stop.");
        while (!Thread.currentThread().isInterrupted()) {
            watcher.scan();
            Thread.sleep(2000);
        }
    }
}
