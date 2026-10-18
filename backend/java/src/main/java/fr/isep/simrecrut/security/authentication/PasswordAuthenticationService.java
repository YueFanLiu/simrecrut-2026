package fr.isep.simrecrut.security.authentication;

import fr.isep.simrecrut.entity.identity.AccountStatus;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import fr.isep.simrecrut.service.identity.AccountInputValidator;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordAuthenticationService {

    /** Internal identity supplied by the server authentication layer. */
    public record AccountPrincipal(long userId) implements java.security.Principal {
        public AccountPrincipal {
            if (userId < 1) {
                throw new IllegalArgumentException("A valid account ID is required.");
            }
        }

        @Override
        public String getName() {
            return Long.toString(userId);
        }
    }


    private final UserAccountMapper accounts;
    private final AccountInputValidator inputs;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public PasswordAuthenticationService(
        UserAccountMapper accounts,
        AccountInputValidator inputs,
        PasswordEncoder passwords) {
        this.accounts = accounts;
        this.inputs = inputs;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /**
     * Verify credentials and return the internal user ID.
     * The caller must enforce rate limits before invoking this method.
     * Successful verification does not establish an authenticated session.
     */
    public long verify(String identifier, String password) {
        if (password == null
            || password.isEmpty()
            || password.codePointCount(0, password.length()) > 128) {
            throw invalidCredentials();
        }

        UserAccount account;

        try {
            String value = identifier == null ? "" : identifier.trim();

            account = value.contains("@")
                ? accounts.findByEmail(inputs.normalizeEmail(value))
                : accounts.findByUsername(inputs.normalizeUsername(value));
        } catch (IllegalArgumentException exception) {
            throw invalidCredentials();
        }

        String hash = account == null ? dummyHash : account.getPasswordHash();
        boolean matches = passwords.matches(password, hash);

        if (account == null
            || !matches
            || account.getStatus() != AccountStatus.ACTIVE
            || account.getEmailVerifiedAt() == null) {
            throw invalidCredentials();
        }

        return account.getId();
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException(
            "Invalid username/email or password.");
    }
}
