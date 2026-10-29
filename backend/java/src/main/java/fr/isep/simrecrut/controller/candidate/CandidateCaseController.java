package fr.isep.simrecrut.controller.candidate;

import static fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.*;
import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.CandidateCaseService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/candidate/cases")
public class CandidateCaseController {
    private final CandidateCaseService service;
    public CandidateCaseController(CandidateCaseService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<JsonNode>> create(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateCaseRequest body) {
        return result(service.create(actor, key, body));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<CasePage>> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ok(service.list(actor, page, pageSize));
    }
    @PostMapping(value = "/{caseId}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JsonNode>> upload(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        if (file.getSize() > 10 * 1024 * 1024 || file.isEmpty()) throw new IllegalArgumentException("Invalid PDF size.");
        return result(service.upload(actor, caseId, key, file.getOriginalFilename(), file.getBytes()));
    }
    @GetMapping("/{caseId}/status")
    public ResponseEntity<ApiResponse<CaseStatus>> status(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId) { return ok(service.status(actor, caseId)); }
    @GetMapping("/{caseId}/draft")
    public ResponseEntity<ApiResponse<ReviewDraft>> draft(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId) { return ok(service.draft(actor, caseId)); }
    @PutMapping("/{caseId}/draft")
    public ResponseEntity<ApiResponse<JsonNode>> saveDraft(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody UpdateDraftRequest body) { return result(service.saveDraft(actor, caseId, key, body)); }
    @GetMapping("/{caseId}/research-attributes")
    public ResponseEntity<ApiResponse<ResearchAttributes>> attributes(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId) { return ok(service.attributes(actor, caseId)); }
    @PutMapping("/{caseId}/research-attributes")
    public ResponseEntity<ApiResponse<JsonNode>> saveAttributes(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AttributesRequest body) { return result(service.saveAttributes(actor, caseId, key, body)); }
    @PostMapping("/{caseId}/confirm")
    public ResponseEntity<ApiResponse<JsonNode>> confirm(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ConfirmRequest body) { return result(service.confirm(actor, caseId, key, body)); }
    @PostMapping("/{caseId}/run")
    public ResponseEntity<ApiResponse<JsonNode>> run(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) { return result(service.run(actor, caseId, key)); }
    @PostMapping("/{caseId}/retry")
    public ResponseEntity<ApiResponse<JsonNode>> retry(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) { return result(service.retry(actor, caseId, key)); }
    @GetMapping("/{caseId}/results")
    public ResponseEntity<ApiResponse<FiveResults>> results(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId) { return ok(service.results(actor, caseId)); }
    @DeleteMapping("/{caseId}")
    public ResponseEntity<ApiResponse<JsonNode>> delete(@AuthenticationPrincipal AccountPrincipal actor,
            @PathVariable String caseId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) { return result(service.delete(actor, caseId, key)); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("Case retrieved.", value));
    }
    private ResponseEntity<ApiResponse<JsonNode>> result(OperationResult<JsonNode> value) {
        return ResponseEntity.status(value.status()).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(value.status() == 202 ? "Operation accepted; poll the current state."
                        : "Case operation completed.", value.data()));
    }
}
