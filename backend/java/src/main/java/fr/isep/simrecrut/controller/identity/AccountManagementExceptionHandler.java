package fr.isep.simrecrut.controller.identity;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.service.identity.*;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = {AdminUserController.class, RolePermissionController.class,
        PasswordResetController.class})
public class AccountManagementExceptionHandler {
    @ExceptionHandler(IdentityManagementException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> business(IdentityManagementException error) {
        return failure(error.status(), error.getMessage(), error.status() == 503 || error.status() == 429);
    }
    @ExceptionHandler({AccountConflictException.class, DuplicateKeyException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> conflict() {
        return failure(409, "The account conflicts with an existing record.", false);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> denied() {
        return failure(403, "Access denied.", false);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalid() {
        return failure(400, "Invalid identity request. Check fields, identifiers and revisions.", false);
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> storage() {
        return failure(503, "Identity storage is temporarily unavailable.", true);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected() {
        return failure(500, "The identity operation could not be completed.", false);
    }
    private ResponseEntity<ApiResponse<Map<String, Object>>> failure(int status, String message, boolean retryable) {
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (status == 429) response.header(HttpHeaders.RETRY_AFTER, "60");
        return response.body(ApiResponse.failure(status, message, retryable));
    }
}
