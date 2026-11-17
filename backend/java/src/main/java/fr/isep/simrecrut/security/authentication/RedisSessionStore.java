package fr.isep.simrecrut.security.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class RedisSessionStore {

    private static final String PREFIX = "simrecrut:session:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public void create(String sessionId, long userId, String publicId, long version, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw AuthenticationFlowException.unavailable();
        }
        Session session = new Session(userId, publicId, version, expiresAt.getEpochSecond());
        try {
            boolean stored = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                    key(sessionId), json.writeValueAsString(session), ttl));
            if (!stored) {
                throw AuthenticationFlowException.unavailable();
            }
        } catch (DataAccessException | JsonProcessingException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public Session load(String sessionId) {
        try {
            String value = redis.opsForValue().get(key(sessionId));
            if (value == null) {
                return null;
            }
            Session session = json.readValue(value, Session.class);
            if (session == null || session.userId() < 1 || session.publicId() == null
                    || session.securityVersion() < 0 || session.expiresAtEpochSecond() < 1) {
                throw AuthenticationFlowException.unavailable();
            }
            return session;
        } catch (DataAccessException | JsonProcessingException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public void revoke(String sessionId) {
        try {
            redis.delete(key(sessionId));
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    private String key(String sessionId) {
        if (sessionId == null) {
            throw new BadCredentialsException("Invalid access token.");
        }
        try {
            if (!UUID.fromString(sessionId).toString().equals(sessionId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid access token.");
        }
        return PREFIX + sessionId;
    }

    public record Session(long userId, String publicId, long securityVersion, long expiresAtEpochSecond) {
    }
}
