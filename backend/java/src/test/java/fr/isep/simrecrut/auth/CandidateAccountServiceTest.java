package fr.isep.simrecrut.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.SysRegisterService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verify account persistence and privilege boundaries without changing the shared database. */
class CandidateAccountServiceTest {
    final ISysUserService users = mock(ISysUserService.class);
    final ISysConfigService config = mock(ISysConfigService.class);
    final SysRegisterService captcha = mock(SysRegisterService.class);
    final SysLoginService login = mock(SysLoginService.class);
    final JdbcTemplate database = mock(JdbcTemplate.class);
    final CandidateAccountService service = new CandidateAccountService(users, config, captcha, login, database);
    CandidateAccountService.Registration form(String password) {
        return new CandidateAccountService.Registration("candidate_test", "test@example.org", password, password, true, "challenge", "uuid");
    }
    void validSetup() {
        when(config.selectCaptchaEnabled()).thenReturn(true);
        when(users.checkUserNameUnique(any())).thenReturn(true);
        when(users.checkEmailUnique(any())).thenReturn(true);
        when(database.queryForList(anyString(), eq(Long.class), eq("candidate"))).thenReturn(List.of(9L));
        when(users.insertUser(any())).thenReturn(1);
    }
    @Test void storesOnlyCandidateRoleAndHashedPassword() {
        validSetup(); service.register(form("ExamplePass7"));
        verify(captcha).validateCaptcha("candidate_test", "challenge", "uuid");
        ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
        verify(users).insertUser(saved.capture());
        assertArrayEquals(new Long[]{9L}, saved.getValue().getRoleIds());
        assertEquals("test@example.org", saved.getValue().getEmail());
        assertTrue(SecurityUtils.matchesPassword("ExamplePass7", saved.getValue().getPassword()));
        assertNotEquals("ExamplePass7", saved.getValue().getPassword());
    }
    @Test void rejectsCaptchaFailureBeforeWriting() {
        validSetup(); doThrow(new ServiceException("Invalid verification code")).when(captcha).validateCaptcha(any(), any(), any());
        assertThrows(ServiceException.class, () -> service.register(form("ExamplePass7")));
        verify(users, never()).insertUser(any());
    }
    @Test void rejectsDuplicateUsernameEmailAndMissingRole() {
        validSetup(); when(users.checkUserNameUnique(any())).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.register(form("ExamplePass7")));
        when(users.checkUserNameUnique(any())).thenReturn(true); when(users.checkEmailUnique(any())).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.register(form("ExamplePass7")));
        when(users.checkEmailUnique(any())).thenReturn(true);
        when(database.queryForList(anyString(), eq(Long.class), eq("candidate"))).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.register(form("ExamplePass7")));
        verify(users, never()).insertUser(any());
    }
    @Test void rejectsPasswordMismatchWeakOrOverlongPasswordsAndMissingNotice() {
        for (String password : List.of("short1", "onlyletters", "x".repeat(20)+"1"))
            assertThrows(ServiceException.class, () -> service.register(form(password)));
        assertThrows(ServiceException.class, () -> service.register(new CandidateAccountService.Registration("candidate_test", "test@example.org", "ExamplePass7", "different", true, "", "")));
        assertThrows(ServiceException.class, () -> service.register(new CandidateAccountService.Registration("candidate_test", "test@example.org", "ExamplePass7", "ExamplePass7", false, "", "")));
        verifyNoInteractions(users, captcha);
    }
    @Test void resolvesEmailButDelegatesPasswordCaptchaAndTokenToRuoYi() {
        when(database.queryForList(anyString(), eq(String.class), eq("test@example.org"))).thenReturn(List.of("candidate_test"));
        when(login.login("candidate_test", "ExamplePass7", "challenge", "uuid")).thenReturn("token");
        assertEquals("token", service.login(" test@example.org ", "ExamplePass7", "challenge", "uuid"));
        assertEquals("token", service.login("candidate_test", "ExamplePass7", "challenge", "uuid"));
    }
    @Test void disabledRegistrationNeverWrites() {
        var controller = new CandidateAccountController(service, false);
        assertThrows(ServiceException.class, () -> controller.register(form("ExamplePass7")));
        verifyNoInteractions(users, captcha);
    }
    @Test void missingEmailStillRequiresCaptchaAndNeverAuthenticatesAnotherUsername() {
        validSetup(); when(database.queryForList(anyString(), eq(String.class), anyString())).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.login("missing@example.org", "ExamplePass7", "challenge", "uuid"));
        verify(login).validateCaptcha("missing@example.org", "challenge", "uuid");
        verify(login, never()).login(any(), any(), any(), any());
    }
    @Test void registrationOptionsRequireOneActiveCandidateRole() {
        validSetup(); assertTrue(service.registrationAvailable());
        when(database.queryForList(anyString(), eq(Long.class), eq("candidate"))).thenReturn(List.of());
        assertFalse(service.registrationAvailable());
    }
}
