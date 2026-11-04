package fr.isep.simrecrut.application.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.auth.PasswordResetCodes;
import fr.isep.simrecrut.auth.PasswordResetMail;
import fr.isep.simrecrut.config.properties.RegistrationProperties;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.mapper.identity.*;
import fr.isep.simrecrut.service.identity.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!resume-workspace")
public class PasswordResetApplicationService {
    private final PasswordResetCodes codes;
    private final PasswordResetMail mail;
    private final UserAccountMapper accounts;
    private final IdentityAdminMapper db;
    private final AccountInputValidator inputs;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetApplicationService(PasswordResetCodes codes, PasswordResetMail mail,
            UserAccountMapper accounts, IdentityAdminMapper db, AccountInputValidator inputs,
            PasswordEncoder passwords, ObjectMapper json, PlatformTransactionManager transactions,
            RegistrationProperties properties) {
        this.codes = codes;
        this.mail = mail;
        this.accounts = accounts;
        this.db = db;
        this.inputs = inputs;
        this.passwords = passwords;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
        try { secret = Base64.getDecoder().decode(properties.codeSecret()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid code secret."); }
        if (secret.length < 32) throw new IllegalArgumentException("The code secret must contain at least 32 bytes.");
    }

    public ResetOptions options() { return new ResetOptions(mail.available()); }

    public ResetCodeResponse requestCode(ResetCodeRequest body, String clientIp) {
        String email = inputs.normalizeEmail(body.email());
        if (!mail.available()) throw IdentityManagementException.unavailable();
        if (!codes.allow(digest("email", email), digest("ip", clientIp))) throw limited();
        String id = UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        UserAccount account = accounts.findByEmail(email);
        // Unknown and disabled addresses receive the same public response without a stored challenge or email.
        if (account != null && account.getStatus() == AccountStatus.ACTIVE) {
            try {
                String snapshot = json.writeValueAsString(new ResetSnapshot(account.getId(), account.getPublicId(),
                        email, account.getSecurityVersion()));
                codes.store(id, digest("code", id, email, code), snapshot);
                mail.send(email, code);
            } catch (Exception exception) {
                codes.discard(id);
                throw IdentityManagementException.unavailable();
            }
        }
        return new ResetCodeResponse(id, 600, 60);
    }

    public ResetResult reset(ResetRequest body, String clientIp) {
        if (!codes.allowSubmission(digest("ip", clientIp))) throw limited();
        String email = inputs.normalizeEmail(body.email());
        inputs.validateNewPassword(body.password(), body.passwordConfirmation());
        String snapshot = codes.consume(body.challengeId(), digest("code", body.challengeId(), email, body.emailCode()));
        if (snapshot == null || snapshot.isBlank()) throw invalidProof();
        ResetSnapshot proof;
        try { proof = json.readValue(snapshot, ResetSnapshot.class); }
        catch (Exception exception) { throw invalidProof(); }
        if (!email.equals(proof.email())) throw invalidProof();
        // Password hashing and Redis consumption happen before the short MySQL transaction.
        String encoded = passwords.encode(body.password());
        return tx.execute(ignored -> {
            if (db.resetPassword(proof.userId(), email, proof.securityVersion(), encoded) != 1) throw invalidProof();
            // The security-version increment invalidates every old JWT, including concurrent login attempts.
            if (db.audit(proof.userId(), "PASSWORD_RESET", "ACCOUNT",
                    AdminAccountService.referenceHash(proof.publicId())) != 1) {
                throw IdentityManagementException.unavailable();
            }
            return new ResetResult(true);
        });
    }

    private String digest(String purpose, String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update("PASSWORD_RESET".getBytes(StandardCharsets.UTF_8));
            for (String value : concat(purpose, values)) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                mac.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) { throw IdentityManagementException.unavailable(); }
    }
    private List<String> concat(String purpose, String[] values) {
        List<String> result = new ArrayList<>(); result.add(purpose); result.addAll(Arrays.asList(values)); return result;
    }
    private IdentityManagementException limited() {
        return new IdentityManagementException(429, "Too many requests. Try again later.");
    }
    private IdentityManagementException invalidProof() {
        return new IdentityManagementException(400, "Invalid or expired verification code. Request a new code.");
    }
    public record ResetSnapshot(long userId, String publicId, String email, long securityVersion) {
        @Override public String toString() { return "ResetSnapshot[redacted]"; }
    }
}
