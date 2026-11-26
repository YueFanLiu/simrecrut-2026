package fr.isep.simrecrut.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

@Profile("!resume-workspace")
@ConfigurationProperties(prefix = "simrecrut.registration")
public record RegistrationProperties(
        Duration codeTtl,
        Duration resendCooldown,
        int maxCodeAttempts,
        int maxEmailSendsPerHour,
        int maxIpSendsPerHour,
        int maxGlobalSendsPerHour,
        int maxIpVerificationsPerTenMinutes,
        String codeSecret) {

    public RegistrationProperties {
        if (codeTtl == null
                || codeTtl.toSeconds() < 60
                || codeTtl.toSeconds() > 3600
                || resendCooldown == null
                || resendCooldown.toSeconds() < 1
                || resendCooldown.compareTo(codeTtl) > 0
                || maxCodeAttempts < 1
                || maxEmailSendsPerHour < 1
                || maxIpSendsPerHour < 1
                || maxGlobalSendsPerHour < 1
                || maxIpVerificationsPerTenMinutes < 1) {
            throw new IllegalArgumentException("Invalid registration configuration.");
        }
    }

    @Override
    public String toString() {
        return "RegistrationProperties[redacted]";
    }
}
