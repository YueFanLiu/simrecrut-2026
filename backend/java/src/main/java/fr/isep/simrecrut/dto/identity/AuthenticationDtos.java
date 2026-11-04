package fr.isep.simrecrut.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthenticationDtos {

    private AuthenticationDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotNull @Size(min = 1, max = 256) String password) {

        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }

    public record LoginResponse(
            String tokenType, String accessToken, long expiresInSeconds, String publicId) {

        @Override
        public String toString() {
            return "LoginResponse[redacted]";
        }
    }

    public record CurrentUserResponse(
            String publicId, String username, String email, String status,
            List<String> roles, List<String> permissions) {

        public CurrentUserResponse {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }

    public record LogoutResponse(boolean loggedOut) {
    }
}
