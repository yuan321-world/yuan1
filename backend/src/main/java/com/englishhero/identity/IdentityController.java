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
    public record Registration(String username, String password, String displayName, String email, String challengeId, String code) {}
    public record EmailCode(String purpose, String email, String username) {}
    public record EmailBinding(String email, String currentPassword, String challengeId, String code) {}
    public record EmailBindingCode(String email, String currentPassword) {}
    public record PasswordReset(String username, String email, String challengeId, String code, String newPassword) {}
    public record Login(String username, String password, Boolean rememberMe) {}
    public record Profile(String displayName) {}
    public record Password(String currentPassword, String newPassword) {}
    public record CsrfDto(String token, String headerName) {}
    private final IdentityService service;
    private final IdentityEmailService emails;
    private final com.englishhero.identity.token.TokenAuthenticationService tokens;
    private final com.englishhero.identity.token.RefreshCookieService cookies;
    private final org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository;
    public IdentityController(IdentityService service, IdentityEmailService emails,
            com.englishhero.identity.token.TokenAuthenticationService tokens,
            com.englishhero.identity.token.RefreshCookieService cookies,
            org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository) {
        this.service=service;this.emails=emails;this.tokens=tokens;this.cookies=cookies;this.csrfRepository=csrfRepository;
    }
    @GetMapping("/auth/csrf") public ResponseEntity<CsrfDto> csrf(HttpServletRequest request) {
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return response(200, new CsrfDto(token.getToken(), token.getHeaderName()));
    }
    @PostMapping("/auth/register") public ResponseEntity<UserDto> register(@RequestBody Registration input) {
        return response(201, emails.register(input.username(), input.password(), input.displayName(), input.email(), input.challengeId(), input.code()));
    }
    @PostMapping("/auth/email-codes") public ResponseEntity<?> emailCodes(@RequestBody EmailCode input, HttpServletRequest request) {
        return response(202, emails.issuePublic(input.purpose(), input.email(), input.username(), request.getRemoteAddr()));
    }
    @PostMapping("/auth/password-reset") public ResponseEntity<Void> reset(@RequestBody PasswordReset input) {
        emails.reset(input.username(), input.email(), input.challengeId(), input.code(), input.newPassword());
        return response(204, null);
    }
    @PostMapping("/account/email-codes") public ResponseEntity<?> bindingCode(@RequestBody EmailBindingCode input, Authentication auth, HttpServletRequest request) {
        return response(202, emails.issueBinding(principal(auth), input.email(), input.currentPassword(), request.getRemoteAddr()));
    }
    @PostMapping("/account/email") public ResponseEntity<UserDto> bind(@RequestBody EmailBinding input, Authentication auth, HttpServletRequest request) {
        return response(200, emails.bind(principal(auth), input.email(), input.currentPassword(), input.challengeId(), input.code(), request.getRemoteAddr()));
    }
    @GetMapping("/account/email-codes/{challengeId}") public ResponseEntity<?> bindingStatus(@PathVariable String challengeId, Authentication auth) {
        return response(200, emails.bindingStatus(principal(auth), challengeId));
    }
    @PostMapping("/auth/login") public ResponseEntity<com.englishhero.identity.token.AuthTokens> login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        var issue=tokens.login(input.username(),input.password(),Boolean.TRUE.equals(input.rememberMe()),cookies.read(request),request.getRemoteAddr());
        cookies.write(response,issue);csrfRepository.saveToken(null,request,response);
        return response(200,issue.tokens());
    }
    @PostMapping("/auth/refresh") public ResponseEntity<com.englishhero.identity.token.AuthTokens> refresh(HttpServletRequest request,HttpServletResponse response) {
        var issue=tokens.refresh(cookies.read(request));cookies.write(response,issue);
        return response(200,issue.tokens());
    }
    @GetMapping("/auth/me") public ResponseEntity<UserDto> me(Authentication auth) { return response(200, service.dto(service.current(principal(auth)))); }
    @GetMapping("/admin/identity") public ResponseEntity<UserDto> admin(Authentication auth) { return me(auth); }
    @PatchMapping("/account/profile") public ResponseEntity<UserDto> profile(@RequestBody Profile input, Authentication auth) { return response(200, service.profile(principal(auth), input.displayName())); }
    @PostMapping("/account/password") public ResponseEntity<Void> password(@RequestBody Password input, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        service.password(principal(auth), input.currentPassword(), input.newPassword());
        // security_version has committed; Redis cleanup cannot roll back the new password.
        try { tokens.logout(cookies.read(request), null); }
        catch (IdentityException e) { if (e.status != 503) throw e; }
        cookies.clear(response);csrfRepository.saveToken(null,request,response);SecurityContextHolder.clearContext();
        return response(204, null);
    }
    @PostMapping("/auth/logout") public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        clearCredentials(request, response); return response(204, null);
    }
    private void clearCredentials(HttpServletRequest request, HttpServletResponse response) {
        tokens.logout(cookies.read(request),com.englishhero.identity.token.JwtIdentityFilter.bearer(request));
        cookies.clear(response);csrfRepository.saveToken(null,request,response);SecurityContextHolder.clearContext();
    }
    private SessionPrincipal principal(Authentication auth) { return (SessionPrincipal) auth.getPrincipal(); }
    private <T> ResponseEntity<T> response(int status, T value) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value); }
}
