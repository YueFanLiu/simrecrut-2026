package fr.isep.simrecrut.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysUserService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

/** Verify mailbox proof, replay rejection and session revocation without real emails or passwords. */
class PasswordResetServiceTest {
    final PasswordResetCodes codes=mock(PasswordResetCodes.class);
    final PasswordResetMail mail=mock(PasswordResetMail.class);
    final JdbcTemplate database=mock(JdbcTemplate.class);
    final ISysConfigService config=mock(ISysConfigService.class);
    final SysLoginService login=mock(SysLoginService.class);
    final ISysUserService users=mock(ISysUserService.class);
    final RedisCache sessions=mock(RedisCache.class);
    final PasswordResetService service=new PasswordResetService(codes,mail,database,config,login,users,sessions,"unit-test-secret");
    final Map<String,String> digests=new HashMap<>(), accounts=new HashMap<>();
    String capturedCode;
    void setup() {
        when(mail.available()).thenReturn(true); when(config.selectCaptchaEnabled()).thenReturn(true);
        when(codes.allow(any(),any())).thenReturn(true);
        when(database.queryForList(anyString(),eq(Long.class),eq("person@example.test"))).thenReturn(List.of(5L));
        SysUser user=new SysUser(5L); user.setPassword("previous-hash"); when(users.selectUserById(5L)).thenReturn(user);
        doAnswer(call->{digests.put(call.getArgument(0),call.getArgument(1));accounts.put(call.getArgument(0),call.getArgument(2));return null;}).when(codes).store(any(),any(),any());
        doAnswer(call->{capturedCode=call.getArgument(1);return null;}).when(mail).send(any(),any());
        when(codes.consume(any(),any())).thenAnswer(call->{String id=call.getArgument(0);return Objects.equals(digests.get(id),call.getArgument(1))?accounts.remove(id):"";});
        when(database.update(anyString(),any(Object[].class))).thenReturn(1);
        when(sessions.keys("login_tokens:*")).thenReturn(List.of("login_tokens:own","login_tokens:other"));
        LoginUser own=new LoginUser(); own.setUserId(5L); LoginUser other=new LoginUser();other.setUserId(6L);
        when(sessions.getCacheObject("login_tokens:own")).thenReturn(own);
        when(sessions.getCacheObject("login_tokens:other")).thenReturn(other);
    }
    String request() { return service.request(new PasswordResetService.Challenge("person@example.test","captcha","uuid"),"127.0.0.1"); }
    PasswordResetService.Reset reset(String id,String code) {return new PasswordResetService.Reset("person@example.test",id,code,"NewPassword7","NewPassword7");}
    @Test void verifiedResetHashesPasswordAndRevokesOnlyThisUsersSessions() {
        setup(); String id=request(); assertTrue(capturedCode.matches("\\d{6}"));
        assertFalse(accounts.get(id).contains(capturedCode));
        verify(login).validateCaptcha("password-reset","captcha","uuid");
        service.reset(reset(id,capturedCode));
        ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
        verify(database).update(anyString(),args.capture());
        assertTrue(SecurityUtils.matchesPassword("NewPassword7",(String)args.getValue()[0]));
        assertEquals(5L,args.getValue()[1]); assertEquals("previous-hash",args.getValue()[3]);
        verify(sessions).deleteObject("login_tokens:own"); verify(sessions,never()).deleteObject("login_tokens:other");
        assertThrows(ServiceException.class,()->service.reset(reset(id,capturedCode)));
    }
    @Test void wrongOrExpiredCodeNeverChangesPassword() {
        setup(); String id=request(); String wrong=capturedCode.equals("000000")?"000001":"000000";
        assertThrows(ServiceException.class,()->service.reset(reset(id,wrong)));
        assertThrows(ServiceException.class,()->service.reset(reset(UUID.randomUUID().toString(),capturedCode)));
        verify(database,never()).update(anyString(),any(Object[].class));
    }
    @Test void unregisteredAddressDoesNotSendAndUsesTheSameRequestShape() {
        setup(); when(database.queryForList(anyString(),eq(Long.class),anyString())).thenReturn(List.of());
        assertNotNull(UUID.fromString(request())); verify(mail,never()).send(any(),any());
    }
    @Test void missingMailConfigAndRateLimitDoNotSendMessages() {
        setup(); when(mail.available()).thenReturn(false); assertThrows(ServiceException.class,this::request);
        when(mail.available()).thenReturn(true); when(codes.allow(any(),any())).thenReturn(false); assertThrows(ServiceException.class,this::request);
        verify(mail,never()).send(any(),any());
    }
    @Test void mailFailureDiscardsChallenge() {
        setup(); doThrow(new IllegalStateException("SMTP unavailable")).when(mail).send(any(),any());
        assertThrows(ServiceException.class,this::request); verify(codes).discard(any());
    }
    @Test void gmailFromDefaultsToConfiguredMailbox() {
        var sender=mock(JavaMailSender.class); var delivery=new PasswordResetMail(sender,"sender@gmail.com","test-app-password","");
        assertTrue(delivery.available()); delivery.send("person@example.test","123456");
        ArgumentCaptor<org.springframework.mail.SimpleMailMessage> message=ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        verify(sender).send(message.capture()); assertEquals("sender@gmail.com",message.getValue().getFrom());
    }
}
