package com.englishhero.identity.token;

import java.time.*;
import com.englishhero.identity.*;
import com.englishhero.identity.email.PersistentRateLimiter;
import com.englishhero.config.SecurityConfig.AuthUser;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;

@Service
public class TokenAuthenticationService {
    private final IdentityService users; private final IdentityMapper mapper;
    private final AuthenticationManager manager; private final PersistentRateLimiter limits;
    private final JwtService jwt; private final RefreshTokenCodec codec; private final RedisFamilyRepository families;private final Clock clock;
    public TokenAuthenticationService(IdentityService users,IdentityMapper mapper,AuthenticationManager manager,PersistentRateLimiter limits,
            JwtService jwt,RefreshTokenCodec codec,RedisFamilyRepository families,Clock clock){
        this.users=users;this.mapper=mapper;this.manager=manager;this.limits=limits;this.jwt=jwt;this.codec=codec;this.families=families;this.clock=clock;
    }
    public record TokenIssue(AuthTokens tokens,String refreshCredential,LoginFamily family) {
        @Override public String toString(){return "TokenIssue[redacted]";}
    }
    public TokenIssue login(String username,String password,boolean remember,String oldCredential,String ip) {
        String name=IdentityRules.username(username,false);limits.loginAttempt(name,ip);IdentityRules.password(password,"password");
        IdentityUser user;
        try {user=((AuthUser)manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(name,password)).getPrincipal()).identity;}
        catch(BadCredentialsException|DisabledException|LockedException e){throw new IdentityException(401,"BAD_CREDENTIALS","用户名或密码不正确");}
        var principal=principal(user.id(),user.securityVersion());
        var refresh=codec.create();var family=LoginFamily.start(refresh.sid(),user.id(),user.securityVersion(),remember,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        var access=jwt.issue(principal,family);
        // Verify/revoke the prior cookie without trusting a supplied sid. Infrastructure failures abort login.
        if(oldCredential!=null)logout(oldCredential,null);
        families.create(family,codec.digest(refresh.secret()));
        return issue(user,access,refresh.value(),family);
    }
    public TokenIssue refresh(String value) {
        var old=codec.parse(value);var f=families.find(old.sid());
        var principal=principal(f.userId(),f.securityVersion());
        var user=users.current(principal);
        var next=codec.next(f.sid());var access=jwt.issue(principal,f);
        var result=families.rotate(f.sid(),codec.digest(old.secret()),codec.digest(next.secret()),f.userId(),f.securityVersion(),clock.instant());
        if(result.status()!=RedisFamilyRepository.RotationStatus.SUCCESS)throw TokenErrors.invalid();
        return issue(user,access,next.value(),f);
    }
    private TokenIssue issue(IdentityUser user,JwtService.IssuedAccess access,String refresh,LoginFamily f){
        return new TokenIssue(new AuthTokens(users.dto(user),access.value(),"Bearer",access.expiresIn()),refresh,f);
    }
    private SessionPrincipal principal(long uid,int sv) {
        var user=mapper.byId(uid);
        if(user==null||!user.enabled()||user.securityVersion()!=sv)throw TokenErrors.invalid();
        return new SessionPrincipal(uid,user.username(),sv,mapper.roles(uid));
    }
    public SessionPrincipal authenticate(String value) {
        var access=jwt.verifySignatureAndClaims(value);var principal=principal(access.userId(),access.securityVersion());
        // Expired access must not extend idle life, nor hide revocation behind an expiry response.
        boolean expired=!clock.instant().isBefore(access.expiresAt());
        families.validate(access.sid(),access.userId(),access.securityVersion(),clock.instant(),!expired);
        if(expired)throw new IdentityException(401,"ACCESS_TOKEN_EXPIRED","登录凭据需要续期");
        return principal;
    }
    public void logout(String refresh,String bearer) {
        if(refresh!=null) {
            try {var value=codec.parse(refresh);families.revokeRefresh(value.sid(),codec.digest(value.secret()));}
            catch(IdentityException e){if(e.status!=401)throw e;}
        }
        if(bearer!=null) {
            try {
                var value=jwt.verifySignatureAndClaims(bearer);
                if(clock.instant().isBefore(value.expiresAt())) {
                    principal(value.userId(),value.securityVersion());
                    families.validate(value.sid(),value.userId(),value.securityVersion(),clock.instant(),false);
                    families.revokeVerifiedAccess(value);
                }
            } catch(IdentityException e){if(e.status!=401)throw e;}
        }
    }
}
