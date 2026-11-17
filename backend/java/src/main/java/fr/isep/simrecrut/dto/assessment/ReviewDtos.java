package fr.isep.simrecrut.dto.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.StrictRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {
    private ReviewDtos() { }
    public record Page<T>(List<T> items, long total, int page, int pageSize) { }
    public record HrCaseSummary(String caseId, String jobId, String jobVersionId, String jobTitle,
            String processingStatus, String humanStatus, boolean resultsRestricted, LocalDateTime expiresAt) { }
    public record HrCaseDetail(String caseId, JsonNode professional, JsonNode job, JsonNode matching,
            JsonNode weights, JsonNode contributions, JsonNode objective, JsonNode neutralMl, JsonNode biasedMl,
            List<HumanSummary> human, JsonNode versions, LocalDateTime expiresAt) { }
    public record HumanSummary(String condition, String status, String decision, String reason) { }
    public record TaskSummary(String reviewId, String caseId, String jobTitle, String condition,
            String purpose, String status, LocalDateTime createdAt) { }
    public record TaskDetail(String reviewId, String caseId, String status, String condition, String purpose,
            JsonNode professional, JsonNode job, JsonNode rubric, JsonNode scenario, String instruction, JsonNode versions,
            boolean submissionVisible, String decision, String reason, LocalDateTime submittedAt) { }
    public record EligibleReviewer(String userId, String displayName) { }
    public record Assignment(@NotBlank @Pattern(regexp = "[0-9A-HJKMNP-TV-Z]{26}") String reviewerId,
            @NotBlank @Pattern(regexp = "STANDARD|INSTRUCTED_BIAS") String condition) implements StrictRequest { }
    public record AssignReviewsRequest(@NotEmpty @Size(max = 2) List<@NotNull @Valid Assignment> assignments)
            implements StrictRequest { }
    public record SubmitReviewRequest(@NotBlank @Pattern(regexp = "ACCEPT|REJECT|Accept|Reject") String decision,
            @NotBlank @Size(max = 2000) String reason) implements StrictRequest { }
    public record AssignmentResult(List<TaskSummary> tasks) { }
    public record SubmissionResult(String reviewId, String status, LocalDateTime submittedAt) { }
}
