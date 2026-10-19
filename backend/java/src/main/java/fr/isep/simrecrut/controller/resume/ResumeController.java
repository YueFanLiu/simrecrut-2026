package fr.isep.simrecrut.controller.resume;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.application.resume.ResumeApplicationService;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.resume.ResumeDtos.EditRequest;
import fr.isep.simrecrut.dto.resume.ResumeDtos.EmptyRequest;
import fr.isep.simrecrut.dto.resume.ResumeDtos.ImportRequest;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeApplicationService resumes;

    public ResumeController(ResumeApplicationService resumes) {
        this.resumes = resumes;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JsonNode>>> list(@AuthenticationPrincipal AccountPrincipal account)
            throws Exception {
        return response(200, resumes.list(account.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JsonNode>> detail(
            @AuthenticationPrincipal AccountPrincipal account, @PathVariable String id) throws Exception {
        return response(200, resumes.detail(account.userId(), id));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<JsonNode>> importFile(@AuthenticationPrincipal AccountPrincipal account,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ImportRequest body) throws Exception {
        JsonNode result = resumes.importFile(account.userId(), key, body);
        return response(result.path("duplicate").asBoolean() ? 200 : 201, result);
    }

    @PostMapping("/{id}/clean")
    public ResponseEntity<ApiResponse<JsonNode>> clean(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) throws Exception {
        return response(202, resumes.clean(account.userId(), id, key));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<ApiResponse<JsonNode>> save(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EditRequest body) throws Exception {
        return response(200, resumes.save(account.userId(), id, key, body));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<JsonNode>> confirm(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EditRequest body) throws Exception {
        return response(200, resumes.confirm(account.userId(), id, key, body));
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<ApiResponse<JsonNode>> publish(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) throws Exception {
        return response(200, resumes.publish(account.userId(), id, key));
    }

    @PostMapping("/{id}/cleanup")
    public ResponseEntity<ApiResponse<JsonNode>> cleanup(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) throws Exception {
        return response(200, resumes.cleanup(account.userId(), id, key));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<JsonNode>> delete(@AuthenticationPrincipal AccountPrincipal account,
            @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) EmptyRequest body) throws Exception {
        JsonNode result = resumes.delete(account.userId(), id, key);
        return response(result.path("pending").asBoolean() ? 202 : 200, result);
    }

    private <T> ResponseEntity<ApiResponse<T>> response(int status, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("OK", data));
    }
}
