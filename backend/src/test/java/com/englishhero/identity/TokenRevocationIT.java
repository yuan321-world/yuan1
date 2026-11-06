package com.englishhero.identity;
import com.englishhero.identity.token.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TokenRevocationIT extends TokenHttpITSupport {
    @Autowired JwtService jwt;
    @Autowired RedisFamilyRepository families;
    @Autowired StringRedisTemplate redis;
    @Test void realRedisOutageFailsClosedAndCommittedPasswordStillRevokesAllTokens() throws Exception {
        String name=fixtureName();identities.register(name,"original-password",null);
        var c=client();var other=client();String body="{\"username\":\""+name+"\",\"password\":\"original-password\"}";
        assertThat(call(c,"POST","/auth/login",body,csrf(c)).statusCode()).isEqualTo(200);
        assertThat(call(other,"POST","/auth/login",body,csrf(other)).statusCode()).isEqualTo(200);
        var access=jwt.verifySignatureAndClaims(accessTokens.get(c));
        var principal=new SessionPrincipal(access.userId(),name,access.securityVersion(),List.of("USER"));
        // Shutdown is limited to the independently authenticated test connection; never the developer instance.
        TokenTestSupport.properties();
        try {
            try(var connection=redis.getConnectionFactory().getConnection()){connection.serverCommands().shutdown();}
            catch(org.springframework.dao.DataAccessException expected) { /* server closes its connection */ }
            var unavailable=call(c,"GET","/auth/me",null,null);
            assertThat(unavailable.statusCode()).isEqualTo(503);
            assertThat(unavailable.body()).contains("AUTH_SERVICE_UNAVAILABLE").doesNotContain("RedisConnection","password");
            identities.password(principal,"original-password","replacement-password");
            assertThat(jdbc.queryForObject("SELECT security_version FROM identity_user WHERE username=?",Integer.class,name)).isEqualTo(access.securityVersion()+1);
        } finally {
            var project=java.nio.file.Path.of("..").toAbsolutePath().normalize();
            var process=new ProcessBuilder("pwsh","-NoProfile","-File",project.resolve("scripts/project-redis.ps1").toString(),"-Environment","test","-Action","start").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)||process.exitValue()!=0)throw new AssertionError("Project test Redis failed to restart.");
        }
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);boolean redisReady=false;
        while(System.nanoTime()<deadline){
            try{redisReady="PONG".equals(redis.execute((org.springframework.data.redis.core.RedisCallback<String>)connection->connection.ping()));if(redisReady)break;}
            catch(org.springframework.dao.DataAccessException reconnecting){/* Wait for the existing test connection to recover. */}
            Thread.sleep(100);
        }
        assertThat(redisReady).as("test Redis must actually reconnect before token revocation assertions").isTrue();
        assertThat(call(c,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(call(other,"POST","/auth/refresh",null,csrf(other)).statusCode()).isEqualTo(401);
    }
}
