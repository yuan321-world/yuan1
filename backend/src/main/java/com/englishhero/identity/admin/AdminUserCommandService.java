package com.englishhero.identity.admin;
import java.time.Clock;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.web.BusinessException;
import static com.englishhero.identity.admin.AdminUserDtos.*;
@Service
public class AdminUserCommandService {
 private final AdminUserMapper mapper;private final AdminUserService users;private final Clock clock;
 public AdminUserCommandService(AdminUserMapper mapper,AdminUserService users,Clock clock){this.mapper=mapper;this.users=users;this.clock=clock;}
 private Receipt existing(long actor,String request,String hash){return mapper.sql().query("SELECT target_id,request_hash,result_enabled,result_version FROM admin_user_command WHERE actor_id=? AND request_id=?",(r,n)->{if(hash!=null&&!hash.equals(r.getString("request_hash")))throw new BusinessException(409,"ADMIN_USER_REQUEST_CONFLICT","请求已用于其他操作，请重新确认");return new Receipt(request,r.getString("target_id"),r.getBoolean("result_enabled"),r.getInt("result_version"),true);},actor,request).stream().findFirst().orElse(null);}
 @Transactional(readOnly=true)public Receipt status(SessionPrincipal actor,String request){long uid=users.requireAdmin(actor);var r=existing(uid,AdminUserRules.uuid(request),null);if(r==null)throw AdminUserRules.missing();return r;}
 @Transactional(isolation=Isolation.READ_COMMITTED)public Receipt apply(SessionPrincipal actor,String id,StatusInput input){long uid=users.requireAdmin(actor),target=AdminUserRules.id(id,"id");if(input==null||input.enabled()==null)throw BusinessException.invalid("enabled","请选择账号状态");String request=AdminUserRules.uuid(input.requestId()),reason=AdminUserRules.text(input.reason(),"reason",200,true);if(input.expectedVersion()==null||input.expectedVersion()<0||input.expectedVersion()>=Integer.MAX_VALUE)throw BusinessException.invalid("expectedVersion","账号版本无效，请重新加载");String hash=hash(target+"\n"+input.expectedVersion()+"\n"+input.enabled()+"\n"+reason);
 // Stable account lock order serializes command UUIDs without a Redis-wide scan or absent-row gap locks.
 for(long lock:new TreeSet<>(List.of(uid,target)))mapper.lock(lock);users.requireAdmin(actor);
 var previous=existing(uid,request,hash);if(previous!=null)return previous;
 if(target==uid||mapper.roles(target).contains("ADMIN"))throw AdminUserRules.forbidden();var row=mapper.row(target,false);if(row.version()!=input.expectedVersion()||row.enabled()==input.enabled())throw AdminUserRules.conflict();
 if(mapper.sql().update("UPDATE identity_user SET enabled=?,security_version=security_version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND security_version=?",input.enabled(),target,row.version())!=1)throw AdminUserRules.conflict();var now=clock.instant();long command=mapper.insertCommand(uid,request,target,hash,input.enabled(),row.version()+1,now);mapper.insertAudit(command,uid,target,row.enabled(),input.enabled(),row.version(),reason,now);return new Receipt(request,id,input.enabled(),row.version()+1,true);
 }
 private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
