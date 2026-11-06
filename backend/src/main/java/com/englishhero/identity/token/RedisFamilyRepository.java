package com.englishhero.identity.token;

import java.time.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataAccessException;

@Repository
public class RedisFamilyRepository {
    private final StringRedisTemplate redis; private final String prefix;
    private final DefaultRedisScript<Long> create=script("create"),touch=script("touch"),rotate=script("rotate"),revoke=script("revoke");
    public RedisFamilyRepository(StringRedisTemplate redis,AuthProperties p){this.redis=redis;prefix=p.getNamespace();}
    private static DefaultRedisScript<Long> script(String name){var s=new DefaultRedisScript<Long>();s.setLocation(new ClassPathResource("redis/auth-"+name+".lua"));s.setResultType(Long.class);return s;}
    private void sid(String sid){if(sid==null || !sid.matches("[a-f0-9]{32}"))throw TokenErrors.invalid();}
    public String familyKey(String sid){sid(sid);return prefix+":{"+sid+"}:family";}
    public String usedKey(String sid,String digest){if(!digest.matches("[a-f0-9]{64}"))throw TokenErrors.invalid();return prefix+":{"+sid+"}:used:"+digest;}
    private long execute(DefaultRedisScript<Long> s,List<String> keys,String...args){
        try {var result=redis.execute(s,keys,(Object[])args);if(result==null)throw TokenErrors.unavailable();return result;}
        catch(DataAccessException e){throw TokenErrors.unavailable();}
    }
    public void create(LoginFamily f,String digest) {
        long result=execute(create,List.of(familyKey(f.sid())),Long.toString(f.userId()),Integer.toString(f.securityVersion()),
            Long.toString(f.createdAt().getEpochSecond()),Long.toString(f.absoluteExpiresAt().getEpochSecond()),Long.toString(f.idleSeconds()),Boolean.toString(f.remember()),digest);
        if(result!=1)throw TokenErrors.unavailable();
    }
    public LoginFamily find(String sid) {
        try {
            Map<Object,Object> m=redis.opsForHash().entries(familyKey(sid));
            if(m.isEmpty())throw TokenErrors.invalid();
            return new LoginFamily(sid,Long.parseLong((String)m.get("userId")),Integer.parseInt((String)m.get("sv")),instant(m,"createdAt"),instant(m,"lastSeen"),instant(m,"absoluteExpiresAt"),Long.parseLong((String)m.get("idleSeconds")),Boolean.parseBoolean((String)m.get("remember")),(String)m.get("state"));
        } catch(DataAccessException e){throw TokenErrors.unavailable();}
    }
    private Instant instant(Map<Object,Object> m,String name){return Instant.ofEpochSecond(Long.parseLong((String)m.get(name)));}
    public LoginFamily validateAndTouch(String sid,long uid,int sv,Instant now){return validate(sid,uid,sv,now,true);}
    public LoginFamily validate(String sid,long uid,int sv,Instant now,boolean activity) {
        if(execute(touch,List.of(familyKey(sid)),Long.toString(uid),Integer.toString(sv),Long.toString(now.getEpochSecond()),activity?"1":"0")!=1)throw TokenErrors.invalid();
        return find(sid);
    }
    public enum RotationStatus {SUCCESS,REPLAY,INVALID,INACTIVE}
    public record RotationResult(RotationStatus status) {}
    public RotationResult rotate(String sid,String presented,String next,long uid,int sv,Instant now) {
        long code=execute(rotate,List.of(familyKey(sid),usedKey(sid,presented)),Long.toString(uid),Integer.toString(sv),Long.toString(now.getEpochSecond()),presented,next);
        return new RotationResult(code==1?RotationStatus.SUCCESS:code==-2?RotationStatus.REPLAY:code==-1?RotationStatus.INVALID:RotationStatus.INACTIVE);
    }
    // Only service code with an already validated bearer can invoke this package-private path.
    void revokeVerifiedAccess(VerifiedAccess access) {execute(revoke,List.of(familyKey(access.sid()),usedKey(access.sid(),"0".repeat(64))),"bearer","");}
    public void revokeRefresh(String sid,String digest) {execute(revoke,List.of(familyKey(sid),usedKey(sid,digest)),"refresh",digest);}
}
