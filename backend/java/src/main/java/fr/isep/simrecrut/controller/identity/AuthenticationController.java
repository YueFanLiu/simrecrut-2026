package fr.isep.simrecrut.controller.identity;

import fr.isep.simrecrut.application.identity.RegistrationApplicationService;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.CodeRequest;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.CodeResponse;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.RegisterRequest;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import fr.isep.simrecrut.application.identity.AuthenticationApplicationService;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.CurrentUserResponse;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LoginRequest;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LoginResponse;
import fr.isep.simrecrut.dto.identity.AuthenticationDtos.LogoutResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final RegistrationApplicationService registration;

    private final AuthenticationApplicationService authentication;

    public AuthenticationController(
        RegistrationApplicationService registration,
        AuthenticationApplicationService authentication) {
        this.registration = registration;
        this.authentication = authentication;
    }

    @PostMapping("/registration/code")
    public ResponseEntity<ApiResponse<CodeResponse>> requestCode(
            @Valid @RequestBody CodeRequest body, HttpServletRequest request) {
        CodeResponse result = registration.requestCode(body.email(), request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Verification code sent.", result));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        RegisterResponse result = registration.register(body, request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Registration completed.", result));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest body,
        HttpServletRequest request) {

        LoginResponse result = authentication.login(
            body,
            request.getRemoteAddr());

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(
                "Login completed.",
                result));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> currentUser(
        @AuthenticationPrincipal AccountPrincipal principal) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(
                "Current user retrieved.",
                authentication.currentUser(principal)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
        @AuthenticationPrincipal AccountPrincipal principal) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(
                "Logout completed.",
                authentication.logout(principal)));
    }
}
