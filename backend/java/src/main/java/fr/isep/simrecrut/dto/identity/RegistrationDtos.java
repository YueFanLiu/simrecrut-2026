package fr.isep.simrecrut.dto.identity;

import fr.isep.simrecrut.entity.identity.AccountRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record CodeRequest(
            @NotBlank @Size(max = 254) String email) {
    }

    public record CodeResponse(
            String challengeId,
            long expiresInSeconds,
            long resendAfterSeconds) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 254) String email,
            @NotNull @Size(max = 256) String password,
            @NotNull @Size(max = 256) String passwordConfirmation,
            @NotNull AccountRole accountType,
            @NotNull
            @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            String challengeId,
            @NotNull @Pattern(regexp = "[0-9]{6}") String emailCode,
            @AssertTrue boolean agreedToTerms) {

        @Override
        public String toString() {
            return "RegisterRequest[redacted]";
        }
    }

    public record RegisterResponse(
            String publicId,
            String username,
            AccountRole accountType) {
    }

    public record ApiResponse<T>(
            int code,
            String message,
            String requestId,
            T data) {

        public static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(0, message, UUID.randomUUID().toString(), data);
        }

        public static ApiResponse<Map<String, Object>> failure(
                int code, String message, boolean retryable) {
            return new ApiResponse<>(
                    code,
                    message,
                    UUID.randomUUID().toString(),
                    Map.<String, Object>of("retryable", retryable));
        }
    }
}
