package fr.isep.simrecrut.controller.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import fr.isep.simrecrut.application.identity.PasswordResetApplicationService;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetApplicationService service;
    public PasswordResetController(PasswordResetApplicationService service) { this.service = service; }
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<ResetOptions>> options() { return ok("Recovery settings retrieved.", service.options()); }
    @PostMapping("/code")
    public ResponseEntity<ApiResponse<ResetCodeResponse>> requestCode(@Valid @RequestBody ResetCodeRequest body,
            HttpServletRequest request) {
        return ok("If the account is eligible, a verification code has been sent.",
                service.requestCode(body, request.getRemoteAddr()));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<ResetResult>> reset(@Valid @RequestBody ResetRequest body,
            HttpServletRequest request) {
        return ok("Password updated. Sign in again.", service.reset(body, request.getRemoteAddr()));
    }
    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(message, data));
    }
}
