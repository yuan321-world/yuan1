package com.englishhero.identity;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IdentitySessionFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    MockHttpServletRequest authenticated(long login, boolean remember) {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me"); request.setServletPath("/api/v1/auth/me");
        request.getSession().setAttribute(IdentitySessionFilter.LOGIN_TIME, login);
        request.getSession().setAttribute(IdentitySessionFilter.REMEMBER, remember);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(1, "learner", 0, List.of("USER")), null, List.of()));
        return request;
    }
    @Test void ordinaryAbsoluteExpiryUsesSuccessfulLoginTimeAtExactBoundary() throws Exception {
        long now = 2_000_000_000L;
        var filter = new IdentitySessionFilter(mock(IdentityService.class), Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
        var response = new MockHttpServletResponse(); var passed = new AtomicBoolean();
        filter.doFilter(authenticated(now - 86_400_000L + 1, false), response, (q, s) -> passed.set(true));
        assertThat(passed.get()).isTrue();
        passed.set(false); response = new MockHttpServletResponse();
        filter.doFilter(authenticated(now - 86_400_000L, false), response, (q, s) -> passed.set(true));
        assertThat(response.getStatus()).isEqualTo(401); assertThat(passed.get()).isFalse();
    }
    @Test void rememberHasSevenDayAbsoluteBoundaryAndDatabaseFailureClosesAccess() throws Exception {
        long now = 2_000_000_000L;
        var service = mock(IdentityService.class);
        var filter = new IdentitySessionFilter(service, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
        var response = new MockHttpServletResponse(); var passed = new AtomicBoolean();
        filter.doFilter(authenticated(now - 604_800_000L, true), response, (q, s) -> passed.set(true));
        assertThat(response.getStatus()).isEqualTo(401); assertThat(passed.get()).isFalse();
        when(service.current(any())).thenThrow(new IllegalStateException("private database detail"));
        response = new MockHttpServletResponse();
        filter.doFilter(authenticated(now, false), response, (q, s) -> passed.set(true));
        assertThat(response.getStatus()).isEqualTo(500); assertThat(response.getContentAsString()).doesNotContain("private database detail");
    }
}
