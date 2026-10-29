package fr.isep.simrecrut.identity;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.identity.AdminRoleController;
import fr.isep.simrecrut.controller.identity.AdminRoleExceptionHandler;
import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.identity.Role;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.security.authentication.AuthenticationErrorHandler;
import fr.isep.simrecrut.security.authentication.JwtSessionAuthenticationConverter;
import fr.isep.simrecrut.service.identity.RoleQueryService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class AdminRoleAccessTest {

    private static final String ENDPOINT = "/api/v1/admin/roles";
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static RoleMapper roles;

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        roles = context.getBean(RoleMapper.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetDatabaseMock() {
        reset(roles);
    }

    @Test
    void anonymousRequestRequiresAuthentication() throws Exception {
        mvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(401))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(roles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANDIDATE", "HR", "RESEARCHER"})
    void ordinaryRolesCannotReadCatalogue(String role) throws Exception {
        mvc.perform(get(ENDPOINT).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(403));
        verifyNoInteractions(roles);
    }

    @Test
    void administratorStillNeedsExplicitPermission() throws Exception {
        mvc.perform(get(ENDPOINT).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("message").value("Access denied."));
        verifyNoInteractions(roles);
    }

    @Test
    void permissionAloneDoesNotGrantAdministratorAccess() throws Exception {
        mvc.perform(get(ENDPOINT).with(jwt().authorities(new SimpleGrantedAuthority("simrecrut:role:list"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roles);
    }

    @Test
    void authorizedAdministratorReceivesOnlyRoleProjection() throws Exception {
        when(roles.findAll()).thenReturn(Arrays.stream(AccountRole.values()).map(code -> {
            Role role = new Role();
            role.setId((long) code.ordinal() + 1);
            role.setRoleCode(code);
            role.setName(code.name());
            role.setDescription("Application role.");
            return role;
        }).toList());

        mvc.perform(get(ENDPOINT).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("simrecrut:role:list"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("code").value(0))
                .andExpect(jsonPath("requestId").isNotEmpty())
                .andExpect(jsonPath("data.length()").value(4))
                .andExpect(jsonPath("data[0].code").value("CANDIDATE"))
                .andExpect(jsonPath("data[0].id").doesNotExist());
        verify(roles).findAll();
    }

    @Test
    void emptyCatalogueIsAnEmptyArray() throws Exception {
        when(roles.findAll()).thenReturn(List.of());
        mvc.perform(get(ENDPOINT).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("simrecrut:role:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data").isEmpty());
    }

    @Test
    void databaseFailureReturnsSanitizedRetryableError() throws Exception {
        when(roles.findAll()).thenThrow(new DataAccessResourceFailureException("Private database diagnostic."));
        mvc.perform(get(ENDPOINT).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("simrecrut:role:list"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value(503))
                .andExpect(jsonPath("message").value("Role information is temporarily unavailable."))
                .andExpect(jsonPath("data.retryable").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AdminRoleController.class, AdminRoleExceptionHandler.class,
            RoleQueryService.class, AuthenticationErrorHandler.class})
    static class TestConfiguration {

        @Bean
        RoleMapper roleMapper() {
            return mock(RoleMapper.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        JwtSessionAuthenticationConverter converter() {
            return mock(JwtSessionAuthenticationConverter.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
