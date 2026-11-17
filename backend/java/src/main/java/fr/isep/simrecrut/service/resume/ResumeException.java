package fr.isep.simrecrut.service.resume;

/** Safe workflow errors shared by the HTTP adapter and the private resume store. */
public class ResumeException extends IllegalArgumentException {

    public enum Reason {
        NOT_FOUND, CONFLICT, UNAVAILABLE
    }

    private final Reason reason;

    public ResumeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
