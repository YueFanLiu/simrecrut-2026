package fr.isep.simrecrut.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.isep.simrecrut.config.PasswordConfig;
import fr.isep.simrecrut.entity.identity.AccountStatus;
import fr.isep.simrecrut.entity.identity.Role;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import fr.isep.simrecrut.security.authentication.PasswordAuthenticationService;
import fr.isep.simrecrut.service.identity.AccountConflictException;
import fr.isep.simrecrut.service.identity.AccountInputValidator;
import fr.isep.simrecrut.service.identity.AccountService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountFoundationTest {

    private static final ValidatorFactory VALIDATION =
        Validation.buildDefaultValidatorFactory();

    private final AccountInputValidator inputs =
        new AccountInputValidator(VALIDATION.getValidator());

    @AfterAll
    static void closeValidation() {
        VALIDATION.close();
    }

    @Test
    void hashesWithRandomSaltAndRejectsWrongPassword() {
        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
        String password = "A long test password 2026";

        String first = encoder.encode(password);
        String second = encoder.encode(password);

        assertNotEquals(password, first);
        assertNotEquals(first, second);
        assertTrue(encoder.matches(password, first));
        assertFalse(encoder.matches("A different password", first));
        assertTrue(first.matches("\\{pbkdf2-v1\\}[0-9a-f]{96}"));
    }

    @Test
    void normalizesIdentifiersAndValidatesNewPasswords() {
        assertEquals("alex", inputs.normalizeUsername(" Alex "));
        assertEquals("alex@example.com",
            inputs.normalizeEmail(" Alex@Example.com "));

        assertThrows(IllegalArgumentException.class,
            () -> inputs.normalizeUsername("invalid-name"));

        assertThrows(IllegalArgumentException.class,
            () -> inputs.normalizeEmail("not-an-email"));

        assertThrows(IllegalArgumentException.class,
            () -> inputs.validateNewPassword("short", "short"));

        assertThrows(IllegalArgumentException.class,
            () -> inputs.validateNewPassword(
                "A long test password", "A different password"));

        assertDoesNotThrow(() -> inputs.validateNewPassword(
            " A long test password ", " A long test password "));
    }

    @Test
    void disabledAccountCannotAuthenticateWithCorrectPassword() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(encoder.encode(anyString())).thenReturn("dummy");
        when(encoder.matches("correct-password", "stored-hash"))
            .thenReturn(true);

        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setPasswordHash("stored-hash");
        account.setStatus(AccountStatus.DISABLED);
        account.setEmailVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));

        when(accounts.findByUsername("alex")).thenReturn(account);

        PasswordAuthenticationService service =
            new PasswordAuthenticationService(accounts, inputs, encoder);

        assertThrows(BadCredentialsException.class,
            () -> service.verify("alex", "correct-password"));
    }

    @Test
    void translatesConcurrentUniqueConstraintFailure() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        RoleMapper roles = mock(RoleMapper.class);

        Role role = new Role();
        role.setId(1L);
        role.setRoleCode(AccountRole.CANDIDATE);

        when(roles.findByCode(AccountRole.CANDIDATE)).thenReturn(role);
        when(accounts.insert(any(UserAccount.class)))
            .thenThrow(new DuplicateKeyException("Duplicate test record"));

        AccountService service = new AccountService(accounts, roles, inputs);

        assertThrows(AccountConflictException.class,
            () -> service.createVerifiedAccount(
                "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "alex",
                "alex@example.com",
                "{pbkdf2-v1}" + "0".repeat(96),
                AccountRole.CANDIDATE,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));

        verify(roles, never()).insertUserRole(anyLong(), anyLong());
    }

    @Test
    void publicAccountCreationRejectsPrivilegedRoles() {
        AccountService service = new AccountService(
            mock(UserAccountMapper.class),
            mock(RoleMapper.class),
            inputs);

        assertThrows(IllegalArgumentException.class,
            () -> service.createVerifiedAccount(
                "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "alex",
                "alex@example.com",
                "{pbkdf2-v1}" + "0".repeat(96),
                AccountRole.ADMIN,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }
}
