package com.englishhero.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class IdentitySessionFilter extends OncePerRequestFilter {
    public static final String LOGIN_TIME = "EH_LOGIN_TIME";
    public static final String REMEMBER = "EH_REMEMBER";
    private final IdentityService service;
    private final Clock clock;
    public IdentitySessionFilter(IdentityService service, Clock clock) { this.service = service; this.clock = clock; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionPrincipal principal) {
            try {
                service.current(principal);
                var session = request.getSession(false);
                Long time = session == null ? null : (Long) session.getAttribute(LOGIN_TIME);
                boolean remember = session != null && Boolean.TRUE.equals(session.getAttribute(REMEMBER));
                long ttl = (remember ? 604800L : 86400L) * 1000;
                if (time == null || clock.millis() - time >= ttl || clock.millis() < time) throw new IdentityException(401, "UNAUTHENTICATED", "登录已失效，请重新登录");
            } catch (IdentityException e) {
                var session = request.getSession(false); if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                String path = request.getServletPath();
                if (!path.equals("/api/v1/auth/csrf") && !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register") && !path.equals("/api/v1/system/status")) {
                    IdentityErrors.write(response, request, 401, "UNAUTHENTICATED", "登录已失效，请重新登录"); return;
                }
            } catch (Exception e) {
                IdentityErrors.write(response, request, 500, "INTERNAL_ERROR", "服务暂不可用，请稍后重试"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
