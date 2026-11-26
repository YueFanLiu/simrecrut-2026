package fr.isep.simrecrut.identity;

import static fr.isep.simrecrut.dto.identity.AccountManagementDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.application.identity.PasswordResetApplicationService;
import fr.isep.simrecrut.auth.*;
import fr.isep.simrecrut.config.properties.RegistrationProperties;
import fr.isep.simrecrut.entity.identity.*;
import fr.isep.simrecrut.mapper.identity.*;
import fr.isep.simrecrut.service.identity.*;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PasswordResetApplicationServiceTest {
    final PasswordResetCodes codes = mock(PasswordResetCodes.class);
    final PasswordResetMail mail = mock(PasswordResetMail.class);
    final UserAccountMapper accounts = mock(UserAccountMapper.class);
    final IdentityAdminMapper db = mock(IdentityAdminMapper.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final ObjectMapper json = new ObjectMapper();
    PasswordResetApplicationService service;
    UserAccount account;
    String challenge, deliveredCode, digest, snapshot;

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mail.available()).thenReturn(true);
        when(codes.allow(anyString(), anyString())).thenReturn(true);
        when(codes.allowSubmission(anyString())).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("new-supported-hash");
        when(db.resetPassword(anyLong(), anyString(), anyLong(), anyString())).thenReturn(1);
        when(db.audit(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        service = new PasswordResetApplicationService(codes, mail, accounts, db,
                new AccountInputValidator(Validation.buildDefaultValidatorFactory().getValidator()), encoder, json,
                transactions, new RegistrationProperties(Duration.ofMinutes(10), Duration.ofSeconds(60),
                        5, 5, 30, 500, 60, Base64.getEncoder().encodeToString(new byte[32])));
        account = new UserAccount(); account.setId(7L); account.setPublicId("01AAAAAAAAAAAAAAAAAAAAAAAA");
        account.setEmail("one@example.org"); account.setStatus(AccountStatus.ACTIVE); account.setSecurityVersion(4L);
        when(accounts.findByEmail("one@example.org")).thenReturn(account);
        doAnswer(call -> {
            challenge = call.getArgument(0); digest = call.getArgument(1); snapshot = call.getArgument(2); return null;
        }).when(codes).store(anyString(), anyString(), anyString());
        doAnswer(call -> { deliveredCode = call.getArgument(1); return null; }).when(mail).send(anyString(), anyString());
        when(codes.consume(anyString(), anyString())).thenAnswer(call ->
                Objects.equals(challenge, call.getArgument(0)) && Objects.equals(digest, call.getArgument(1)) ? snapshot : "");
    }

    @Test void sendsBoundCodeWithoutReturningCodeOrPasswordSnapshot() {
        var response = service.requestCode(new ResetCodeRequest(" ONE@example.org "), "127.0.0.1");
        assertEquals(challenge, response.challengeId());
        assertEquals(600, response.expiresInSeconds());
        assertTrue(deliveredCode.matches("[0-9]{6}"));
        assertTrue(digest.matches("[a-f0-9]{64}"));
        assertFalse(snapshot.contains("passwordHash"));
        assertEquals(4, json.valueToTree(readSnapshot()).path("securityVersion").asInt());
    }

    @Test void unknownAndDisabledAccountsReceiveGenericChallengeWithoutMail() {
        var unknown = service.requestCode(new ResetCodeRequest("unknown@example.org"), "127.0.0.1");
        account.setStatus(AccountStatus.DISABLED);
        var disabled = service.requestCode(new ResetCodeRequest("one@example.org"), "127.0.0.1");
        assertEquals(unknown.expiresInSeconds(), disabled.expiresInSeconds());
        assertNotEquals(unknown.challengeId(), disabled.challengeId());
        verify(mail, never()).send(anyString(), anyString());
        verify(codes, never()).store(anyString(), anyString(), anyString());
    }

    @Test void requestRateLimitPreventsAccountLookupAndMail() {
        when(codes.allow(anyString(), anyString())).thenReturn(false);
        assertEquals(429, assertThrows(IdentityManagementException.class,
                () -> service.requestCode(new ResetCodeRequest("one@example.org"), "127.0.0.1")).status());
        verifyNoInteractions(accounts);
    }

    @Test void missingMailConfigurationIsUnavailable() {
        when(mail.available()).thenReturn(false);
        assertFalse(service.options().emailEnabled());
        assertEquals(503, assertThrows(IdentityManagementException.class,
                () -> service.requestCode(new ResetCodeRequest("one@example.org"), "127.0.0.1")).status());
    }

    @Test void mailFailureDiscardsUnsentChallenge() {
        doThrow(new MailSendException("private SMTP diagnostic")).when(mail).send(anyString(), anyString());
        assertThrows(IdentityManagementException.class,
                () -> service.requestCode(new ResetCodeRequest("one@example.org"), "127.0.0.1"));
        verify(codes).discard(challenge);
    }

    @Test void mailboxProofSetsPasswordAndChecksFrozenSecurityVersion() {
        request();
        assertTrue(service.reset(body("one@example.org", deliveredCode), "127.0.0.1").passwordUpdated());
        verify(db).resetPassword(7, "one@example.org", 4, "new-supported-hash");
        verify(db).audit(eq(7L), eq("PASSWORD_RESET"), eq("ACCOUNT"), matches("[a-f0-9]{64}"));
        verify(transactions).commit(any());
    }

    @Test void wrongEmailOrCodeCannotReuseMailboxProof() {
        request();
        assertThrows(IdentityManagementException.class,
                () -> service.reset(body("other@example.org", deliveredCode), "127.0.0.1"));
        String wrong = deliveredCode.equals("000000") ? "111111" : "000000";
        assertThrows(IdentityManagementException.class,
                () -> service.reset(body("one@example.org", wrong), "127.0.0.1"));
        verify(db, never()).resetPassword(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test void concurrentPasswordEmailRoleOrStatusChangeRejectsOutstandingProof() {
        request();
        when(db.resetPassword(anyLong(), anyString(), anyLong(), anyString())).thenReturn(0);
        assertThrows(IdentityManagementException.class,
                () -> service.reset(body("one@example.org", deliveredCode), "127.0.0.1"));
        verify(transactions).rollback(any());
        verify(db, never()).audit(anyLong(), anyString(), anyString(), anyString());
    }

    @Test void submissionRateLimitAndPasswordValidationPrecedeConsumption() {
        when(codes.allowSubmission(anyString())).thenReturn(false);
        request();
        assertThrows(IdentityManagementException.class,
                () -> service.reset(body("one@example.org", deliveredCode), "127.0.0.1"));
        when(codes.allowSubmission(anyString())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.reset(new ResetRequest("one@example.org",
                challenge, deliveredCode, "short", "short"), "127.0.0.1"));
        verify(codes, never()).consume(anyString(), anyString());
    }

    @Test void consumedOrExpiredChallengeCannotUpdatePassword() {
        request();
        when(codes.consume(anyString(), anyString())).thenReturn("");
        assertThrows(IdentityManagementException.class,
                () -> service.reset(body("one@example.org", deliveredCode), "127.0.0.1"));
        verify(db, never()).resetPassword(anyLong(), anyString(), anyLong(), anyString());
    }

    private void request() { service.requestCode(new ResetCodeRequest("one@example.org"), "127.0.0.1"); }
    private ResetRequest body(String email, String code) {
        return new ResetRequest(email, challenge, code, "a sufficiently long password", "a sufficiently long password");
    }
    private PasswordResetApplicationService.ResetSnapshot readSnapshot() {
        try { return json.readValue(snapshot, PasswordResetApplicationService.ResetSnapshot.class); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
