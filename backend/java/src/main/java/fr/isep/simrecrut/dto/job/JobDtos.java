package fr.isep.simrecrut.dto.job;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class JobDtos {
    private JobDtos() { }

    public enum Criterion { SKILLS, EXPERIENCE, EDUCATION, LANGUAGES, PROJECTS }

    public interface StrictRequest {
        @JsonAnySetter
        default void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported job request field.");
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String description) implements StrictRequest { }

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String description,
            @NotNull @Positive Long revision) implements StrictRequest { }

    public record Requirement(
            @NotNull Criterion criterionType,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requirementCode,
            @NotNull JsonNode payload,
            @NotNull Boolean assessmentIncluded,
            @NotNull Boolean mandatory,
            @NotNull Boolean publicVisible,
            @NotNull Boolean reviewed,
            @Size(max = 1000) String evidenceExcerpt,
            @Size(max = 255) String evidenceLocation,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 255) String> warnings)
            implements StrictRequest { }

    public record RequirementsRequest(
            @NotNull @Positive Long revision,
            @NotNull @Size(max = 100) List<@NotNull @Valid Requirement> requirements) implements StrictRequest { }

    public record Weights(
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7) BigDecimal skills,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7) BigDecimal experience,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7) BigDecimal education,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7) BigDecimal languages,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7) BigDecimal projects)
            implements StrictRequest {
        public List<BigDecimal> values() { return List.of(skills, experience, education, languages, projects); }
    }

    public record WeightsRequest(
            @NotNull @Positive Long revision,
            @NotNull @Size(min = 1, max = 5) List<@NotNull Criterion> activeCriteria,
            @NotNull @Valid Weights weights) implements StrictRequest { }

    public record RevisionRequest(@NotNull @Positive Long revision) implements StrictRequest { }
    public record CloneRequest(
            @NotBlank @Pattern(regexp = "[0-9A-HJKMNP-TV-Z]{26}") String sourceJobVersionId)
            implements StrictRequest { }
    public record EmptyRequest() implements StrictRequest { }

    public record Issue(String field, String code, String message) { }
    public record Validation(boolean valid, List<Issue> errors, List<String> warnings) { }
    public record Page<T>(List<T> items, long total, int page, int pageSize) { }
}
