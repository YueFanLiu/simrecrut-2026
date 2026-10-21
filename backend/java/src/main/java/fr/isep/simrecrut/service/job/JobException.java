package fr.isep.simrecrut.service.job;

import java.util.Map;

public class JobException extends RuntimeException {
    private final int status;
    private final Map<String, Object> details;

    public JobException(int status, String message) { this(status, message, Map.of()); }
    public JobException(int status, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.details = details;
    }
    public int status() { return status; }
    public Map<String, Object> details() { return details; }
}
