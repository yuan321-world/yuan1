package com.englishhero.identity.token;
import java.time.Instant;
/** Signature/claims verified only. This value does not confer authentication. */
public record VerifiedAccess(long userId,String sid,int securityVersion,Instant expiresAt) {}
