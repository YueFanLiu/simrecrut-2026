package fr.isep.simrecrut.integration.pdf;

import fr.isep.simrecrut.service.assessment.CaseException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class CaseFileStore {
    private static final java.util.concurrent.Semaphore VALIDATION_CAPACITY = new java.util.concurrent.Semaphore(2);
    private final Path root;
    public CaseFileStore(@Value("${simrecrut.storage.case-workspace:runtime/cases}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public void validate(String filename, byte[] data) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || data.length == 0
                || data.length > 10 * 1024 * 1024) throw new IllegalArgumentException("A PDF up to 10 MiB is required.");
        if (!VALIDATION_CAPACITY.tryAcquire()) throw CaseException.dependency();
        try (var pdf = Loader.loadPDF(data)) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 20) {
                throw new IllegalArgumentException("The PDF must be unencrypted and contain 1-20 pages.");
            }
        } catch (IOException exception) { throw new IllegalArgumentException("The PDF is invalid or encrypted."); }
        finally { VALIDATION_CAPACITY.release(); }
    }
    public String stage(String caseId, byte[] data) throws IOException {
        Path directory = directory(caseId);
        Files.createDirectories(directory);
        safe(directory);
        restrict(directory, true);
        Path file = directory.resolve(UUID.randomUUID() + ".pdf");
        Files.write(file, data, StandardOpenOption.CREATE_NEW);
        restrict(file, false);
        return root.relativize(file).toString().replace('\\', '/');
    }
    public byte[] read(String relative) throws IOException {
        Path file = root.resolve(relative).normalize(); safe(file);
        if (Files.size(file) > 10 * 1024 * 1024) throw new IOException("Case file exceeds the limit.");
        return Files.readAllBytes(file);
    }
    public void discard(String relative) throws IOException {
        Path file = root.resolve(relative).normalize();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { safe(file); Files.delete(file); }
    }
    public void removeCase(String caseId) throws IOException {
        Path directory = directory(caseId);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        safe(directory);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Unexpected case storage link.");
                safe(path); Files.deleteIfExists(path);
            }
        }
    }
    private Path directory(String caseId) {
        if (caseId == null || !caseId.matches("[0-9A-HJKMNP-TV-Z]{26}")) throw CaseException.missing();
        return root.resolve(caseId);
    }
    private void safe(Path path) throws IOException {
        if (!path.startsWith(root) || !path.toRealPath().startsWith(root.toRealPath()) || Files.isSymbolicLink(path)) {
            throw new IOException("Invalid case storage path.");
        }
    }
    private void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }
}
