package fr.isep.simrecrut.auth;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.SysRegisterService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persist public accounts with a server-selected Candidate role and RuoYi credentials. */
@Service
public class CandidateAccountService {
    private final ISysUserService users;
    private final ISysConfigService config;
    private final SysRegisterService registration;
    private final SysLoginService login;
    private final JdbcTemplate database;

    public CandidateAccountService(ISysUserService users, ISysConfigService config,
            SysRegisterService registration, SysLoginService login, JdbcTemplate database) {
        this.users = users; this.config = config; this.registration = registration;
        this.login = login; this.database = database;
    }

    public record Registration(String username, String email, String password,
            String confirmation, boolean agreedToTerms, String code, String uuid) {}

    public boolean registrationAvailable() {
        return candidateRoles().size() == 1;
    }
    private List<Long> candidateRoles() {
        return database.queryForList("SELECT role_id FROM sys_role WHERE role_key = ? AND status = '0' AND del_flag = '0'", Long.class, "candidate");
    }

    // Serialize registrations in this application; insertUser commits the account and role together.
    public synchronized void register(Registration body) {
        String username = body.username() == null ? "" : body.username().trim();
        String email = body.email() == null ? "" : body.email().trim();
        require(username.matches("[A-Za-z0-9_]{3,20}"), "Use a username of 3-20 letters, numbers or underscores.");
        require(email.length() <= 100 && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Enter a valid email address.");
        String password = body.password();
        require(password != null && password.length() >= 8 && password.length() <= 20
                && password.matches("(?s).*[A-Za-z].*") && password.matches("(?s).*[0-9].*"),
                "Use 8-20 password characters including letters and numbers.");
        require(password.equals(body.confirmation()), "Passwords do not match.");
        require(body.agreedToTerms(), "Read the registration and privacy notices before continuing.");
        if (config.selectCaptchaEnabled()) registration.validateCaptcha(username,
                body.code() == null ? "" : body.code(), body.uuid());

        SysUser user = new SysUser();
        user.setUserName(username); user.setNickName(username); user.setEmail(email);
        require(users.checkUserNameUnique(user), "This username is already registered.");
        require(users.checkEmailUnique(user), "This email is already registered.");
        List<Long> roles = candidateRoles();
        require(roles.size() == 1, "Candidate registration is unavailable. Contact the administrator.");
        user.setRoleIds(new Long[] { roles.get(0) });
        user.setStatus("0"); user.setDelFlag("0"); user.setCreateBy("self-registration");
        user.setPwdUpdateDate(new java.util.Date());
        user.setPassword(SecurityUtils.encryptPassword(password));
        require(users.insertUser(user) == 1, "Account creation failed. Please retry.");
    }

    public String login(String identifier, String password, String code, String uuid) {
        String username = identifier == null ? "" : identifier.trim();
        require(username.length() <= 100, "Invalid username/email or password.");
        if (username.contains("@")) {
            List<String> names = database.queryForList("SELECT user_name FROM sys_user WHERE email = ? AND del_flag = '0' LIMIT 2", String.class, username);
            if (names.size() != 1 && config.selectCaptchaEnabled())
                login.validateCaptcha(username, code == null ? "" : code, uuid);
            require(names.size() == 1, "Invalid username/email or password.");
            username = names.get(0);
        }
        return login.login(username, password, code == null ? "" : code, uuid);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new ServiceException(message);
    }
}
