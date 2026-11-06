package com.englishhero.identity.token;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import com.englishhero.identity.IdentityErrors;
import org.springframework.web.filter.OncePerRequestFilter;
public class AuthOriginFilter extends OncePerRequestFilter {
    private final AuthProperties p;
    public AuthOriginFilter(AuthProperties p){this.p=p;}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
        if(r.getMethod().equals("POST")&&Set.of("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout").contains(r.getServletPath())) {
            String origin=r.getHeader("Origin");
            if(origin==null) {
                try {var uri=URI.create(r.getHeader("Referer"));if(uri.getUserInfo()==null&&uri.getHost()!=null)origin=new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),null,null,null).toString();}
                catch(Exception ignored){}
            }
            if(origin==null||!p.getAllowedOrigins().contains(origin)){IdentityErrors.write(s,r,403,"CSRF_INVALID","请求来源无效，请从本站重试");return;}
        }
        chain.doFilter(r,s);
    }
}
