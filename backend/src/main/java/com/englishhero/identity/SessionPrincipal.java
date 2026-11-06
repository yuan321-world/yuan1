package com.englishhero.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

/** Non-secret business principal, rebuilt from verified JWT, MySQL account and Redis family. */
public record SessionPrincipal(long userId, String username, int securityVersion, List<String> roles)
        implements Principal, Serializable {
    @Override public String getName() { return username; }
}
