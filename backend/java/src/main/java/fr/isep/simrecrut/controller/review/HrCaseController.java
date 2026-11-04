package fr.isep.simrecrut.controller.review;

import fr.isep.simrecrut.dto.assessment.ReviewDtos.*;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.ReviewCaseService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/hr/cases")
public class HrCaseController {
    private final ReviewCaseService service;
    public HrCaseController(ReviewCaseService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<ApiResponse<Page<HrCaseSummary>>> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "") String jobId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Cases retrieved.", service.hrCases(actor, jobId, page, pageSize)));
    }
    @GetMapping("/{caseId}")
    public ResponseEntity<ApiResponse<HrCaseDetail>> detail(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Case retrieved.", service.hrCase(actor, caseId)));
    }
}
