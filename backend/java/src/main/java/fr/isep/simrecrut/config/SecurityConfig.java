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
                "/api/v1/auth/logout"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    EndpointRequest.to(HealthEndpoint.class))
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/registration/code",
                    "/api/v1/auth/register",
                    "/api/v1/auth/login")
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
