package fr.isep.simrecrut.service.identity;

import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.identity.AccountStatus;
import fr.isep.simrecrut.entity.identity.Role;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserAccountMapper accounts;
    private final RoleMapper roles;
    private final AccountInputValidator inputs;

    public AccountService(
        UserAccountMapper accounts,
        RoleMapper roles,
        AccountInputValidator inputs) {
        this.accounts = accounts;
        this.roles = roles;
        this.inputs = inputs;
    }

    /**
     * Persist a public account after trusted email verification.
     * The caller supplies a generated public ID and an already encoded password.
     * Never bind these arguments directly from an HTTP registration request.
     */
    @Transactional
    public long createVerifiedAccount(
        String publicId,
        String username,
        String email,
        String passwordHash,
        AccountRole roleCode,
        LocalDateTime emailVerifiedAt) {

        if (publicId == null
            || !publicId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid account public ID.");
        }

        if (passwordHash == null
            || !passwordHash.matches("\\{pbkdf2-v1\\}[0-9a-f]{96}")) {
            throw new IllegalArgumentException("A supported password hash is required.");
        }

        if (roleCode != AccountRole.CANDIDATE && roleCode != AccountRole.HR) {
            throw new IllegalArgumentException(
                "Public registration supports Candidate and HR accounts only.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (emailVerifiedAt == null || emailVerifiedAt.isAfter(now)) {
            throw new IllegalArgumentException(
                "A valid email verification time is required.");
        }

        String normalizedUsername = inputs.normalizeUsername(username);
        String normalizedEmail = inputs.normalizeEmail(email);

        if (accounts.findByUsername(normalizedUsername) != null
            || accounts.findByEmail(normalizedEmail) != null) {
            throw new AccountConflictException();
        }

        Role role = roles.findByCode(roleCode);

        if (role == null) {
            throw new IllegalStateException("The requested account role is unavailable.");
        }

        UserAccount account = new UserAccount();
        account.setPublicId(publicId);
        account.setUsername(normalizedUsername);
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordHash);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmailVerifiedAt(emailVerifiedAt);
        account.setPasswordUpdatedAt(now);
        account.setSecurityVersion(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        try {
            if (accounts.insert(account) != 1 || account.getId() == null) {
                throw new IllegalStateException("Account creation failed.");
            }

            if (roles.insertUserRole(account.getId(), role.getId()) != 1) {
                throw new IllegalStateException("Account role assignment failed.");
            }
        } catch (DuplicateKeyException exception) {
            throw new AccountConflictException();
        }

        return account.getId();
    }
}
