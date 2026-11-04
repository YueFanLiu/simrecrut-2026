package fr.isep.simrecrut.service.identity;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationFlowException extends AuthenticationException {

    public enum Reason {
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Reason reason;
    private final long retryAfterSeconds;

    private AuthenticationFlowException(Reason reason, String message, long retryAfterSeconds) {
        super(message);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Reason reason() {
        return reason;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static AuthenticationFlowException rateLimited(long seconds) {
        return new AuthenticationFlowException(
                Reason.RATE_LIMITED, "Too many login attempts. Try again later.", Math.max(1, seconds));
    }

    public static AuthenticationFlowException unavailable() {
        return new AuthenticationFlowException(
                Reason.UNAVAILABLE, "Authentication is temporarily unavailable.", 0);
    }
}
