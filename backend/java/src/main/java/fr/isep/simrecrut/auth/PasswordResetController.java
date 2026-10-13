package fr.isep.simrecrut.auth;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Do not log reset payloads, SMTP credentials or verification codes. */
@Anonymous
@RestController
@RequestMapping("/simrecrut/account/password")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service=service; }
    @GetMapping("/options") public AjaxResult options() { return AjaxResult.success().put("emailEnabled",service.available()); }
    @PostMapping("/code") public AjaxResult code(@RequestBody PasswordResetService.Challenge body, HttpServletRequest request) {
        String id=service.request(body,request.getRemoteAddr());
        return AjaxResult.success("If this email belongs to an active account, a verification code has been sent.").put("requestId",id);
    }
    @PostMapping("/reset") public AjaxResult reset(@RequestBody PasswordResetService.Reset body) {
        service.reset(body); return AjaxResult.success("Password reset. Sign in again with your new password.");
    }
}
