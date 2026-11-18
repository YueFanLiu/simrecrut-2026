package fr.isep.simrecrut.controller.job;

import static fr.isep.simrecrut.dto.job.JobDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.job.JobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/hr/jobs")
public class HrJobController {
    private final JobService jobs;
    public HrJobController(JobService jobs) { this.jobs = jobs; }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JsonNode>>> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword) {
        return response(200, jobs.list(actor, false, page, pageSize, keyword));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JsonNode>> create(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateRequest body) {
        return response(201, jobs.create(actor, key, body));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JsonNode>> draft(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) { return response(200, jobs.detail(actor, jobId, false)); }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JsonNode>> update(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody UpdateRequest body) { return response(200, jobs.update(actor, jobId, key, body)); }

    @GetMapping("/{jobId}/requirements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requirements(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) {
        JsonNode draft = jobs.detail(actor, jobId, false);
        return response(200, Map.of("jobId", jobId, "revision", draft.path("revision"),
                "requirements", draft.path("requirements")));
    }

    @PutMapping("/{jobId}/requirements")
    public ResponseEntity<ApiResponse<JsonNode>> requirements(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RequirementsRequest body) {
        return response(200, jobs.saveRequirements(actor, jobId, key, body));
    }

    @GetMapping("/{jobId}/weights")
    public ResponseEntity<ApiResponse<Map<String, Object>>> weights(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) {
        JsonNode draft = jobs.detail(actor, jobId, false);
        return response(200, Map.of("jobId", jobId, "revision", draft.path("revision"),
                "weights", draft.path("weights"), "activeCriteria", draft.path("activeCriteria")));
    }

    @PutMapping("/{jobId}/weights")
    public ResponseEntity<ApiResponse<JsonNode>> weights(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody WeightsRequest body) {
        return response(200, jobs.saveWeights(actor, jobId, key, body));
    }

    @PostMapping("/{jobId}/extract-requirements")
    public ResponseEntity<ApiResponse<JsonNode>> extract(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) {
        return response(202, jobs.extract(actor, jobId, key));
    }

    @GetMapping("/{jobId}/extraction-status")
    public ResponseEntity<ApiResponse<JsonNode>> extraction(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) { return response(200, jobs.extractionStatus(actor, jobId)); }

    @PostMapping("/{jobId}/validate")
    public ResponseEntity<ApiResponse<Validation>> validate(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @Valid @RequestBody(required = false) EmptyRequest body) {
        return response(200, jobs.validate(actor, jobId));
    }

    @PostMapping("/{jobId}/publish")
    public ResponseEntity<ApiResponse<JsonNode>> publish(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RevisionRequest body) { return response(200, jobs.publish(actor, jobId, key, body)); }

    @GetMapping("/{jobId}/versions")
    public ResponseEntity<ApiResponse<List<JsonNode>>> versions(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId) { return response(200, jobs.versions(actor, jobId)); }

    @GetMapping("/{jobId}/versions/{jobVersionId}")
    public ResponseEntity<ApiResponse<JsonNode>> version(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @PathVariable String jobVersionId) {
        return response(200, jobs.version(actor, jobId, jobVersionId));
    }

    @PostMapping("/{jobId}/drafts")
    public ResponseEntity<ApiResponse<JsonNode>> cloneDraft(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String jobId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CloneRequest body) { return response(201, jobs.cloneDraft(actor, jobId, key, body)); }

    static <T> ResponseEntity<ApiResponse<T>> response(int status, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Job request completed.", data));
    }
}
