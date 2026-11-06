package com.englishhero.identity.token;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import com.englishhero.identity.IdentityException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwtIdentityFilterTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void redisFailureNeverPassesProtectedRequest() throws Exception {
        var service=mock(TokenAuthenticationService.class);
        when(service.authenticate(anyString())).thenThrow(new IdentityException(503,"AUTH_SERVICE_UNAVAILABLE","暂不可用"));
        var req=new MockHttpServletRequest("GET","/api/v1/auth/me");req.setServletPath("/api/v1/auth/me");req.addHeader("Authorization","Bearer test");
        var response=new MockHttpServletResponse();
        new JwtIdentityFilter(service).doFilter(req,response,(q,s)->{throw new AssertionError("Must not pass");});
        assertThat(response.getStatus()).isEqualTo(503);
    }
    @Test void publicRecoveryIgnoresOldBearer() throws Exception {
        var service=mock(TokenAuthenticationService.class);
        var req=new MockHttpServletRequest("POST","/api/v1/auth/password-reset");req.setServletPath("/api/v1/auth/password-reset");req.addHeader("Authorization","Bearer old");
        new JwtIdentityFilter(service).doFilter(req,new MockHttpServletResponse(),(q,s)->{});
        verifyNoInteractions(service);
    }
}
