package fr.isep.simrecrut.controller.identity;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = AdminRoleController.class)
public class AdminRoleExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> denied() {
        return ResponseEntity.status(403)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.failure(403, "Access denied.", false));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unavailable() {
        return ResponseEntity.status(503)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.failure(503, "Role information is temporarily unavailable.", true));
    }
}
