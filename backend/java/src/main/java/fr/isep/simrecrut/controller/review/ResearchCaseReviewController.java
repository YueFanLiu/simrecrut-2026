package fr.isep.simrecrut.controller.review;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.assessment.ReviewDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.ReviewCaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/research/cases/{caseId}")
public class ResearchCaseReviewController {
    private final ReviewCaseService service;
    public ResearchCaseReviewController(ReviewCaseService service) { this.service = service; }
    @GetMapping("/eligible-reviewers")
    public ResponseEntity<ApiResponse<List<EligibleReviewer>>> eligible(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Eligible reviewers retrieved.", service.eligible(actor, caseId, query, limit)));
    }
    @PostMapping("/reviews")
    public ResponseEntity<ApiResponse<JsonNode>> assign(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AssignReviewsRequest body) {
        var result = service.assign(actor, caseId, key, body);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Primary review tasks assigned.", result.data()));
    }
}
