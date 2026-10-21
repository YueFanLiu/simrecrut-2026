package fr.isep.simrecrut.integration.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline batch adapter using exactly the same gateway implementation as Java services. */
public final class ResumeCleaningCli {
    private ResumeCleaningCli() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <input-directory> <output-directory>");
        Path input = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(input)) throw new IllegalArgumentException("Output must be outside input.");
        Files.createDirectories(output);
        var cleaner = new LocalResumeCleaner();
        var json = new ObjectMapper();
        try (var paths = Files.list(input)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String filename = file.getFileName().toString();
                String lower = filename.toLowerCase(java.util.Locale.ROOT);
                if (!List.of(".pdf", ".docx", ".txt").stream().anyMatch(lower::endsWith)) continue;
                if (Files.size(file) > LocalResumeCleaner.MAX_BYTES) {
                    throw new IllegalArgumentException("Input exceeds 10 MiB.");
                }
                var draft = cleaner.clean(filename, Files.readAllBytes(file));
                // Atomic create prevents overwriting an existing reviewed record.
                Files.writeString(output.resolve(filename + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(draft) + "\n",
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            }
        }
    }
}
