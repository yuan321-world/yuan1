package com.englishhero.identity.token;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
@Component
public class AuthReadinessCheck implements ApplicationRunner {
    private final StringRedisTemplate redis;private final AuthProperties p;private final org.springframework.core.env.Environment environment;
    public AuthReadinessCheck(StringRedisTemplate redis,AuthProperties p,org.springframework.core.env.Environment environment){this.redis=redis;this.p=p;this.environment=environment;}
    @Override public void run(ApplicationArguments args){
        p.validate();
        if(environment.matchesProfiles("prod") && (!p.isSecure() || !p.getNamespace().startsWith("eh:prod:")))throw new IllegalStateException("Production requires Secure cookies and its own auth namespace.");
        try(var c=redis.getConnectionFactory().getConnection()){if(!"PONG".equals(c.ping()))throw new IllegalStateException("Auth Redis is not ready.");}
    }
}
