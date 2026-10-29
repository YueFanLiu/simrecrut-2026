package fr.isep.simrecrut.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Send verification messages through server-only SMTP credentials. */
@Component
public class PasswordResetMail {
    private final JavaMailSender sender;
    private final String username, password, from;
    public PasswordResetMail(JavaMailSender sender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${simrecrut.mail.from:}") String from) {
        this.sender = sender; this.username = username; this.password = password; this.from = from.isBlank()?username:from;
    }
    public boolean available() { return !username.isBlank() && !password.isBlank() && !from.isBlank(); }
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("SimRecrut password reset verification");
        message.setText("Your SimRecrut verification code is: " + code
                + "\nThis code expires in 10 minutes and can be used once."
                + "\nIf you did not request a password reset, ignore this message.");
        sender.send(message);
    }
}
