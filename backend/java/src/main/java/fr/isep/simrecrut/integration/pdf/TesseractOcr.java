package fr.isep.simrecrut.integration.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Run local OCR without sending original documents to an external service. */
final class TesseractOcr {
    private static final java.util.concurrent.Semaphore CAPACITY = new java.util.concurrent.Semaphore(2);
    String read(BufferedImage image, long remainingMillis) throws IOException {
        if (remainingMillis <= 0) throw new IOException("Extraction timed out.");
        if (!CAPACITY.tryAcquire()) throw new IOException("Local OCR is busy. Retry extraction later.");
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("simrecrut-ocr-");
            Path input = directory.resolve("page.png"), output = directory.resolve("result");
            ImageIO.write(image, "png", input.toFile());
            String executable = System.getenv().getOrDefault("TESSERACT_EXECUTABLE", "tesseract");
            if (executable.equals("tesseract") && Files.isRegularFile(Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe"))) executable = "C:/Program Files/Tesseract-OCR/tesseract.exe";
            List<String> command = new ArrayList<>(List.of(executable, input.toString(), output.toString(),
                "-l", System.getenv().getOrDefault("TESSERACT_LANGUAGES", "eng+fra"), "--oem", "1", "--psm", "3"));
            String models = System.getenv("TESSDATA_PATH");
            if (models != null && !models.isBlank()) command.addAll(List.of("--tessdata-dir", models));
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(Math.min(30_000, remainingMillis), TimeUnit.MILLISECONDS)) throw new IOException("Local OCR timed out.");
            if (process.exitValue() != 0) throw new IOException("Local OCR failed. Check the installed language models.");
            Path result = directory.resolve("result.txt");
            if (Files.size(result) > 500_000) throw new IOException("OCR text is too large.");
            return Files.readString(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OCR was cancelled.", e);
        } finally {
            try {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    try { process.waitFor(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
                if (directory != null) {
                    try (var files = Files.list(directory)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
                    Files.deleteIfExists(directory);
                }
            } finally { CAPACITY.release(); }
        }
    }
}
