package com.englishhero.identity;

import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    public record Registration(String username, String password, String displayName) {}
    public record Login(String username, String password, Boolean rememberMe) {}
    public record Profile(String displayName) {}
    public record Password(String currentPassword, String newPassword) {}
    public record CsrfDto(String token, String headerName) {}
    private final IdentityService service;
    private final IdentityMapper mapper;
    private final AuthenticationManager manager;
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository repository;
    private final java.time.Clock clock;
    public IdentityController(IdentityService service, IdentityMapper mapper, AuthenticationManager manager,
            SessionAuthenticationStrategy strategy, SecurityContextRepository repository, java.time.Clock clock) {
        this.service = service; this.mapper = mapper; this.manager = manager;
        this.strategy = strategy; this.repository = repository; this.clock = clock;
    }
    @GetMapping("/auth/csrf") public ResponseEntity<CsrfDto> csrf(HttpServletRequest request) {
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return response(200, new CsrfDto(token.getToken(), token.getHeaderName()));
    }
    @PostMapping("/auth/register") public ResponseEntity<UserDto> register(@RequestBody Registration input) {
        return response(201, service.register(input.username(), input.password(), input.displayName()));
    }
    @PostMapping("/auth/login") public ResponseEntity<UserDto> login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        String name = IdentityRules.username(input.username(), false);
        IdentityRules.password(input.password(), "password");
        IdentityUser user;
        try { user = ((com.englishhero.config.SecurityConfig.AuthUser) manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(name, input.password())).getPrincipal()).identity; }
        catch (BadCredentialsException | DisabledException | LockedException e) { throw new IdentityException(401, "BAD_CREDENTIALS", "用户名或密码不正确"); }
        var roles = mapper.roles(user.id());
        var principal = new SessionPrincipal(user.id(), user.username(), user.securityVersion(), roles);
        service.current(principal);
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
            roles.stream().map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList());
        strategy.onAuthentication(auth, request, response);
        var session = request.getSession(true);
        session.setAttribute(IdentitySessionFilter.LOGIN_TIME, clock.millis());
        session.setAttribute(IdentitySessionFilter.REMEMBER, Boolean.TRUE.equals(input.rememberMe()));
        session.setMaxInactiveInterval(Boolean.TRUE.equals(input.rememberMe()) ? 604800 : 7200);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return response(200, service.dto(user));
    }
    @GetMapping("/auth/me") public ResponseEntity<UserDto> me(Authentication auth) { return response(200, service.dto(service.current(principal(auth)))); }
    @GetMapping("/admin/identity") public ResponseEntity<UserDto> admin(Authentication auth) { return me(auth); }
    @PatchMapping("/account/profile") public ResponseEntity<UserDto> profile(@RequestBody Profile input, Authentication auth) { return response(200, service.profile(principal(auth), input.displayName())); }
    @PostMapping("/account/password") public ResponseEntity<Void> password(@RequestBody Password input, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        service.password(principal(auth), input.currentPassword(), input.newPassword());
        logoutSession(request, response);
        return response(204, null);
    }
    @PostMapping("/auth/logout") public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutSession(request, response); return response(204, null);
    }
    private void logoutSession(HttpServletRequest request, HttpServletResponse response) {
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
    private SessionPrincipal principal(Authentication auth) { return (SessionPrincipal) auth.getPrincipal(); }
    private <T> ResponseEntity<T> response(int status, T value) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value); }
}
