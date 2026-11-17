package fr.isep.simrecrut.controller.candidate;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.service.assessment.CaseException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = CandidateCaseController.class)
public class CandidateCaseExceptionHandler {
    @ExceptionHandler(CaseException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> business(CaseException error) {
        return error(error.status(), error.code(), error.getMessage());
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> denied() { return error(403, "ACCESS_DENIED", "Access denied."); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalid() {
        return error(400, "INVALID_CASE_REQUEST", "Invalid case request. Check PDF, fields, revision and operation key.");
    }
    @ExceptionHandler({DataAccessException.class, java.io.IOException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> storage() {
        return error(503, "CASE_STORAGE_UNAVAILABLE", "Case storage is temporarily unavailable.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected() {
        return error(500, "CASE_OPERATION_FAILED", "The case operation could not be completed.");
    }
    private ResponseEntity<ApiResponse<Map<String, Object>>> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiResponse<>(status,
                message, UUID.randomUUID().toString(), Map.of("retryable", status == 503, "errorCode", code)));
    }
}
