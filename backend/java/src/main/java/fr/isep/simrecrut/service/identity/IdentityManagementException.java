package fr.isep.simrecrut.service.identity;

public class IdentityManagementException extends RuntimeException {
    private final int status;
    public IdentityManagementException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
    public static IdentityManagementException conflict() {
        return new IdentityManagementException(409, "The record changed. Reload before retrying.");
    }
    public static IdentityManagementException unavailable() {
        return new IdentityManagementException(503, "Identity operations are temporarily unavailable.");
    }
}
