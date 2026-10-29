package fr.isep.simrecrut.controller.review;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.assessment.ReviewDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.ReviewCaseService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/reviewer/tasks")
public class ReviewerTaskController {
    private final ReviewCaseService service;
    public ReviewerTaskController(ReviewCaseService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskSummary>>> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Tasks retrieved.", service.tasks(actor, status, page, pageSize)));
    }
    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<TaskDetail>> detail(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String reviewId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Task retrieved.", service.task(actor, reviewId)));
    }
    @PostMapping("/{reviewId}/submit")
    public ResponseEntity<ApiResponse<JsonNode>> submit(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String reviewId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody SubmitReviewRequest body) {
        var result = service.submit(actor, reviewId, key, body);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Review submitted.", result.data()));
    }
}
