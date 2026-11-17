package fr.isep.simrecrut.identity;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.application.identity.PasswordResetApplicationService;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.identity.*;
import fr.isep.simrecrut.dto.identity.AccountManagementDtos.ResetCodeResponse;
import fr.isep.simrecrut.mapper.identity.*;
import fr.isep.simrecrut.security.authentication.*;
import fr.isep.simrecrut.service.identity.*;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class AccountManagementApiTest {
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static IdentityAdminMapper db;
    static PasswordResetApplicationService reset;

    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        db = context.getBean(IdentityAdminMapper.class);
        reset = context.getBean(PasswordResetApplicationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void close() { context.close(); }
    @BeforeEach void prepare() {
        org.mockito.Mockito.reset(db, reset);
        when(context.getBean(PlatformTransactionManager.class).getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(db.listUsers(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test void anonymousAdminAccessIsDeniedAndLegacyRoutesAreNotExposed() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/system/user/list").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }

    @Test void accountPermissionDoesNotBypassAdminRole() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_HR"), new SimpleGrantedAuthority("simrecrut:user:list"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }

    @Test void adminStillNeedsOperationPermission() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("message").value("Access denied."));
        mvc.perform(get("/api/v1/admin/permissions").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("simrecrut:user:list"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }

    @Test void authorizedListUsesIndependentEnvelopeAndNoStore() throws Exception {
        mvc.perform(get("/api/v1/admin/users?page=2&pageSize=5&keyword=engineer").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("simrecrut:user:list"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("code").value(0)).andExpect(jsonPath("data.items").isArray())
                .andExpect(jsonPath("data.page").value(2)).andExpect(jsonPath("data.pageSize").value(5));
        verify(db).listUsers("engineer", null, null, 5, 5);
    }

    @Test void writesNeedNoLegacyCsrfButStillRequireActionPermission() throws Exception {
        mvc.perform(put("/api/v1/admin/users/01BBBBBBBBBBBBBBBBBBBBBBBB/status")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"status\":\"DISABLED\",\"revision\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(db);
    }

    @Test void unknownFieldsAndUnsupportedRolesAreRejected() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("simrecrut:user:create"));
        mvc.perform(post("/api/v1/admin/users").with(admin).contentType("application/json")
                .content("{\"username\":\"new_user\",\"email\":\"one@example.org\",\"roles\":[\"HR\"],\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/users").with(admin).contentType("application/json")
                .content("{\"username\":\"new_user\",\"email\":\"one@example.org\",\"roles\":[\"REVIEWER\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(db);
    }

    @Test void resetCodeIsPublicAndDoesNotAcceptLegacyCaptchaOrOwnerFields() throws Exception {
        when(reset.requestCode(any(), anyString())).thenReturn(new ResetCodeResponse(UUID.randomUUID().toString(), 600, 60));
        mvc.perform(post("/api/v1/auth/password-reset/code").contentType("application/json")
                .content("{\"email\":\"one@example.org\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.challengeId").isNotEmpty())
                .andExpect(jsonPath("data.emailCode").doesNotExist());
        mvc.perform(post("/api/v1/auth/password-reset/code").contentType("application/json")
                .content("{\"email\":\"one@example.org\",\"uuid\":\"old-captcha\"}"))
                .andExpect(status().isBadRequest());
        verify(reset, times(1)).requestCode(any(), anyString());
    }

    @Test void datastoreErrorsAreSanitized() throws Exception {
        when(db.listUsers(anyString(), any(), any(), anyInt(), anyInt())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("Private SQL and credentials"));
        mvc.perform(get("/api/v1/admin/users").with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("simrecrut:user:list"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("data.retryable").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private SQL"))));
    }

    @Test void publicRecoveryRateLimitProvidesRetryMetadata() throws Exception {
        when(reset.requestCode(any(), anyString())).thenThrow(
                new IdentityManagementException(429, "Too many requests. Try again later."));
        mvc.perform(post("/api/v1/auth/password-reset/code").contentType("application/json")
                .content("{\"email\":\"one@example.org\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("data.retryable").value(true));
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, AuthenticationErrorHandler.class, AdminUserController.class,
            RolePermissionController.class, PasswordResetController.class, AccountManagementExceptionHandler.class,
            AdminAccountService.class})
    static class Config {
        @Bean IdentityAdminMapper db() { return mock(IdentityAdminMapper.class); }
        @Bean UserAccountMapper accounts() { return mock(UserAccountMapper.class); }
        @Bean RoleMapper roles() { return mock(RoleMapper.class); }
        @Bean PasswordResetApplicationService reset() { return mock(PasswordResetApplicationService.class); }
        @Bean PasswordEncoder passwords() { return mock(PasswordEncoder.class); }
        @Bean PublicIdGenerator ids() { return new PublicIdGenerator(); }
        @Bean AccountInputValidator inputs() {
            return new AccountInputValidator(Validation.buildDefaultValidatorFactory().getValidator());
        }
        @Bean PlatformTransactionManager transactions() { return mock(PlatformTransactionManager.class); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}
