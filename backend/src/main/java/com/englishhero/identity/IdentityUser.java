package com.englishhero.identity;

public record IdentityUser(long id, String username, String displayName, String passwordHash,
                           boolean enabled, int securityVersion, String email, java.time.LocalDateTime emailVerifiedAt) {
    public IdentityUser(long id, String username, String displayName, String passwordHash, boolean enabled, int securityVersion) {
        this(id, username, displayName, passwordHash, enabled, securityVersion, null, null);
    }
}
