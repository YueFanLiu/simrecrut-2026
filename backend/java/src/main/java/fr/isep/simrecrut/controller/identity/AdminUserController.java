package fr.isep.simrecrut.controller.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.identity.AdminAccountService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminAccountService service;
    public AdminUserController(AdminAccountService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<UserPage>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) AccountStatus status, @RequestParam(required = false) AccountRole role) {
        return ok(service.list(page, pageSize, keyword, status, role));
    }
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserView>> detail(@PathVariable String userId) {
        return ok(service.detail(userId));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<UserView>> create(@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody CreateUserRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Account created. Mailbox password setup is required.", service.create(actor, body)));
    }
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserView>> update(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String userId, @Valid @RequestBody UpdateUserRequest body) {
        return ok(service.update(actor, userId, body));
    }
    @PutMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserView>> status(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String userId, @Valid @RequestBody StatusRequest body) {
        return ok(service.status(actor, userId, body));
    }
    @PutMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<UserView>> assignRoles(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String userId, @Valid @RequestBody RolesRequest body) {
        return ok(service.assignRoles(actor, userId, body));
    }
    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Account operation completed.", data));
    }
}
