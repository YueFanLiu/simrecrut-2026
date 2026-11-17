package fr.isep.simrecrut.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Use isolated Redis keys to verify Lua expiry, attempt limits and one-use consumption. */
@EnabledIfEnvironmentVariable(named="SIMRECRUT_TEST_REDIS",matches="true")
class PasswordResetCodesTest {
    @Test void enforcesCooldownExpiryAttemptLimitAndSingleUse() {
        var factory=new LettuceConnectionFactory("127.0.0.1",6379); factory.afterPropertiesSet(); factory.start();
        var redis=new StringRedisTemplate(factory); redis.afterPropertiesSet(); var codes=new PasswordResetCodes(redis);
        String id=UUID.randomUUID().toString(), second=UUID.randomUUID().toString(), mail="test-"+id, ip="test-ip-"+id;
        List<String> keys=List.of("reset:code:"+id,"reset:code:"+second,"reset:cooldown:"+mail,"reset:email:"+mail,"reset:ip:"+ip);
        try {
            assertTrue(codes.allow(mail,ip)); assertFalse(codes.allow(mail,ip));
            codes.store(id,"correct-digest","account"); assertTrue(redis.getExpire("reset:code:"+id)>0);
            for(int i=0;i<5;i++) assertTrue(codes.consume(id,"wrong-digest").isBlank());
            assertTrue(codes.consume(id,"correct-digest").isBlank());
            codes.store(id,"correct-digest","account"); assertEquals("account",codes.consume(id,"correct-digest"));
            assertTrue(codes.consume(id,"correct-digest").isBlank());
            codes.store(second,"correct-digest","account"); redis.expire("reset:code:"+second,Duration.ZERO);
            assertTrue(codes.consume(second,"correct-digest").isBlank());
        } finally { redis.delete(keys); factory.destroy(); }
    }
}
