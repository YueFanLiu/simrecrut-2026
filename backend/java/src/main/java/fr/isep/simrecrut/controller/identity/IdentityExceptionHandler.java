package fr.isep.simrecrut.controller.identity;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.service.identity.AccountConflictException;
import fr.isep.simrecrut.service.identity.RegistrationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import org.springframework.security.core.AuthenticationException;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class IdentityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityExceptionHandler.class);

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> registration(RegistrationException exception) {
        int status = switch (exception.reason()) {
            case INVALID_VERIFICATION -> 400;
            case RATE_LIMITED -> 429;
            case UNAVAILABLE -> 503;
        };
        return error(status, exception.getMessage(), status == 429 || status == 503,
                exception.retryAfterSeconds());
    }

    @ExceptionHandler(AccountConflictException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> conflict() {
        return error(409, "The account conflicts with an existing record.", false, 0);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalidBody() {
        return error(400, "Invalid registration request.", false, 0);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalidInput(IllegalArgumentException exception) {
        return error(400, exception.getMessage(), false, 0);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> storage() {
        LOGGER.warn("Registration storage is unavailable.");
        return error(503, "Registration is temporarily unavailable.", true, 0);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected(Exception exception) {
        LOGGER.error("Registration failed with exception type {}.", exception.getClass().getSimpleName());
        return error(500, "Registration could not be completed.", false, 0);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> error(
            int status, String message, boolean retryable, long retryAfterSeconds) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore());
        if (retryAfterSeconds > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return response.body(ApiResponse.failure(status, message, retryable));
    }

    @ExceptionHandler(AuthenticationFlowException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> authenticationFlow(
        AuthenticationFlowException exception) {

        int status = switch (exception.reason()) {
            case RATE_LIMITED -> 429;
            case UNAVAILABLE -> 503;
        };

        return error(
            status,
            exception.getMessage(),
            true,
            exception.retryAfterSeconds());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> authenticationFailed() {
        return error(
            401,
            "Invalid username/email or password.",
            false,
            0);
    }
}
