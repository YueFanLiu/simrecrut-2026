package fr.isep.simrecrut.dto.resume;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record ImportRequest(
            @NotBlank @Size(max = 255) @Pattern(regexp = "(?i).+\\.pdf") String filename,
            @NotBlank @Size(max = 13_981_016) String content) {

        @JsonAnySetter
        public void rejectUnknown(String name, JsonNode value) {
            throw new IllegalArgumentException("Unsupported request field.");
        }

        @Override
        public String toString() {
            return "ImportRequest[redacted]";
        }
    }

    public record EditRequest(
            @NotNull JsonNode draft,
            @NotNull Boolean confirmed,
            @NotNull @Min(1) @Max(Integer.MAX_VALUE) Long expectedRevision) {

        @JsonAnySetter
        public void rejectUnknown(String name, JsonNode value) {
            throw new IllegalArgumentException("Unsupported request field.");
        }

        @Override
        public String toString() {
            return "EditRequest[redacted]";
        }
    }

    public record EmptyRequest() {

        @JsonAnySetter
        public void rejectUnknown(String name, JsonNode value) {
            throw new IllegalArgumentException("This action accepts no request fields.");
        }
    }
}
