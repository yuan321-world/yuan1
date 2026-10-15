package com.englishhero.identity;

public record IdentityUser(long id, String username, String displayName, String passwordHash,
                           boolean enabled, int securityVersion) {}
