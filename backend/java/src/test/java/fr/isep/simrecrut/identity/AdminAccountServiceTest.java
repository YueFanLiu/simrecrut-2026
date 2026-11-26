package fr.isep.simrecrut.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.mapper.identity.*;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.security.authentication.JwtSessionAuthenticationConverter;
import fr.isep.simrecrut.security.authentication.RedisSessionStore;
import fr.isep.simrecrut.service.identity.*;
import jakarta.validation.Validation;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AdminAccountServiceTest {
    final IdentityAdminMapper db = mock(IdentityAdminMapper.class);
    final UserAccountMapper accounts = mock(UserAccountMapper.class);
    final RoleMapper roles = mock(RoleMapper.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final AccountPrincipal actor = new AccountPrincipal(1, "01AAAAAAAAAAAAAAAAAAAAAAAA",
            UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(600));
    AdminAccountService service;
    List<Role> catalogue;
    UserAccount target;

    @BeforeEach void setup() {
        var inputs = new AccountInputValidator(Validation.buildDefaultValidatorFactory().getValidator());
        service = new AdminAccountService(db, accounts, roles, inputs, encoder, new PublicIdGenerator(), transactions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        catalogue = Arrays.stream(AccountRole.values()).map(code -> {
            Role role = new Role(); role.setId((long) code.ordinal() + 1); role.setRoleCode(code); role.setRevision(1L);
            return role;
        }).toList();
        when(db.lockRoles()).thenReturn(catalogue);
        UserAccount admin = account(1, actor.publicId());
        when(accounts.findById(1)).thenReturn(admin);
        when(roles.findByUserId(1)).thenReturn(List.of(role(AccountRole.ADMIN)));
        when(roles.findPermissionCodesByUserId(1)).thenReturn(new ArrayList<>(AdminAccountService.ADMIN_CORE));
        target = account(2, "01BBBBBBBBBBBBBBBBBBBBBBBB");
        when(db.lockUser(target.getPublicId())).thenReturn(target);
        when(db.findPublic(target.getPublicId())).thenReturn(target);
        when(roles.findByUserId(2)).thenReturn(List.of(role(AccountRole.CANDIDATE)));
        when(db.audit(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(db.updateStatus(anyLong(), any(), anyLong())).thenReturn(1);
        when(db.touchRoles(anyLong(), anyLong())).thenReturn(1);
        when(roles.insertUserRole(anyLong(), anyLong())).thenReturn(1);
        when(db.touchRole(anyLong(), anyLong())).thenReturn(1);
        when(db.insertRolePermission(anyLong(), anyLong())).thenReturn(1);
    }

    @Test void createsUnverifiedAccountWithoutExposingGeneratedCredential() throws Exception {
        when(encoder.encode(anyString())).thenReturn("private-hash");
        when(accounts.insert(any())).thenAnswer(call -> { ((UserAccount) call.getArgument(0)).setId(3L); return 1; });
        when(roles.findByUserId(3)).thenReturn(List.of(role(AccountRole.RESEARCHER)));
        UserView created = service.create(actor, new CreateUserRequest(" Research_1 ", "ONE@example.org",
                List.of(AccountRole.RESEARCHER)));
        assertEquals("research_1", created.username());
        assertEquals("one@example.org", created.email());
        assertFalse(created.emailVerified());
        assertTrue(created.passwordSetupRequired());
        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(created);
        assertFalse(serialized.contains("private-hash"));
        assertFalse(serialized.contains("securityVersion"));
        verify(roles).insertUserRole(3, role(AccountRole.RESEARCHER).getId());
        verify(transactions).commit(any());
    }

    @Test void duplicateAccountRollsBackWithoutRoleAssignment() {
        when(accounts.findByEmail("target@example.org")).thenReturn(target);
        assertThrows(AccountConflictException.class, () -> service.create(actor,
                new CreateUserRequest("new_user", "target@example.org", List.of(AccountRole.HR))));
        verify(accounts, never()).insert(any());
        verify(transactions).rollback(any());
    }

    @Test void staleRevisionCannotChangeStatus() {
        assertThrows(IdentityManagementException.class, () -> service.status(actor, target.getPublicId(),
                new StatusRequest(AccountStatus.DISABLED, 2L)));
        verify(db, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test void disabledOrRevokedActingAdminCannotWriteUsingOldAuthentication() {
        when(roles.findPermissionCodesByUserId(1)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> service.status(actor, target.getPublicId(),
                new StatusRequest(AccountStatus.DISABLED, 1L)));
        verify(db, never()).lockUser(anyString());
    }

    @Test void cannotDisableSelf() {
        target = account(1, actor.publicId());
        when(db.lockUser(actor.publicId())).thenReturn(target);
        assertThrows(IdentityManagementException.class, () -> service.status(actor, actor.publicId(),
                new StatusRequest(AccountStatus.DISABLED, 1L)));
        verify(db, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test void lastAvailableAdminCannotBeDisabledDemotedOrLoseMailboxVerification() {
        when(roles.findByUserId(2)).thenReturn(List.of(role(AccountRole.ADMIN)));
        when(db.countAvailableAdmins()).thenReturn(1L);
        assertThrows(IdentityManagementException.class, () -> service.status(actor, target.getPublicId(),
                new StatusRequest(AccountStatus.DISABLED, 1L)));
        assertThrows(IdentityManagementException.class, () -> service.assignRoles(actor, target.getPublicId(),
                new RolesRequest(List.of(AccountRole.HR), 1L)));
        assertThrows(IdentityManagementException.class, () -> service.update(actor, target.getPublicId(),
                new UpdateUserRequest("new@example.org", 1L)));
        verify(db, never()).touchRoles(anyLong(), anyLong());
        verify(db, never()).updateEmail(anyLong(), anyString(), anyLong());
    }

    @Test void disablingOrdinaryAccountInvalidatesVersionAndRecordsContentFreeAudit() {
        service.status(actor, target.getPublicId(), new StatusRequest(AccountStatus.DISABLED, 1L));
        verify(db).updateStatus(2, AccountStatus.DISABLED, 1);
        verify(db).audit(eq(1L), eq("ACCOUNT_STATUS_CHANGED"), eq("ACCOUNT"), matches("[a-f0-9]{64}"));
    }

    @Test void repeatedNoOpDoesNotInvalidateSessions() {
        service.status(actor, target.getPublicId(), new StatusRequest(AccountStatus.ACTIVE, 1L));
        verify(db, never()).updateStatus(anyLong(), any(), anyLong());
        verify(db, never()).audit(anyLong(), anyString(), anyString(), anyString());
    }

    @Test void roleReplacementBumpsVersionAndReplacesWholeSet() {
        service.assignRoles(actor, target.getPublicId(), new RolesRequest(List.of(AccountRole.HR), 1L));
        var order = inOrder(db, roles);
        order.verify(db).touchRoles(2, 1);
        order.verify(db).deleteUserRoles(2);
        order.verify(roles).insertUserRole(2, role(AccountRole.HR).getId());
    }

    @Test void unknownOrCrossRolePermissionsAreRejected() {
        when(db.permissions()).thenReturn(List.of(new IdentityAdminMapper.PermissionRow(1,
                "simrecrut:resume:list", "Read resumes", "Own resumes.")));
        assertThrows(IllegalArgumentException.class, () -> service.savePermissions(actor, AccountRole.HR,
                new PermissionsRequest(List.of("simrecrut:resume:list"), 1L)));
        assertThrows(IllegalArgumentException.class, () -> service.savePermissions(actor, AccountRole.HR,
                new PermissionsRequest(List.of("*:*:*"), 1L)));
        verify(db, never()).deleteRolePermissions(anyLong());
    }

    @Test void coreAdminPermissionsCannotBeRemoved() {
        when(db.permissions()).thenReturn(List.of());
        assertThrows(IdentityManagementException.class, () -> service.savePermissions(actor, AccountRole.ADMIN,
                new PermissionsRequest(List.of(), 1L)));
        verify(db, never()).touchRole(anyLong(), anyLong());
    }

    @Test void permissionChangeInvalidatesAffectedAccountsAndChecksRevision() {
        when(db.permissions()).thenReturn(List.of(new IdentityAdminMapper.PermissionRow(5,
                "simrecrut:job:list", "Read jobs", "Managed jobs.")));
        when(db.rolePermissions(anyLong())).thenReturn(List.of());
        RolePermissionsView updated = service.savePermissions(actor, AccountRole.HR,
                new PermissionsRequest(List.of("simrecrut:job:list"), 1L));
        assertEquals(2, updated.revision());
        verify(db).invalidateRoleSessions(role(AccountRole.HR).getId());
        assertThrows(IdentityManagementException.class, () -> service.savePermissions(actor, AccountRole.HR,
                new PermissionsRequest(List.of(), 99L)));
    }

    @Test void duplicateRolesAndInvalidPaginationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.assignRoles(actor, target.getPublicId(),
                new RolesRequest(List.of(AccountRole.HR, AccountRole.HR), 1L)));
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 20, "", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(1, 101, "", null, null));
    }

    @Test void parsesAllIdentityMapperStatementsAndStrictRequests() throws Exception {
        var configuration = new org.apache.ibatis.session.Configuration();
        for (String mapper : List.of("UserAccountMapper", "RoleMapper", "IdentityAdminMapper")) {
            String resource = "mapper/identity/" + mapper + ".xml";
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        assertTrue(configuration.hasStatement(IdentityAdminMapper.class.getName() + ".resetPassword"));
        assertThrows(Exception.class, () -> new ObjectMapper().readValue(
                "{\"email\":\"one@example.org\",\"revision\":1,\"ownerId\":1}", UpdateUserRequest.class));
    }

    @Test void oldRedisSessionCannotAuthorizeAfterDatabaseSecurityVersionChanges() {
        RedisSessionStore sessions = mock(RedisSessionStore.class);
        Instant expiry = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        when(sessions.load(actor.sessionId())).thenReturn(new RedisSessionStore.Session(1,
                actor.publicId(), 0, expiry.getEpochSecond()));
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(actor.publicId())
                .jti(actor.sessionId()).claim("ver", 0).expiresAt(expiry).build();
        var converter = new JwtSessionAuthenticationConverter(sessions, accounts, roles);
        assertNotNull(converter.convert(token));
        accounts.findById(1).setSecurityVersion(1L);
        assertThrows(BadCredentialsException.class, () -> converter.convert(token));
    }

    private Role role(AccountRole code) { return catalogue.stream().filter(r -> r.getRoleCode() == code).findFirst().orElseThrow(); }
    private UserAccount account(long id, String publicId) {
        UserAccount value = new UserAccount(); value.setId(id); value.setPublicId(publicId);
        value.setUsername("account" + id); value.setEmail("target@example.org"); value.setRevision(1L);
        value.setStatus(AccountStatus.ACTIVE); value.setSecurityVersion(0L);
        value.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC)); return value;
    }
}
