package fr.isep.simrecrut.service.assessment;

public class CaseException extends RuntimeException {
    private final int status;
    private final String code;
    public CaseException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static CaseException missing() { return new CaseException(404, "CASE_NOT_FOUND", "Case unavailable."); }
    public static CaseException conflict() {
        return new CaseException(409, "CASE_CONFLICT", "The case state or revision changed. Reload before retrying.");
    }
    public static CaseException dependency() {
        return new CaseException(503, "ASSESSMENT_UNAVAILABLE", "Assessment configuration or service is unavailable.");
    }
}
