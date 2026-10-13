package fr.isep.simrecrut.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Load only SMTP settings from the teammate's private desktop file before bean creation. */
public class DesktopMailEnvironment implements EnvironmentPostProcessor, Ordered {
    private static final Set<String> KEYS = Set.of("SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM");
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String location = environment.getProperty("SIMRECRUT_MAIL_FILE");
        Path file = location == null || location.isBlank()
                ? Path.of(environment.getProperty("user.home", System.getProperty("user.home")), "Desktop", "simrecrut-gmail.private.env")
                : Path.of(location);
        if (!Files.exists(file)) return;
        Map<String,Object> values = new LinkedHashMap<>();
        try {
            int number = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                number++;
                String entry = line.trim();
                if (number == 1 && entry.startsWith("\uFEFF")) entry = entry.substring(1).trim();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                int separator = entry.indexOf('=');
                if (separator < 1 || !KEYS.contains(entry.substring(0,separator).trim()))
                    throw new IllegalArgumentException("Invalid SMTP setting at line " + number);
                String value = entry.substring(separator+1).trim();
                if (value.length()>=2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'")))
                    value = value.substring(1,value.length()-1);
                values.put(entry.substring(0,separator).trim(), value);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Could not read the private desktop SMTP file. Check its path and NAME=value format.");
        }
        // Desktop SMTP values override old mailbox values; other application settings stay untouched.
        environment.getPropertySources().addFirst(new MapPropertySource("desktopMailSettings", values));
    }
}
