package com.englishhero.identity.token;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import com.englishhero.identity.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public class JwtIdentityFilter extends OncePerRequestFilter {
    private final TokenAuthenticationService service;
    public JwtIdentityFilter(TokenAuthenticationService service){this.service=service;}
    public static String bearer(HttpServletRequest r){var v=r.getHeader("Authorization");return v!=null&&v.startsWith("Bearer ")?v.substring(7):null;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        return Set.of("/api/v1/auth/csrf","/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout","/api/v1/auth/register","/api/v1/auth/email-codes","/api/v1/auth/password-reset","/api/v1/system/status").contains(r.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
        var bearer=bearer(r);
        if(bearer!=null) {
            try {var p=service.authenticate(bearer);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.roles().stream().map(role->new SimpleGrantedAuthority("ROLE_"+role)).toList()));}
            catch(IdentityException e){IdentityErrors.write(s,r,e.status,e.code,e.getMessage());return;}
            catch(Exception e){IdentityErrors.write(s,r,503,"AUTH_SERVICE_UNAVAILABLE","认证服务暂不可用，请稍后重试");return;}
        }
        chain.doFilter(r,s);
    }
}
