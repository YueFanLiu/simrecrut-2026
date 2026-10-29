package fr.isep.simrecrut.dto.assessment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CandidateCaseDtos {
    private CandidateCaseDtos() { }
    public interface StrictRequest {
        @JsonAnySetter default void rejectUnknown(String name, JsonNode value) {
            throw new IllegalArgumentException("Unsupported case request field.");
        }
    }
    public record CreateCaseRequest(@NotBlank @Pattern(regexp = "[0-9A-HJKMNP-TV-Z]{26}")
            String jobVersionId) implements StrictRequest { }
    public record UpdateDraftRequest(@NotNull JsonNode professional, @NotNull @Positive Long revision,
            @Size(max = 5) List<@NotBlank String> confirmedAbsent) implements StrictRequest { }
    public record AttributeInput(@NotBlank String type, @NotBlank @Size(max = 128) String value)
            implements StrictRequest { }
    public record AttributesRequest(@NotNull @Size(max = 4) List<@NotNull @Valid AttributeInput> attributes,
            @NotNull @Positive Long revision) implements StrictRequest { }
    public record ConfirmRequest(@NotNull @Positive Long revision, @NotNull @AssertTrue Boolean confirmed)
            implements StrictRequest { }
    public record EmptyRequest() implements StrictRequest { }
    public record CaseAction(String caseId, String processingStatus, long revision, LocalDateTime expiresAt) { }
    public record CaseStatus(String caseId, String processingStatus, String stage, boolean retryable,
            String errorCode, String message, String cleanupStatus, LocalDateTime updatedAt) { }
    public record CaseSummary(String caseId, String jobTitle, String processingStatus, String humanStatus,
            LocalDateTime createdAt, LocalDateTime purgeAt) { }
    public record CasePage(List<CaseSummary> items, long total, int page, int pageSize) { }
    public record ReviewDraft(String caseId, long revision, JsonNode professional, List<String> confirmedAbsent,
            List<String> unresolvedRequiredFields) { }
    public record ResearchAttribute(String type, String value, String source, boolean candidateConfirmed) { }
    public record ResearchAttributes(String caseId, long revision, List<ResearchAttribute> attributes) { }
    public record PublicJob(String jobId, String jobVersionId, int version, String title) { }
    public record HumanResult(String status, String decision, String reason) { }
    public record Privacy(LocalDateTime purgeAt, boolean canDeleteNow) { }
    public record FiveResults(String caseId, String automatedStatus, PublicJob job, JsonNode objective,
            JsonNode neutralMl, JsonNode biasedMl, Map<String, HumanResult> human, Privacy privacy) { }
    public record DeleteResult(String caseId, String status) { }
    public record ObjectiveView(BigDecimal score, BigDecimal displayScore, String decision,
            List<String> strengths, List<String> gaps) { }
    public record MlView(Double acceptanceScore, String decision, Map<String, String> attributeSummary) { }
    public record OperationResult<T>(int status, T data) { }
}
