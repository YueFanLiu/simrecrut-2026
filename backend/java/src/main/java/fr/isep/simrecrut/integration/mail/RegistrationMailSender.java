package fr.isep.simrecrut.integration.mail;

import fr.isep.simrecrut.config.properties.RegistrationProperties;
import fr.isep.simrecrut.service.identity.RegistrationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class RegistrationMailSender {

    private final JavaMailSender sender;
    private final RegistrationProperties properties;
    private final String from;

    public RegistrationMailSender(
            JavaMailSender sender,
            RegistrationProperties properties,
            @Value("${simrecrut.mail.from:}") String configuredFrom,
            @Value("${spring.mail.username:}") String username) {
        this.sender = sender;
        this.properties = properties;
        this.from = configuredFrom.isBlank() ? username : configuredFrom;
    }

    public void sendRegistrationCode(String email, String code) {
        if (from.isBlank()) {
            throw RegistrationException.unavailable();
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("SimRecrut registration verification");
        message.setText(
                "Your registration verification code is: " + code
                        + "\n\nThis code expires in " + properties.codeTtl().toSeconds()
                        + " seconds and can be used once."
                        + "\nIf you did not request this code, ignore this message.");

        try {
            sender.send(message);
        } catch (MailException exception) {
            throw RegistrationException.unavailable();
        }
    }
}
