package fr.isep.simrecrut.auth;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reset only the active account proved by its mailbox, without changing roles. */
@Service
public class PasswordResetService {
    private final PasswordResetCodes codes;
    private final PasswordResetMail mail;
    private final JdbcTemplate database;
    private final ISysConfigService config;
    private final SysLoginService login;
    private final ISysUserService users;
    private final RedisCache sessions;
    private final String secret;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();
    public PasswordResetService(PasswordResetCodes codes, PasswordResetMail mail, JdbcTemplate database,
            ISysConfigService config, SysLoginService login, ISysUserService users, RedisCache sessions,
            @Value("${token.secret}") String secret) {
        this.codes=codes; this.mail=mail; this.database=database; this.config=config;
        this.login=login; this.users=users; this.sessions=sessions; this.secret=secret;
    }
    public record Challenge(String email, String code, String uuid) {}
    public record Reset(String email, String requestId, String emailCode, String password, String confirmation) {}
    public boolean available() { return mail.available(); }
    public String request(Challenge body, String ip) {
        String email = email(body.email());
        require(available(), "Email verification is not configured. Contact the administrator.");
        if (config.selectCaptchaEnabled()) login.validateCaptcha("password-reset", body.code()==null?"":body.code(), body.uuid());
        require(codes.allow(digest(email), digest(ip)), "Please wait before requesting another code.");
        String id = UUID.randomUUID().toString();
        List<Long> ids = database.queryForList("SELECT user_id FROM sys_user WHERE email = ? AND status = '0' AND del_flag = '0' LIMIT 2", Long.class, email);
        // The same response is returned when no unique active account owns this address.
        if (ids.size()!=1) return id;
        var user = users.selectUserById(ids.get(0));
        require(user!=null, "Email verification is unavailable. Please retry.");
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        try {
            String account = json.writeValueAsString(Map.of("userId",user.getUserId(),"password",user.getPassword()));
            codes.store(id, digest(email+"|"+id+"|"+code), account);
            mail.send(email, code);
        } catch (Exception failure) {
            codes.discard(id);
            throw new ServiceException("The verification email could not be sent. Please retry later.");
        }
        return id;
    }
    public void reset(Reset body) {
        String email = email(body.email());
        require(body.requestId()!=null && body.requestId().matches("[a-f0-9-]{36}"), "Request a new email code.");
        String password = body.password();
        require(password!=null && password.length()>=8 && password.length()<=20
                && password.matches("(?s).*[A-Za-z].*") && password.matches("(?s).*[0-9].*"), "Use 8-20 password characters including letters and numbers.");
        require(password.equals(body.confirmation()), "Passwords do not match.");
        // Malformed guesses also consume an attempt, preventing unlimited code probing.
        String code = body.emailCode()==null ? "" : body.emailCode();
        String account = codes.consume(body.requestId(), digest(email+"|"+body.requestId()+"|"+code));
        require(account!=null && !account.isBlank(), "The email code is incorrect, expired or already used.");
        try {
            var stored = json.readTree(account);
            long id = stored.path("userId").asLong();
            int changed = database.update("UPDATE sys_user SET password = ?, pwd_update_date = NOW(), update_time = NOW() WHERE user_id = ? AND email = ? AND status = '0' AND del_flag = '0' AND password = ?",
                    SecurityUtils.encryptPassword(password), id, email, stored.path("password").asText());
            require(changed==1, "The account changed. Request a new code and retry.");
            for (String key : sessions.keys("login_tokens:*")) {
                LoginUser session = sessions.getCacheObject(key);
                if (session!=null && Objects.equals(session.getUserId(),id)) sessions.deleteObject(key);
            }
        } catch (ServiceException failure) { throw failure; }
        catch (Exception failure) { throw new ServiceException("Reset could not finish. Request a new code before retrying."); }
    }
    private String digest(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("Verification configuration is invalid."); }
    }
    private static String email(String value) {
        String email=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        require(email.length()<=100 && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Enter a valid email address.");
        return email;
    }
    private static void require(boolean valid,String message) { if (!valid) throw new ServiceException(message); }
}
