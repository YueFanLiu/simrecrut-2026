package fr.isep.simrecrut.service.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;

import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.mapper.identity.*;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!resume-workspace")
public class AdminAccountService {
    public static final Set<String> ADMIN_CORE = Set.of("simrecrut:role:list", "simrecrut:role:edit",
            "simrecrut:permission:list", "simrecrut:user:list", "simrecrut:user:create", "simrecrut:user:edit",
            "simrecrut:user:status", "simrecrut:user:roles");
    private final IdentityAdminMapper db;
    private final UserAccountMapper accounts;
    private final RoleMapper roles;
    private final AccountInputValidator inputs;
    private final PasswordEncoder passwords;
    private final PublicIdGenerator ids;
    private final TransactionTemplate tx;

    public AdminAccountService(IdentityAdminMapper db, UserAccountMapper accounts, RoleMapper roles,
            AccountInputValidator inputs, PasswordEncoder passwords, PublicIdGenerator ids,
            PlatformTransactionManager transactions) {
        this.db = db;
        this.accounts = accounts;
        this.roles = roles;
        this.inputs = inputs;
        this.passwords = passwords;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:list')")
    public UserPage list(int page, int size, String keyword, AccountStatus status, AccountRole role) {
        if (page < 1 || page > 100000 || size < 1 || size > 100 || keyword.length() > 100) {
            throw new IllegalArgumentException("Invalid pagination or search.");
        }
        return tx.execute(ignored -> new UserPage(db.listUsers(keyword.trim(), status, role,
                (page - 1) * size, size).stream().map(this::view).toList(),
                db.countUsers(keyword.trim(), status, role), page, size));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:list')")
    public UserView detail(String userId) { return view(requireUser(db.findPublic(validId(userId)))); }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:create')")
    public UserView create(AccountPrincipal actor, CreateUserRequest body) {
        String username = inputs.normalizeUsername(body.username());
        String email = inputs.normalizeEmail(body.email());
        Set<AccountRole> selected = selectedRoles(body.roles());
        // The generated credential is never returned; mailbox proof is required to set a usable password.
        String hash = passwords.encode(UUID.randomUUID() + ":" + UUID.randomUUID());
        return tx.execute(ignored -> {
            List<Role> catalogue = lockAndAuthorize(actor, "simrecrut:user:create");
            if (accounts.findByUsername(username) != null || accounts.findByEmail(email) != null) {
                throw new AccountConflictException();
            }
            UserAccount account = new UserAccount();
            account.setPublicId(ids.nextId());
            account.setUsername(username);
            account.setEmail(email);
            account.setPasswordHash(hash);
            account.setStatus(AccountStatus.ACTIVE);
            account.setSecurityVersion(0L);
            account.setRevision(1L);
            account.setPasswordUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            account.setCreatedAt(account.getPasswordUpdatedAt());
            account.setUpdatedAt(account.getCreatedAt());
            changed(accounts.insert(account));
            if (account.getId() == null) throw IdentityManagementException.unavailable();
            assign(account.getId(), catalogue, selected);
            audit(actor, "ACCOUNT_CREATED", "ACCOUNT", account.getPublicId());
            return view(account);
        });
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:edit')")
    public UserView update(AccountPrincipal actor, String userId, UpdateUserRequest body) {
        String email = inputs.normalizeEmail(body.email());
        return tx.execute(ignored -> {
            lockAndAuthorize(actor, "simrecrut:user:edit");
            UserAccount account = locked(userId, body.revision());
            if (!email.equals(account.getEmail())) {
                protectAvailableAdmin(account);
                UserAccount duplicate = accounts.findByEmail(email);
                if (duplicate != null && !duplicate.getId().equals(account.getId())) throw new AccountConflictException();
                changed(db.updateEmail(account.getId(), email, body.revision()));
                audit(actor, "ACCOUNT_EMAIL_CHANGED", "ACCOUNT", userId);
            }
            return detailInternal(userId);
        });
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:status')")
    public UserView status(AccountPrincipal actor, String userId, StatusRequest body) {
        return tx.execute(ignored -> {
            lockAndAuthorize(actor, "simrecrut:user:status");
            UserAccount account = locked(userId, body.revision());
            if (body.status() != account.getStatus()) {
                if (body.status() == AccountStatus.DISABLED) {
                    if (account.getId() == actor.userId()) {
                        throw new IdentityManagementException(409, "You cannot disable your own account.");
                    }
                    protectAvailableAdmin(account);
                }
                changed(db.updateStatus(account.getId(), body.status(), body.revision()));
                audit(actor, "ACCOUNT_STATUS_CHANGED", "ACCOUNT", userId);
            }
            return detailInternal(userId);
        });
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:user:roles')")
    public UserView assignRoles(AccountPrincipal actor, String userId, RolesRequest body) {
        Set<AccountRole> selected = selectedRoles(body.roles());
        return tx.execute(ignored -> {
            List<Role> catalogue = lockAndAuthorize(actor, "simrecrut:user:roles");
            UserAccount account = locked(userId, body.revision());
            Set<AccountRole> current = new HashSet<>(roles.findByUserId(account.getId()).stream()
                    .map(Role::getRoleCode).toList());
            if (!current.equals(selected)) {
                if (current.contains(AccountRole.ADMIN) && !selected.contains(AccountRole.ADMIN)) {
                    if (account.getId() == actor.userId()) {
                        throw new IdentityManagementException(409, "You cannot remove your own administrator role.");
                    }
                    protectAvailableAdmin(account);
                }
                changed(db.touchRoles(account.getId(), body.revision()));
                db.deleteUserRoles(account.getId());
                assign(account.getId(), catalogue, selected);
                audit(actor, "ACCOUNT_ROLES_CHANGED", "ACCOUNT", userId);
            }
            return detailInternal(userId);
        });
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:permission:list')")
    public List<PermissionView> permissions() {
        return db.permissions().stream().map(p -> new PermissionView(p.code(), p.name(), p.description(),
                allowedRoles(p.code()), ADMIN_CORE.contains(p.code()))).toList();
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:role:list')")
    public RolePermissionsView rolePermissions(AccountRole code) {
        Role role = roles.findByCode(code);
        if (role == null) throw new IdentityManagementException(404, "Role not found.");
        return new RolePermissionsView(code.name(), role.getRevision(), db.rolePermissions(role.getId()));
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:role:edit')")
    public RolePermissionsView savePermissions(AccountPrincipal actor, AccountRole code, PermissionsRequest body) {
        Set<String> selected = new HashSet<>(body.permissions());
        if (selected.size() != body.permissions().size()) throw new IllegalArgumentException("Duplicate permission.");
        return tx.execute(ignored -> {
            Role role = lockAndAuthorize(actor, "simrecrut:role:edit").stream()
                    .filter(r -> r.getRoleCode() == code).findFirst()
                    .orElseThrow(() -> new IdentityManagementException(404, "Role not found."));
            if (!Objects.equals(role.getRevision(), body.revision())) throw IdentityManagementException.conflict();
            Map<String, IdentityAdminMapper.PermissionRow> catalogue = new HashMap<>();
            db.permissions().forEach(p -> catalogue.put(p.code(), p));
            for (String permission : selected) {
                if (!catalogue.containsKey(permission) || !allowedRoles(permission).contains(code)) {
                    throw new IllegalArgumentException("Unknown permission or permission outside this role's scope.");
                }
            }
            if (code == AccountRole.ADMIN && !selected.containsAll(ADMIN_CORE)) {
                throw new IdentityManagementException(409, "Core administrator permissions cannot be removed.");
            }
            Set<String> current = new HashSet<>(db.rolePermissions(role.getId()));
            if (!current.equals(selected)) {
                changed(db.touchRole(role.getId(), body.revision()));
                db.deleteRolePermissions(role.getId());
                for (String permission : selected.stream().sorted().toList()) {
                    changed(db.insertRolePermission(role.getId(), catalogue.get(permission).id()));
                }
                db.invalidateRoleSessions(role.getId());
                audit(actor, "ROLE_PERMISSIONS_CHANGED", "ROLE", code.name());
            }
            return new RolePermissionsView(code.name(), current.equals(selected) ? body.revision()
                    : body.revision() + 1, selected.stream().sorted().toList());
        });
    }

    private List<Role> lockAndAuthorize(AccountPrincipal actor, String permission) {
        List<Role> catalogue = db.lockRoles();
        if (actor == null) throw new AccessDeniedException("Access denied.");
        UserAccount current = accounts.findById(actor.userId());
        if (current == null || current.getStatus() != AccountStatus.ACTIVE || current.getEmailVerifiedAt() == null
                || !Objects.equals(current.getPublicId(), actor.publicId())
                || !Objects.equals(current.getSecurityVersion(), actor.securityVersion())
                || roles.findByUserId(actor.userId()).stream().noneMatch(r -> r.getRoleCode() == AccountRole.ADMIN)
                || !roles.findPermissionCodesByUserId(actor.userId()).contains(permission)) {
            throw new AccessDeniedException("Access denied.");
        }
        return catalogue;
    }

    private void protectAvailableAdmin(UserAccount account) {
        if (account.getStatus() == AccountStatus.ACTIVE && account.getEmailVerifiedAt() != null
                && roles.findByUserId(account.getId()).stream().anyMatch(r -> r.getRoleCode() == AccountRole.ADMIN)
                && db.countAvailableAdmins() <= 1) {
            throw new IdentityManagementException(409, "The last available administrator must be preserved.");
        }
    }

    private Set<AccountRole> selectedRoles(List<AccountRole> values) {
        if (values == null || values.isEmpty() || values.size() > 4 || values.stream().anyMatch(Objects::isNull)
                || new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("Choose a nonempty set of distinct application roles.");
        }
        return EnumSet.copyOf(values);
    }

    private void assign(long userId, List<Role> catalogue, Set<AccountRole> selected) {
        for (AccountRole code : selected) {
            Role role = catalogue.stream().filter(r -> r.getRoleCode() == code).findFirst()
                    .orElseThrow(IdentityManagementException::unavailable);
            changed(roles.insertUserRole(userId, role.getId()));
        }
    }

    private UserAccount locked(String id, long revision) {
        UserAccount account = requireUser(db.lockUser(validId(id)));
        if (!Objects.equals(account.getRevision(), revision)) throw IdentityManagementException.conflict();
        return account;
    }
    private UserView detailInternal(String id) { return view(requireUser(db.findPublic(id))); }
    private UserAccount requireUser(UserAccount account) {
        if (account == null) throw new IdentityManagementException(404, "Account not found.");
        return account;
    }
    private String validId(String value) {
        if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid account public ID.");
        }
        return value;
    }
    private UserView view(UserAccount account) {
        return new UserView(account.getPublicId(), account.getUsername(), account.getEmail(), account.getStatus(),
                account.getEmailVerifiedAt() != null, account.getEmailVerifiedAt() == null,
                roles.findByUserId(account.getId()).stream().map(r -> r.getRoleCode().name()).sorted().toList(),
                account.getRevision(), account.getCreatedAt(), account.getUpdatedAt());
    }
    private void changed(int rows) {
        if (rows != 1) throw IdentityManagementException.conflict();
    }
    private void audit(AccountPrincipal actor, String action, String type, String reference) {
        changed(db.audit(actor.userId(), action, type, referenceHash(reference)));
    }
    public static String referenceHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public static List<AccountRole> allowedRoles(String permission) {
        if (permission.startsWith("simrecrut:resume:") || permission.startsWith("simrecrut:case:")
                || permission.equals("simrecrut:job:browse")) {
            return List.of(AccountRole.CANDIDATE);
        }
        if (permission.startsWith("simrecrut:job:")) return List.of(AccountRole.HR);
        if (permission.startsWith("simrecrut:research:")) return List.of(AccountRole.RESEARCHER);
        if (permission.startsWith("simrecrut:review:")) return List.of(AccountRole.HR, AccountRole.RESEARCHER);
        return List.of(AccountRole.ADMIN);
    }
}
