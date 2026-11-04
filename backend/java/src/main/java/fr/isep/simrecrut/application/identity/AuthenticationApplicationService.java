package fr.isep.simrecrut.application.identity;

import fr.isep.simrecrut.dto.identity.AuthenticationDtos.CurrentUserResponse;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LoginRequest;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LoginResponse;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LogoutResponse;
import fr.isep.simrecrut.entity.identity.AccountStatus;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import fr.isep.simrecrut.security.authentication.JwtTokenService;
import fr.isep.simrecrut.security.authentication.PasswordAuthenticationService;
import fr.isep.simrecrut.security.authentication.RedisSessionStore;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import fr.isep.simrecrut.service.identity.LoginAttemptService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class AuthenticationApplicationService {

    private final PasswordAuthenticationService passwords;
    private final LoginAttemptService attempts;
    private final JwtTokenService tokens;
    private final RedisSessionStore sessions;
    private final UserAccountMapper accounts;
    private final RoleMapper roles;

    public AuthenticationApplicationService(PasswordAuthenticationService passwords,
            LoginAttemptService attempts, JwtTokenService tokens, RedisSessionStore sessions,
            UserAccountMapper accounts, RoleMapper roles) {
        this.passwords = passwords;
        this.attempts = attempts;
        this.tokens = tokens;
        this.sessions = sessions;
        this.accounts = accounts;
        this.roles = roles;
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        LoginAttemptService.Attempt attempt = attempts.begin(request.identifier(), clientIp);
        try {
            var verified = passwords.verifyAccount(request.identifier(), request.password());
            UserAccount account = accounts.findById(verified.userId());
            requireActiveAccount(account, verified.securityVersion());
            if (attempt.accountId() != null && attempt.accountId() != verified.userId()) {
                throw invalidCredentials();
            }

            attempts.succeeded(attempt);
            var issued = tokens.issue(account.getPublicId(), verified.securityVersion());
            // A signed token is returned only after its expiring Redis session is stored.
            sessions.create(issued.sessionId(), account.getId(), account.getPublicId(),
                    verified.securityVersion(), issued.expiresAt());
            long remaining = Math.max(0, Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
            return new LoginResponse("Bearer", issued.token(), remaining, account.getPublicId());
        } catch (BadCredentialsException exception) {
            attempts.failed(attempt);
            throw invalidCredentials();
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public CurrentUserResponse currentUser(AccountPrincipal principal) {
        if (principal == null) {
            throw invalidCredentials();
        }
        try {
            UserAccount account = accounts.findById(principal.userId());
            requireActiveAccount(account, principal.securityVersion());
            if (!principal.publicId().equals(account.getPublicId())) {
                throw invalidCredentials();
            }
            var roleCodes = roles.findByUserId(account.getId()).stream()
                    .map(role -> role.getRoleCode().name()).distinct().sorted().toList();
            var permissionCodes = roles.findPermissionCodesByUserId(account.getId()).stream()
                    .distinct().sorted().toList();
            return new CurrentUserResponse(account.getPublicId(), account.getUsername(), account.getEmail(),
                    account.getStatus().name(), roleCodes, permissionCodes);
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public LogoutResponse logout(AccountPrincipal principal) {
        if (principal == null) {
            throw invalidCredentials();
        }
        sessions.revoke(principal.sessionId());
        return new LogoutResponse(true);
    }

    private void requireActiveAccount(UserAccount account, long securityVersion) {
        if (account == null || account.getStatus() != AccountStatus.ACTIVE
                || account.getEmailVerifiedAt() == null || account.getSecurityVersion() == null
                || account.getSecurityVersion() != securityVersion) {
            throw invalidCredentials();
        }
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid username/email or password.");
    }
}
