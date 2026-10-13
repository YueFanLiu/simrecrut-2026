package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Follow the business API contract while retaining the existing RuoYi authentication layer. */
@RestController
@RequestMapping("/api/v1/resumes")
@org.springframework.context.annotation.Profile("!resume-workspace")
public class StandardResumeController {
    private final UserResumeServices users;
    public StandardResumeController(UserResumeServices users) { this.users = users; }
    private ResumeService service() throws Exception { return users.forUser(SecurityUtils.getUserId()); }
    private Map<String, Object> envelope(Object data) {
        return Map.of("code", 0, "message", "OK", "requestId", UUID.randomUUID().toString(), "data", data);
    }
    private Object operation(String key, String action, String id, JsonNode body, ResumeOperation.Work work) throws Exception {
        return envelope(ResumeOperation.perform(service(), key, action, id, body, work));
    }
    @GetMapping @PreAuthorize("@ss.hasPermi('simrecrut:resume:list')")
    public Object list() throws Exception { return envelope(service().list()); }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('simrecrut:resume:list')")
    public Object detail(@PathVariable String id) throws Exception { return envelope(service().detail(id)); }
    @PostMapping("/import") @PreAuthorize("@ss.hasPermi('simrecrut:resume:upload')")
    @Log(title="Resume import", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public Object importFile(@RequestHeader("Idempotency-Key") String key, @RequestBody JsonNode body) throws Exception {
        Facts.keys(body, Set.of("filename", "content"));
        Facts.require(body.path("filename").asText().toLowerCase(Locale.ROOT).endsWith(".pdf"), "Upload a PDF file.");
        Facts.require(body.path("content").asText().length() <= 13981016, "The PDF exceeds 10 MB.");
        return operation(key, "import", "", body, service -> service.importFile(body));
    }
    @PostMapping("/{id}/clean") @PreAuthorize("@ss.hasPermi('simrecrut:resume:edit')")
    public Object clean(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody JsonNode body) throws Exception {
        return operation(key, "clean", id, body, service -> service.clean(id, body));
    }
    @PostMapping("/{id}/save") @PreAuthorize("@ss.hasPermi('simrecrut:resume:edit')")
    @Log(title="Resume draft", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public Object save(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody JsonNode body) throws Exception {
        Facts.keys(body, Set.of("draft", "confirmed", "expectedRevision"));
        Facts.require(body.path("expectedRevision").isIntegralNumber(), "Include the loaded expectedRevision.");
        Facts.require(!body.path("confirmed").asBoolean(), "Use the confirmation action to lock reviewed facts.");
        return operation(key, "save", id, body, service -> service.save(id, body));
    }
    @PostMapping("/{id}/confirm") @PreAuthorize("@ss.hasPermi('simrecrut:resume:edit') and @ss.hasPermi('simrecrut:resume:confirm')")
    @Log(title="Resume confirmation", businessType=BusinessType.UPDATE, isSaveRequestData=false, isSaveResponseData=false)
    public Object confirm(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody JsonNode body) throws Exception {
        Facts.keys(body, Set.of("draft", "confirmed", "expectedRevision"));
        Facts.require(body.path("expectedRevision").isIntegralNumber(), "Include the loaded expectedRevision.");
        Facts.require(body.path("confirmed").asBoolean(), "Explicit review confirmation is required.");
        return operation(key, "confirm", id, body, service -> service.save(id, body));
    }
    @PostMapping("/{id}/upload") @PreAuthorize("@ss.hasPermi('simrecrut:resume:confirm')")
    @Log(title="Resume publication", businessType=BusinessType.INSERT, isSaveRequestData=false, isSaveResponseData=false)
    public Object upload(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) throws Exception {
        return operation(key, "upload", id, null, service -> service.upload(id));
    }
    @PostMapping("/{id}/cleanup") @PreAuthorize("@ss.hasPermi('simrecrut:resume:confirm')")
    public Object cleanup(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) throws Exception {
        return operation(key, "cleanup", id, null, service -> service.cleanupOriginal(id));
    }
    @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermi('simrecrut:resume:delete')")
    @Log(title="Resume deletion", businessType=BusinessType.DELETE, isSaveRequestData=false, isSaveResponseData=false)
    public Object delete(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) throws Exception {
        return operation(key, "delete", id, null, service -> service.delete(id));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> error(Exception error) {
        int status = error instanceof org.springframework.security.access.AccessDeniedException ? 403
            : error instanceof IllegalArgumentException || error instanceof org.springframework.web.bind.MissingRequestHeaderException
                || error instanceof org.springframework.http.converter.HttpMessageNotReadableException ? 400 : 503;
        String message = status == 403 ? "Access denied." : status == 400 ? error.getMessage() : "The operation could not finish. Retry with the same operation key.";
        return ResponseEntity.status(status).body(Map.of("code", status, "message", message, "requestId", UUID.randomUUID().toString(), "data", Map.of("retryable", status == 503)));
    }
}
