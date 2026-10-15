package com.englishhero.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

/** Only these non-secret fields are stored in JDBC session attributes. */
public record SessionPrincipal(long userId, String username, int securityVersion, List<String> roles)
        implements Principal, Serializable {
    @Override public String getName() { return username; }
}
