package com.englishhero.identity;
import com.englishhero.identity.token.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TokenFlowIT extends TokenHttpITSupport {
    @Autowired JwtService jwt;
    @Autowired RedisFamilyRepository families;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    String loginUser(HttpClient c) throws Exception {
        String name=fixtureName();identities.register(name,"token-valid-password",null);
        var result=call(c,"POST","/auth/login","{\"username\":\""+name+"\",\"password\":\"token-valid-password\"}",csrf(c));
        assertThat(result.statusCode()).isEqualTo(200);
        return name;
    }
    @Test void refreshRotatesAndReplayRevokesButRandomSecretDoesNot() throws Exception {
        var c=client();loginUser(c);
        var jar=((CookieManager)c.cookieHandler().orElseThrow()).getCookieStore();
        var old=jar.getCookies().stream().filter(v->v.getName().equals("EH_REFRESH")).findFirst().orElseThrow().getValue();
        assertThat(call(c,"POST","/auth/refresh",null,csrf(c)).statusCode()).isEqualTo(200);
        String csrf=csrf(c);
        String cookie=jar.getCookies().stream().filter(v->v.getName().equals("EH_CSRF")).findFirst().orElseThrow().getValue();
        var random=old.substring(0,33)+"A".repeat(43);
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/refresh"))
            .header("Origin","http://127.0.0.1:4174").header("X-CSRF-TOKEN",csrf).header("Cookie","EH_REFRESH="+random+"; EH_CSRF="+cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(HttpClient.newHttpClient().send(r,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(call(c,"GET","/auth/me",null,null).statusCode()).isEqualTo(200);
        var replay=HttpRequest.newBuilder(r.uri()).header("Origin","http://127.0.0.1:4174").header("X-CSRF-TOKEN",csrf).header("Cookie","EH_REFRESH="+old+"; EH_CSRF="+cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(HttpClient.newHttpClient().send(replay,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(call(c,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
    }
    @Test void csrfAndOriginRequiredAndAnonymousCsrfCreatesNoSession() throws Exception {
        var c=client();var token=csrf(c);
        assertThat(((CookieManager)c.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().anyMatch(v->v.getName().equals("EH_SESSION")||v.getName().equals("JSESSIONID"))).isFalse();
        for(String origin:List.of("", "https://evil.invalid")) {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/refresh")).header("X-CSRF-TOKEN",token);
            if(!origin.isEmpty())b.header("Origin",origin);
            assertThat(c.send(b.POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        }
        assertThat(call(c,"POST","/auth/refresh",null,null).statusCode()).isEqualTo(403);
        assertThat(call(c,"POST","/auth/logout",null,csrf(c)).statusCode()).isEqualTo(204);
        assertThat(call(c,"POST","/auth/logout",null,csrf(c)).statusCode()).isEqualTo(204);
    }
    @Test void onlyValidExpiredFamilyGetsExpiryAndLogoutWorksWithExpiredAccess() throws Exception {
        var c=client();String name=loginUser(c);
        String access=accessTokens.get(c);var verified=jwt.verifySignatureAndClaims(access);
        var family=families.find(verified.sid());
        var p=new SessionPrincipal(verified.userId(),name,verified.securityVersion(),List.of("USER"));
        var signing=new JwtService(TokenTestSupport.auth(),java.time.Clock.fixed(java.time.Instant.now().minusSeconds(700),java.time.ZoneOffset.UTC));
        accessTokens.put(c,signing.issue(p,family).value());
        var expired=call(c,"GET","/auth/me",null,null);
        assertThat(expired.statusCode()).isEqualTo(401);assertThat(expired.body()).contains("ACCESS_TOKEN_EXPIRED");
        assertThat(call(c,"POST","/auth/logout",null,csrf(c)).statusCode()).isEqualTo(204);
        var revoked=call(c,"GET","/auth/me",null,null);
        assertThat(revoked.statusCode()).isEqualTo(401);assertThat(revoked.body()).doesNotContain("ACCESS_TOKEN_EXPIRED");
    }
    @Test void rolesAndSecurityVersionAreReadForEveryRequestAndPublicRecoveryIgnoresOldBearer() throws Exception {
        var c=client();String name=loginUser(c);
        jdbc.update("INSERT INTO identity_user_role(user_id,role) SELECT id,'ADMIN' FROM identity_user WHERE username=?",name);
        assertThat(call(c,"GET","/admin/identity",null,null).statusCode()).isEqualTo(200);
        jdbc.update("DELETE r FROM identity_user_role r JOIN identity_user u ON u.id=r.user_id WHERE u.username=? AND r.role='ADMIN'",name);
        assertThat(call(c,"GET","/admin/identity",null,null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE identity_user SET security_version=security_version+1 WHERE username=?",name);
        assertThat(call(c,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(call(c,"POST","/auth/refresh",null,csrf(c)).statusCode()).isEqualTo(401);
        // Unique fixture mailbox avoids sharing the persistent hourly email quota between runs.
        var recovery=call(c,"POST","/auth/email-codes","{\"purpose\":\"RESET\",\"username\":\""+name+"\",\"email\":\""+name+"@example.com\"}",csrf(c));
        assertThat(recovery.statusCode()).isEqualTo(202);
    }
}
