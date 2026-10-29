package fr.isep.simrecrut.controller.resume;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.service.resume.ResumeException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = ResumeController.class)
public class ResumeExceptionHandler {

    @ExceptionHandler(ResumeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> workflow(ResumeException error) {
        int status = switch (error.reason()) {
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case UNAVAILABLE -> 503;
        };
        return error(status, error.getMessage(), status == 503);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalid(Exception exception) {
        return error(400, "Invalid resume request. Check the fields and operation key.", false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> denied() {
        return error(403, "Access denied.", false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unauthenticated() {
        return error(401, "Authentication required.", false);
    }

    @ExceptionHandler({IOException.class, SQLException.class, DataAccessException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> unavailable() {
        return error(503, "The operation could not finish. Retry with the same operation key.", true);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected() {
        return error(500, "The resume operation could not be completed.", false);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> error(int status, String message, boolean retryable) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.failure(status, message, retryable));
    }
}
