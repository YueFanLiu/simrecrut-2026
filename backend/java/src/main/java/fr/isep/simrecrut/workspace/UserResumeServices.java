package fr.isep.simrecrut.workspace;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Keep temporary originals and indexes isolated by authenticated RuoYi user ID. */
@Service
@org.springframework.context.annotation.Profile("!resume-workspace")
public class UserResumeServices {
    private final Path root;
    private final Map<Long, ResumeService> sessions = new ConcurrentHashMap<>();
    public UserResumeServices(@Value("${simrecrut.resumes.directory:runtime/resumes}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    synchronized ResumeService forUser(long userId) throws Exception {
        if (userId < 1) throw new IllegalArgumentException("An authenticated user is required.");
        if (sessions.containsKey(userId)) return sessions.get(userId);
        if (sessions.size() >= 32) throw new IllegalArgumentException("Resume processing capacity is reached. Retry later.");
        ResumeService service = new ResumeService(root.resolve(Long.toString(userId))) {
            @Override Map<String, String> settings() throws Exception {
                // Deployment secrets stay server-side and are never accepted from a browser.
                Map<String, String> config = new HashMap<>();
                for (String key : java.util.List.of("host", "port", "user", "password", "database"))
                    config.put(key, System.getenv().getOrDefault("AIVEN_" + key.toUpperCase(java.util.Locale.ROOT), ""));
                String certificate = System.getenv("AIVEN_CA_FILE");
                config.put("caPem", certificate == null ? "" : Files.readString(Path.of(certificate)));
                return config;
            }
        };
        sessions.put(userId, service);
        return service;
    }
    @PreDestroy public void shutdown() { sessions.values().forEach(ResumeService::shutdown); }
}
