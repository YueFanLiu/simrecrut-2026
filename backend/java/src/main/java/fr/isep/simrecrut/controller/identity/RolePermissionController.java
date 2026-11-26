package fr.isep.simrecrut.controller.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.identity.AdminAccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/admin")
public class RolePermissionController {
    private final AdminAccountService service;
    public RolePermissionController(AdminAccountService service) { this.service = service; }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionView>>> permissions() { return ok(service.permissions()); }

    @GetMapping("/roles/{roleCode}/permissions")
    public ResponseEntity<ApiResponse<RolePermissionsView>> rolePermissions(@PathVariable AccountRole roleCode) {
        return ok(service.rolePermissions(roleCode));
    }
    @PutMapping("/roles/{roleCode}/permissions")
    public ResponseEntity<ApiResponse<RolePermissionsView>> savePermissions(
            @AuthenticationPrincipal AccountPrincipal actor, @PathVariable AccountRole roleCode,
            @Valid @RequestBody PermissionsRequest body) {
        return ok(service.savePermissions(actor, roleCode, body));
    }
    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Permission operation completed.", data));
    }
}
