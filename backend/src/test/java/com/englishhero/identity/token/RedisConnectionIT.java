package com.englishhero.identity.token;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.Duration;

class RedisConnectionIT {
    @Test void dedicatedRedisSupportsPingTtlAndLua() throws Exception {
        var factory=TokenTestSupport.connection();
        try {
            var redis=new StringRedisTemplate(factory);
            try(var c=factory.getConnection()){assertThat(c.ping()).isEqualTo("PONG");}
            String key="eh:test:probe:"+UUID.randomUUID();
            try {
                redis.opsForValue().set(key,"1",Duration.ofSeconds(5));
                assertThat(redis.getExpire(key)).isBetween(1L,5L);
                var script=new DefaultRedisScript<Long>("return redis.call('INCR',KEYS[1])",Long.class);
                assertThat(redis.execute(script,List.of(key))).isEqualTo(2);
            } finally {redis.delete(key);}
        } finally {factory.destroy();}
    }
}
