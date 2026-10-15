package com.englishhero.config;
import com.englishhero.identity.IdentitySessionFilter;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.session.web.http.CookieSerializer;
import static org.assertj.core.api.Assertions.*;
class IdentityCookieTest {
    @Test void sharedSerializerDoesNotMixNormalRememberOrDeletionAndProductionIsSecure() {
        long now = 2_000_000_000L;
        var serializer = new SecurityConfig().cookieSerializer(true, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
        var remembered = new MockHttpServletRequest();
        remembered.getSession().setAttribute(IdentitySessionFilter.REMEMBER, true);
        remembered.getSession().setAttribute(IdentitySessionFilter.LOGIN_TIME, now);
        var r = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(remembered, r, "test-session-id"));
        assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=604800", "Secure", "HttpOnly", "SameSite=Lax");
        r = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(new MockHttpServletRequest(), r, "other-test-id"));
        assertThat(r.getHeader("Set-Cookie")).doesNotContain("Max-Age", "Domain=");
        r = new MockHttpServletResponse();
        var deletion = new CookieSerializer.CookieValue(remembered, r, ""); deletion.setCookieMaxAge(0);
        serializer.writeCookieValue(deletion); assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=0");
    }
}
