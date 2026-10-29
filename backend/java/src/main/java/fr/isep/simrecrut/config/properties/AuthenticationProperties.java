package fr.isep.simrecrut.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

@Profile("!resume-workspace")
@ConfigurationProperties(prefix = "simrecrut.authentication")
public record AuthenticationProperties(
        Duration tokenTtl,
        String issuer,
        String audience,
        String signingSecret,
        Duration clockSkew,
        Duration loginWindow,
        Duration lockDuration,
        int maxFailedAttempts,
        int maxAccountAttempts,
        Duration ipWindow,
        int maxIpAttempts) {

    public AuthenticationProperties {
        if (!positive(tokenTtl) || tokenTtl.toSeconds() < 60 || tokenTtl.compareTo(Duration.ofDays(1)) > 0
                || issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || clockSkew == null || clockSkew.isNegative() || clockSkew.toSeconds() > 60
                || !positive(loginWindow) || !positive(lockDuration) || !positive(ipWindow)
                || maxFailedAttempts < 1 || maxAccountAttempts < maxFailedAttempts || maxIpAttempts < 1) {
            throw new IllegalArgumentException("Invalid authentication configuration.");
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && duration.toSeconds() >= 1;
    }

    @Override
    public String toString() {
        return "AuthenticationProperties[redacted]";
    }
}
