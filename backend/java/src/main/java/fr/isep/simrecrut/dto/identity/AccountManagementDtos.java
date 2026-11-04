package fr.isep.simrecrut.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.identity.AccountStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class AccountManagementDtos {
    private AccountManagementDtos() { }

    public interface StrictRequest {
        @JsonAnySetter
        default void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("Unsupported identity request field.");
        }
    }

    public record CreateUserRequest(@NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 254) String email,
            @NotEmpty @Size(max = 4) List<@NotNull AccountRole> roles) implements StrictRequest { }

    public record UpdateUserRequest(@NotBlank @Size(max = 254) String email,
            @NotNull @Positive Long revision) implements StrictRequest { }

    public record StatusRequest(@NotNull AccountStatus status,
            @NotNull @Positive Long revision) implements StrictRequest { }

    public record RolesRequest(@NotEmpty @Size(max = 4) List<@NotNull AccountRole> roles,
            @NotNull @Positive Long revision) implements StrictRequest { }

    public record PermissionsRequest(@NotNull @Size(max = 200) List<@NotBlank String> permissions,
            @NotNull @Positive Long revision) implements StrictRequest { }

    public record UserView(String userId, String username, String email, AccountStatus status,
            boolean emailVerified, boolean passwordSetupRequired, List<String> roles, long revision,
            LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record UserPage(List<UserView> items, long total, int page, int pageSize) { }

    public record PermissionView(String code, String name, String description,
            List<AccountRole> allowedRoles, boolean protectedAdminPermission) { }

    public record RolePermissionsView(String roleCode, long revision, List<String> permissions) { }

    public record ResetCodeRequest(@NotBlank @Size(max = 254) String email) implements StrictRequest { }

    public record ResetRequest(@NotBlank @Size(max = 254) String email,
            @NotNull @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            String challengeId,
            @NotNull @Pattern(regexp = "[0-9]{6}") String emailCode,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotNull @Size(max = 256) String password,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotNull @Size(max = 256)
            String passwordConfirmation) implements StrictRequest {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }

    public record ResetOptions(boolean emailEnabled) { }
    public record ResetCodeResponse(String challengeId, long expiresInSeconds, long resendAfterSeconds) { }
    public record ResetResult(boolean passwordUpdated) { }
}
