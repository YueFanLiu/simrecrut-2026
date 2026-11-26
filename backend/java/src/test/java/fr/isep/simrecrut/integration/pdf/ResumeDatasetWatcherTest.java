package fr.isep.simrecrut.integration.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeDatasetWatcherTest {
    @TempDir Path root;

    @Test
    void waitsForStableInputAndRegeneratesChangedFilesWithoutDeletingSources() throws Exception {
        Path raw = root.resolve("raw"), clean = root.resolve("clean");
        var watcher = new ResumeDatasetWatcher(raw, clean);
        Files.writeString(raw.resolve("sample.txt"), "Skills: Java");
        watcher.scan();
        assertFalse(Files.exists(clean.resolve("sample.txt.json")));
        watcher.scan();
        assertTrue(Files.readString(clean.resolve("sample.txt.json")).contains("Java"));
        Files.writeString(raw.resolve("sample.txt"), "Skills: Python and Docker");
        watcher.scan();
        watcher.scan();
        String output = Files.readString(clean.resolve("sample.txt.json"));
        assertTrue(output.contains("Python"));
        assertFalse(output.contains("Java"));
        assertTrue(Files.exists(raw.resolve("sample.txt")));
        try (var outputs = Files.list(clean)) {
            assertEquals(1, outputs.count());
        }
    }

    @Test
    void recordsFailureWithoutStoppingOtherFiles() throws Exception {
        Path raw = root.resolve("raw"), clean = root.resolve("clean");
        var watcher = new ResumeDatasetWatcher(raw, clean);
        Files.writeString(raw.resolve("broken.pdf"), "not a pdf");
        Files.writeString(raw.resolve("valid.txt"), "Docker");
        watcher.scan();
        watcher.scan();
        assertTrue(Files.exists(clean.resolve("broken.pdf.error.json")));
        assertTrue(Files.exists(clean.resolve("valid.txt.json")));
    }
}
