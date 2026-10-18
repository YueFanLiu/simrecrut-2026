package fr.isep.simrecrut.application.identity;

import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.config.properties.RegistrationProperties;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.CodeResponse;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.RegisterRequest;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.RegisterResponse;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.integration.mail.RegistrationMailSender;
import fr.isep.simrecrut.service.identity.AccountInputValidator;
import fr.isep.simrecrut.service.identity.AccountService;
import fr.isep.simrecrut.service.identity.EmailVerificationService;
import fr.isep.simrecrut.service.identity.RegistrationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class RegistrationApplicationService {

    private final AccountInputValidator inputs;
    private final EmailVerificationService verification;
    private final RegistrationMailSender mail;
    private final PasswordEncoder passwords;
    private final PublicIdGenerator publicIds;
    private final AccountService accounts;
    private final RegistrationProperties properties;

    public RegistrationApplicationService(
            AccountInputValidator inputs,
            EmailVerificationService verification,
            RegistrationMailSender mail,
            PasswordEncoder passwords,
            PublicIdGenerator publicIds,
            AccountService accounts,
            RegistrationProperties properties) {
        this.inputs = inputs;
        this.verification = verification;
        this.mail = mail;
        this.passwords = passwords;
        this.publicIds = publicIds;
        this.accounts = accounts;
        this.properties = properties;
    }

    public CodeResponse requestCode(String email, String clientIp) {
        String normalizedEmail = inputs.normalizeEmail(email);
        var issued = verification.issue(normalizedEmail, clientIp);

        try {
            mail.sendRegistrationCode(normalizedEmail, issued.code());
        } catch (RegistrationException exception) {
            verification.discard(normalizedEmail, issued.challengeId());
            throw exception;
        }

        long remainingSeconds = Duration.between(Instant.now(), issued.expiresAt()).toSeconds();
        if (remainingSeconds <= 0) {
            verification.discard(normalizedEmail, issued.challengeId());
            throw RegistrationException.unavailable();
        }

        return new CodeResponse(
                issued.challengeId(), remainingSeconds, properties.resendCooldown().toSeconds());
    }

    public RegisterResponse register(RegisterRequest request, String clientIp) {
        verification.checkSubmissionLimit(clientIp);

        if (!request.agreedToTerms()) {
            throw new IllegalArgumentException("Accept the terms before registering.");
        }
        if (request.accountType() != AccountRole.CANDIDATE && request.accountType() != AccountRole.HR) {
            throw new IllegalArgumentException(
                    "Public registration supports Candidate and HR accounts only.");
        }

        String username = inputs.normalizeUsername(request.username());
        String email = inputs.normalizeEmail(request.email());
        inputs.validateNewPassword(request.password(), request.passwordConfirmation());
        verification.consume(email, request.challengeId(), request.emailCode());

        LocalDateTime verifiedAt = LocalDateTime.now(ZoneOffset.UTC);
        String passwordHash = passwords.encode(request.password());
        String publicId = publicIds.nextId();

        // Redis consumption and password hashing precede the short account transaction.
        accounts.createVerifiedAccount(
                publicId, username, email, passwordHash, request.accountType(), verifiedAt);

        return new RegisterResponse(publicId, username, request.accountType());
    }
}
