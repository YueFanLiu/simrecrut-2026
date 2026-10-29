package fr.isep.simrecrut.auth;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Rate limits and one-use code consumption remain atomic across application instances. */
@Component
public class PasswordResetCodes {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> SUBMIT = new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        if count >= 60 then return 0 end
        if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 600) end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> STORE = new DefaultRedisScript<>("""
        redis.call('HSET',KEYS[1],'digest',ARGV[1],'account',ARGV[2],'attempts','0')
        redis.call('EXPIRE',KEYS[1],600)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
        if tonumber(redis.call('GET',KEYS[2]) or '0') >= 5 then return 0 end
        if tonumber(redis.call('GET',KEYS[3]) or '0') >= 10 then return 0 end
        redis.call('SET',KEYS[1],'1','EX',60)
        for i=2,3 do
          if redis.call('INCR',KEYS[i]) == 1 then redis.call('EXPIRE',KEYS[i],3600) end
        end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<String> VERIFY = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1]) == 0 then return '' end
        if redis.call('HGET',KEYS[1],'digest') ~= ARGV[1] then
          if redis.call('HINCRBY',KEYS[1],'attempts',1) >= 5 then redis.call('DEL',KEYS[1]) end
          return ''
        end
        local account = redis.call('HGET',KEYS[1],'account')
        redis.call('DEL',KEYS[1])
        return account
        """, String.class);
    public PasswordResetCodes(StringRedisTemplate redis) { this.redis = redis; }
    public boolean allowSubmission(String ipHash) {
        return Long.valueOf(1).equals(redis.execute(SUBMIT, List.of("reset:submit:" + ipHash)));
    }
    public boolean allow(String emailHash, String ipHash) {
        return Long.valueOf(1).equals(redis.execute(LIMIT, List.of("reset:cooldown:"+emailHash,
                "reset:email:"+emailHash, "reset:ip:"+ipHash)));
    }
    public void store(String id, String digest, String account) {
        String key = "reset:code:"+id;
        if (!Long.valueOf(1).equals(redis.execute(STORE, List.of(key), digest, account)))
            throw new IllegalStateException("Verification storage is unavailable.");
    }
    public String consume(String id, String digest) {
        return redis.execute(VERIFY, List.of("reset:code:"+id), digest);
    }
    public void discard(String id) { redis.delete("reset:code:"+id); }
}
