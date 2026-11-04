package fr.isep.simrecrut.service.identity;

import fr.isep.simrecrut.config.properties.RegistrationProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final String PREFIX = "simrecrut:{registration}:";

    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            local cooldown = redis.call('TTL', KEYS[1])
            if cooldown > 0 then
                return cooldown
            end

            for i = 2, 4 do
                local count = tonumber(redis.call('GET', KEYS[i]) or '0')
                if count >= tonumber(ARGV[i]) then
                    return math.max(redis.call('TTL', KEYS[i]), 1)
                end
            end

            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            for i = 2, 4 do
                if redis.call('INCR', KEYS[i]) == 1 then
                    redis.call('EXPIRE', KEYS[i], 3600)
                end
            end

            redis.call('HSET', KEYS[5],
                'purpose', 'REGISTRATION',
                'email', ARGV[6],
                'digest', ARGV[7],
                'attempts', '0')
            redis.call('EXPIRE', KEYS[5], ARGV[5])
            redis.call('SET', KEYS[6], ARGV[8], 'EX', ARGV[5])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> SUBMISSION_LIMIT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                return math.max(redis.call('TTL', KEYS[1]), 1)
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], 600)
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            if redis.call('GET', KEYS[2]) ~= ARGV[3] then
                return 0
            end
            if redis.call('HGET', KEYS[1], 'purpose') ~= 'REGISTRATION' then
                return 0
            end
            if redis.call('HGET', KEYS[1], 'email') ~= ARGV[1] then
                return 0
            end
            if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[2] then
                local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
                if attempts >= tonumber(ARGV[4]) then
                    redis.call('DEL', KEYS[1], KEYS[2])
                end
                return 0
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DISCARD = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                redis.call('DEL', KEYS[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RegistrationProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final byte[] secret;

    public EmailVerificationService(StringRedisTemplate redis, RegistrationProperties properties) {
        this.redis = redis;
        this.properties = properties;

        try {
            this.secret = Base64.getDecoder().decode(
                    properties.codeSecret() == null ? "" : properties.codeSecret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("REGISTRATION_CODE_SECRET must be valid Base64.");
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException(
                    "REGISTRATION_CODE_SECRET must contain at least 32 random bytes.");
        }
    }

    public IssuedCode issue(String normalizedEmail, String clientIp) {
        String challengeId = UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String emailBinding = digest("email", normalizedEmail);
        String ipBinding = digest("ip", clientIp);
        String codeDigest = digest("code", challengeId, normalizedEmail, code);
        Instant expiresAt = Instant.now().plusSeconds(properties.codeTtl().toSeconds());

        Long wait = redis.execute(
                ISSUE,
                List.of(
                        PREFIX + "cooldown:" + emailBinding,
                        PREFIX + "email-hour:" + emailBinding,
                        PREFIX + "ip-hour:" + ipBinding,
                        PREFIX + "global-hour",
                        challengeKey(challengeId),
                        latestKey(emailBinding)),
                Long.toString(properties.resendCooldown().toSeconds()),
                Integer.toString(properties.maxEmailSendsPerHour()),
                Integer.toString(properties.maxIpSendsPerHour()),
                Integer.toString(properties.maxGlobalSendsPerHour()),
                Long.toString(properties.codeTtl().toSeconds()),
                emailBinding,
                codeDigest,
                challengeId);

        requireResult(wait);
        if (wait > 0) {
            throw RegistrationException.rateLimited(wait);
        }
        return new IssuedCode(challengeId, code, expiresAt);
    }

    public void checkSubmissionLimit(String clientIp) {
        Long wait = redis.execute(
                SUBMISSION_LIMIT,
                List.of(PREFIX + "ip-verify:" + digest("ip", clientIp)),
                Integer.toString(properties.maxIpVerificationsPerTenMinutes()));

        requireResult(wait);
        if (wait > 0) {
            throw RegistrationException.rateLimited(wait);
        }
    }

    public void consume(String normalizedEmail, String challengeId, String code) {
        if (challengeId == null
                || !challengeId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || code == null || !code.matches("[0-9]{6}")) {
            throw RegistrationException.invalidVerification();
        }

        String emailBinding = digest("email", normalizedEmail);
        Long result = redis.execute(
                CONSUME,
                List.of(challengeKey(challengeId), latestKey(emailBinding)),
                emailBinding,
                digest("code", challengeId, normalizedEmail, code),
                challengeId,
                Integer.toString(properties.maxCodeAttempts()));

        requireResult(result);
        if (result != 1L) {
            throw RegistrationException.invalidVerification();
        }
    }

    public void discard(String normalizedEmail, String challengeId) {
        try {
            redis.execute(
                    DISCARD,
                    List.of(challengeKey(challengeId), latestKey(digest("email", normalizedEmail))),
                    challengeId);
        } catch (DataAccessException exception) {
            // Redis TTL still bounds the unused challenge lifetime.
            LOGGER.warn("Registration challenge cleanup failed.");
        }
    }

    private void requireResult(Long result) {
        if (result == null) {
            throw RegistrationException.unavailable();
        }
    }

    private String challengeKey(String challengeId) {
        return PREFIX + "challenge:" + challengeId;
    }

    private String latestKey(String emailBinding) {
        return PREFIX + "latest:" + emailBinding;
    }

    private String digest(String purpose, String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            update(mac, "REGISTRATION");
            update(mac, purpose);
            for (String value : values) {
                update(mac, value);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Registration verification is unavailable.", exception);
        }
    }

    private void update(Mac mac, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        mac.update((byte) (length >>> 24));
        mac.update((byte) (length >>> 16));
        mac.update((byte) (length >>> 8));
        mac.update((byte) length);
        mac.update(bytes);
    }

    public record IssuedCode(String challengeId, String code, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedCode[redacted]";
        }
    }
}
