package fr.isep.simrecrut.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import fr.isep.simrecrut.config.properties.AuthenticationProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@Profile("!resume-workspace")
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(AuthenticationProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthenticationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                jwt -> validateClaims(jwt, properties)));
        return decoder;
    }

    private SecretKey key(AuthenticationProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(
                    properties.signingSecret() == null ? "" : properties.signingSecret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SIGNING_SECRET must be valid Base64.");
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SIGNING_SECRET must contain at least 32 random bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt, AuthenticationProperties properties) {
        try {
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            Instant notBefore = jwt.getNotBefore();
            String subject = jwt.getSubject();
            String sessionId = jwt.getId();
            Object versionClaim = jwt.getClaim("ver");
            long version = Long.parseLong(String.valueOf(versionClaim));
            boolean valid = "JWT".equals(jwt.getHeaders().get("typ"))
                    && subject != null && subject.matches("[0-9A-HJKMNP-TV-Z]{26}")
                    && sessionId != null && UUID.fromString(sessionId).toString().equals(sessionId)
                    && version >= 0 && jwt.getAudience().contains(properties.audience())
                    && issuedAt != null && expiresAt != null && notBefore != null
                    && expiresAt.isAfter(issuedAt) && notBefore.isBefore(expiresAt)
                    && !issuedAt.isAfter(Instant.now().plus(properties.clockSkew()))
                    && Duration.between(issuedAt, expiresAt).compareTo(properties.tokenTtl()) <= 0;
            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalidToken();
        }
        return invalidToken();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid access token.", null));
    }
}
