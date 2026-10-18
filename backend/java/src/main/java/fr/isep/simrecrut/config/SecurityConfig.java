package fr.isep.simrecrut.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;

@org.springframework.context.annotation.Profile("!resume-workspace")
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                "/api/v1/auth/registration/code",
                "/api/v1/auth/register"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    EndpointRequest.to(HealthEndpoint.class))
                .permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/registration/code",
                    "/api/v1/auth/register")
                .permitAll()
                .anyRequest()
                .denyAll());

        return http.build();
    }
}
