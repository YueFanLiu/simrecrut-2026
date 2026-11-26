package fr.isep.simrecrut.security.authentication;

import fr.isep.simrecrut.config.properties.AuthenticationProperties;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final AuthenticationProperties properties;

    public JwtTokenService(JwtEncoder encoder, AuthenticationProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(String publicId, long securityVersion) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plusSeconds(properties.tokenTtl().toSeconds());
        String sessionId = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(publicId).id(sessionId).issuedAt(now).notBefore(now).expiresAt(expiresAt)
                .claim("ver", securityVersion).build();
        try {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
            return new IssuedToken(token, sessionId, expiresAt);
        } catch (JwtEncodingException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public record IssuedToken(String token, String sessionId, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}
