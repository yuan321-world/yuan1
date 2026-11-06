package com.englishhero.identity.token;
import java.time.Instant;
public record LoginFamily(String sid,long userId,int securityVersion,Instant createdAt,Instant lastSeen,
        Instant absoluteExpiresAt,long idleSeconds,boolean remember,String state) {
    public static LoginFamily start(String sid,long uid,int sv,boolean remember,Instant now) {
        return new LoginFamily(sid,uid,sv,now,now,now.plusSeconds(remember?604800:86400),remember?604800:7200,remember,"ACTIVE");
    }
}
