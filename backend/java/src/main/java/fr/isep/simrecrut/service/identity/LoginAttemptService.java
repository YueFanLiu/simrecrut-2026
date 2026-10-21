package fr.isep.simrecrut.service.identity;

import fr.isep.simrecrut.config.properties.AuthenticationProperties;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class LoginAttemptService {

    private static final String PREFIX = "simrecrut:{login}:";

    private static final DefaultRedisScript<Long> RESERVE_IP = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                return math.max(redis.call('TTL', KEYS[1]), 1)
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RESERVE_ACCOUNT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return math.max(redis.call('TTL', KEYS[2]), 1)
            end
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                return math.max(redis.call('TTL', KEYS[1]), 1)
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> FAILED = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return math.max(redis.call('TTL', KEYS[2]), 1)
            end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if count >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
                return tonumber(ARGV[3])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> SUCCEEDED = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return math.max(redis.call('TTL', KEYS[2]), 1)
            end
            redis.call('DEL', KEYS[1])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthenticationProperties properties;
    private final UserAccountMapper accounts;
    private final AccountInputValidator inputs;

    public LoginAttemptService(StringRedisTemplate redis, AuthenticationProperties properties,
            UserAccountMapper accounts, AccountInputValidator inputs) {
        this.redis = redis;
        this.properties = properties;
        this.accounts = accounts;
        this.inputs = inputs;
    }

    public Attempt begin(String identifier, String clientIp) {
        try {
            // Reserve the IP budget before any account lookup or password calculation.
            check(redis.execute(RESERVE_IP, List.of(PREFIX + "ip:" + hash(clientIp)),
                    Integer.toString(properties.maxIpAttempts()),
                    Long.toString(properties.ipWindow().toSeconds())));

            String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
            UserAccount account = resolve(normalized);
            String bucket = account == null ? "unknown:" + hash(normalized) : "account:" + account.getId();
            check(redis.execute(RESERVE_ACCOUNT,
                    List.of(PREFIX + "requests:" + bucket, PREFIX + "lock:" + bucket),
                    Integer.toString(properties.maxAccountAttempts()),
                    Long.toString(properties.loginWindow().toSeconds())));
            return new Attempt(bucket, account == null ? null : account.getId());
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public void failed(Attempt attempt) {
        try {
            check(redis.execute(FAILED, failureKeys(attempt),
                    Integer.toString(properties.maxFailedAttempts()),
                    Long.toString(properties.loginWindow().toSeconds()),
                    Long.toString(properties.lockDuration().toSeconds())));
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    public void succeeded(Attempt attempt) {
        try {
            // Success clears failures, never the IP or account request budgets.
            check(redis.execute(SUCCEEDED, failureKeys(attempt)));
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        }
    }

    private UserAccount resolve(String identifier) {
        try {
            return identifier.contains("@")
                    ? accounts.findByEmail(inputs.normalizeEmail(identifier))
                    : accounts.findByUsername(inputs.normalizeUsername(identifier));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private List<String> failureKeys(Attempt attempt) {
        return List.of(PREFIX + "failures:" + attempt.bucket(), PREFIX + "lock:" + attempt.bucket());
    }

    private void check(Long wait) {
        if (wait == null) {
            throw AuthenticationFlowException.unavailable();
        }
        if (wait > 0) {
            throw AuthenticationFlowException.rateLimited(wait);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Login rate limiting is unavailable.", exception);
        }
    }

    public record Attempt(String bucket, Long accountId) {
    }
}
