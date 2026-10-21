package fr.isep.simrecrut.controller.job;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.job.JobDtos.Page;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.job.JobService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/candidate/jobs")
public class CandidateJobController {
    private final JobService jobs;
    public CandidateJobController(JobService jobs) { this.jobs = jobs; }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JsonNode>>> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword) {
        return HrJobController.response(200, jobs.list(actor, true, page, pageSize, keyword));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JsonNode>> detail(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) { return HrJobController.response(200, jobs.detail(actor, jobId, true)); }
}
