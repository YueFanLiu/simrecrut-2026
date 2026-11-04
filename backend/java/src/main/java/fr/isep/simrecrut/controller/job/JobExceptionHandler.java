package fr.isep.simrecrut.controller.job;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.service.job.JobException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Profile("!resume-workspace")
@RestControllerAdvice(assignableTypes = {HrJobController.class, CandidateJobController.class})
public class JobExceptionHandler {
    @ExceptionHandler(JobException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> workflow(JobException error) {
        Map<String, Object> data = new LinkedHashMap<>(error.details());
        data.put("retryable", error.status() == 503);
        return response(error.status(), error.getMessage(), data);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalid(Exception error) {
        return response(400, "Invalid job request. Check fields, revision and operation key.", Map.of("retryable", false));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> denied() {
        return response(403, "Access denied.", Map.of("retryable", false));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> conflict() {
        return response(409, "A concurrent operation changed this job. Reload before continuing.", Map.of("retryable", false));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unavailable() {
        return response(503, "Job storage is temporarily unavailable. Retry with the same operation key.", Map.of("retryable", true));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected() {
        return response(500, "The job operation could not be completed.", Map.of("retryable", false));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> response(int status, String message, Map<String, Object> data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(status, message, UUID.randomUUID().toString(), data));
    }
}
