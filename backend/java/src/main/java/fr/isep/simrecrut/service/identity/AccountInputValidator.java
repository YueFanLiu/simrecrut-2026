package fr.isep.simrecrut.service.identity;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AccountInputValidator {

    private final Validator validator;

    public AccountInputValidator(Validator validator) {
        this.validator = validator;
    }

    public String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim();

        if (!username.matches("[A-Za-z0-9_]{3,20}")) {
            throw new IllegalArgumentException(
                "Use a username of 3-20 letters, numbers or underscores.");
        }

        return username.toLowerCase(Locale.ROOT);
    }

    public String normalizeEmail(String value) {
        String email = value == null
            ? ""
            : value.trim().toLowerCase(Locale.ROOT);

        if (!validator.validate(new EmailValue(email)).isEmpty()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        return email;
    }

    public void validateNewPassword(String password, String confirmation) {
        if (password == null) {
            throw new IllegalArgumentException("Enter a password.");
        }

        int length = password.codePointCount(0, password.length());

        if (length < 15 || length > 128) {
            throw new IllegalArgumentException(
                "Use a password of 15-128 characters.");
        }

        if (!Objects.equals(password, confirmation)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
    }

    private record EmailValue(
        @NotBlank
        @Email
        @Size(max = 254)
        String value) {
    }
}
