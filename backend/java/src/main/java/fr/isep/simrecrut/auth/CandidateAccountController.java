package fr.isep.simrecrut.auth;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Public identity adapter; request bodies and credentials are never audit payloads. */
@Anonymous
@RestController
@RequestMapping("/simrecrut/account")
public class CandidateAccountController {
    private final CandidateAccountService accounts;
    private final boolean enabled;
    public CandidateAccountController(CandidateAccountService accounts,
            @Value("${simrecrut.registration.enabled:true}") boolean enabled) {
        this.accounts = accounts; this.enabled = enabled;
    }
    @GetMapping("/options")
    public AjaxResult options() { return AjaxResult.success().put("registrationEnabled", enabled && accounts.registrationAvailable()); }

    @PostMapping("/register")
    public AjaxResult register(@RequestBody CandidateAccountService.Registration body) {
        if (!enabled) throw new ServiceException("Registration is currently unavailable.");
        accounts.register(body);
        return AjaxResult.success("Account created. Sign in with your username or email.");
    }
    public record Login(String username, String password, String code, String uuid) {}
    @PostMapping("/login")
    public AjaxResult login(@RequestBody Login body) {
        return AjaxResult.success().put("token", accounts.login(body.username(), body.password(), body.code(), body.uuid()));
    }
}
