package fr.isep.simrecrut.config;

import fr.isep.simrecrut.security.authentication.AuthenticationErrorHandler;
import fr.isep.simrecrut.security.authentication.JwtSessionAuthenticationConverter;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!resume-workspace")
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtSessionAuthenticationConverter converter,
        AuthenticationErrorHandler errors) throws Exception {

        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                "/api/v1/auth/registration/code",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/password-reset",
                "/api/v1/auth/password-reset/code",
                "/api/v1/admin/users",
                "/api/v1/admin/users/**",
                "/api/v1/admin/roles/**",
                "/api/v1/hr/jobs",
                "/api/v1/hr/jobs/**",
                "/api/v1/resumes",
                "/api/v1/resumes/**"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET,
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/v3/api-docs.yaml/**")
                .permitAll()
                .requestMatchers(
                    EndpointRequest.to(HealthEndpoint.class))
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/registration/code",
                    "/api/v1/auth/register",
                    "/api/v1/auth/login")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/code")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/password-reset/options")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/auth/me")
                .authenticated()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/logout")
                .authenticated()
                .requestMatchers("/api/v1/resumes/**")
                .authenticated()
                .requestMatchers("/api/v1/hr/jobs", "/api/v1/hr/jobs/**")
                .hasRole("HR")
                .requestMatchers(HttpMethod.GET, "/api/v1/candidate/jobs", "/api/v1/candidate/jobs/**")
                .hasRole("CANDIDATE")
                .requestMatchers("/api/v1/admin/users", "/api/v1/admin/users/**",
                    "/api/v1/admin/roles", "/api/v1/admin/roles/**", "/api/v1/admin/permissions")
                .hasRole("ADMIN")
                .anyRequest()
                .denyAll())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors))
            .oauth2ResourceServer(resource -> resource
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors));

        return http.build();
    }
}
