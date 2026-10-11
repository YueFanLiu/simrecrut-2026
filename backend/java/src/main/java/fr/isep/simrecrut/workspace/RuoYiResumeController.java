package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Expose the shared cleaner through authenticated, user-scoped RuoYi business routes. */
@RestController
@RequestMapping("/simrecrut/resumes")
@org.springframework.context.annotation.Profile("!resume-workspace")
public class RuoYiResumeController {
    private final UserResumeServices users;
    public RuoYiResumeController(UserResumeServices users) { this.users = users; }
    private ResumeService service() throws Exception { return users.forUser(SecurityUtils.getUserId()); }
    @GetMapping @PreAuthorize("@ss.hasPermi('simrecrut:resume:list')")
    public AjaxResult list() throws Exception { return AjaxResult.success(service().list()); }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('simrecrut:resume:list')")
    public AjaxResult detail(@PathVariable String id) throws Exception { return AjaxResult.success(service().detail(id)); }
    @PostMapping("/import") @PreAuthorize("@ss.hasPermi('simrecrut:resume:upload')")
    @Log(title = "Resume import", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult importFile(@RequestBody JsonNode body) throws Exception {
        Facts.require(body.path("filename").asText().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"), "Upload a PDF file.");
        Facts.require(body.path("content").asText().length() <= 13981016, "The PDF exceeds 10 MB.");
        return AjaxResult.success(service().importFile(body));
    }
    @PostMapping("/{id}/clean") @PreAuthorize("@ss.hasPermi('simrecrut:resume:edit')")
    public AjaxResult clean(@PathVariable String id, @RequestBody JsonNode body) throws Exception { return AjaxResult.success(service().clean(id, body)); }
    @PostMapping("/{id}/save") @PreAuthorize("@ss.hasPermi('simrecrut:resume:edit')")
    @Log(title = "Resume confirmation", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult save(@PathVariable String id, @RequestBody JsonNode body) throws Exception { return AjaxResult.success(service().save(id, body)); }
    @PostMapping("/{id}/upload") @PreAuthorize("@ss.hasPermi('simrecrut:resume:confirm')")
    @Log(title = "Resume publication", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult upload(@PathVariable String id) throws Exception { return AjaxResult.success(service().upload(id)); }
    @PostMapping("/{id}/cleanup") @PreAuthorize("@ss.hasPermi('simrecrut:resume:confirm')")
    public AjaxResult cleanup(@PathVariable String id) throws Exception { return AjaxResult.success(service().cleanupOriginal(id)); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AjaxResult> invalid(IllegalArgumentException error) { return ResponseEntity.badRequest().body(AjaxResult.error(error.getMessage())); }
}
