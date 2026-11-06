package com.englishhero.config;
import com.englishhero.identity.token.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
class IdentityCookieTest {
    @Test void rememberCookieUsesRemainingAbsoluteLifeAndNormalCookieIsNotPersistent() {
        var now=Instant.parse("2026-10-15T00:00:00Z");var p=new AuthProperties();p.setSecure(true);
        var cookies=new RefreshCookieService(p,Clock.fixed(now.plusSeconds(100),ZoneOffset.UTC));
        var r=new MockHttpServletResponse();
        var f=LoginFamily.start("a".repeat(32),1,0,true,now);
        cookies.write(r,new TokenAuthenticationService.TokenIssue(null,"test-value",f));
        assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=604700","Path=/api/v1/auth","Secure","HttpOnly","SameSite=Lax").doesNotContain("Domain=");
        r=new MockHttpServletResponse();cookies.write(r,new TokenAuthenticationService.TokenIssue(null,"test-value",LoginFamily.start(f.sid(),1,0,false,now)));
        assertThat(r.getHeader("Set-Cookie")).doesNotContain("Max-Age");
        r=new MockHttpServletResponse();cookies.clear(r);assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=0","Path=/api/v1/auth");
    }
}
