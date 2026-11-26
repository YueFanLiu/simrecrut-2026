package fr.isep.simrecrut.service.identity;

public class RegistrationException extends RuntimeException {

    public enum Reason {
        INVALID_VERIFICATION,
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Reason reason;
    private final long retryAfterSeconds;

    private RegistrationException(Reason reason, String message, long retryAfterSeconds) {
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

    public static RegistrationException invalidVerification() {
        return new RegistrationException(
                Reason.INVALID_VERIFICATION, "Invalid or expired verification code.", 0);
    }

    public static RegistrationException rateLimited(long seconds) {
        return new RegistrationException(
                Reason.RATE_LIMITED,
                "Too many requests. Try again later.",
                Math.max(1, seconds));
    }

    public static RegistrationException unavailable() {
        return new RegistrationException(
                Reason.UNAVAILABLE, "Registration is temporarily unavailable.", 0);
    }
}
