package com.englishhero.identity.token;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class RefreshCookieService {
    public static final String NAME="EH_REFRESH";
    private final AuthProperties p;private final Clock clock;
    public RefreshCookieService(AuthProperties p,Clock clock){this.p=p;this.clock=clock;}
    public String read(HttpServletRequest r){if(r.getCookies()!=null)for(var c:r.getCookies())if(c.getName().equals(NAME))return c.getValue();return null;}
    public void write(HttpServletResponse r,TokenAuthenticationService.TokenIssue issue) {
        var cookie=base(issue.refreshCredential());
        if(issue.family().remember())cookie.maxAge(Math.max(0,Duration.between(clock.instant(),issue.family().absoluteExpiresAt()).getSeconds()));
        r.addHeader("Set-Cookie",cookie.build().toString());legacy(r);
    }
    public void clear(HttpServletResponse r){r.addHeader("Set-Cookie",base("").maxAge(0).build().toString());legacy(r);}
    private ResponseCookie.ResponseCookieBuilder base(String value){return ResponseCookie.from(NAME,value).httpOnly(true).secure(p.isSecure()).sameSite("Lax").path("/api/v1/auth");}
    private void legacy(HttpServletResponse r){r.addHeader("Set-Cookie",ResponseCookie.from("EH_SESSION","").httpOnly(true).secure(p.isSecure()).sameSite("Lax").path("/").maxAge(0).build().toString());}
}
