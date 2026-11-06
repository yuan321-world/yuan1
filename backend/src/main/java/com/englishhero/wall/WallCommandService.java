package com.englishhero.wall;
import com.englishhero.identity.*;
import com.englishhero.web.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
@Service
public class WallCommandService {
 private final WallMapper mapper;private final IdentityService identities;private final IdentityMapper users;private final tools.jackson.databind.ObjectMapper json;public final Clock clock;
 private final int messageDaily,commentDaily,reportDaily,messageCooldown,commentCooldown;
 public WallCommandService(WallMapper mapper,IdentityService identities,IdentityMapper users,tools.jackson.databind.ObjectMapper json,Clock clock,@Value("${english-hero.wall.message-daily:10}")int md,@Value("${english-hero.wall.comment-daily:60}")int cd,@Value("${english-hero.wall.report-daily:10}")int rd,@Value("${english-hero.wall.message-cooldown-seconds:30}")int mc,@Value("${english-hero.wall.comment-cooldown-seconds:5}")int cc){this.mapper=mapper;this.identities=identities;this.users=users;this.json=json;this.clock=clock;messageDaily=md;commentDaily=cd;reportDaily=rd;messageCooldown=mc;commentCooldown=cc;}
 public long actor(SessionPrincipal actor){if(actor==null)throw new BusinessException(401,"UNAUTHENTICATED","请先登录");identities.current(actor);return actor.userId();}
 public boolean admin(SessionPrincipal actor){return users.roles(actor(actor)).contains("ADMIN");}
 public void requireAdmin(SessionPrincipal actor){if(!admin(actor))throw new BusinessException(403,"FORBIDDEN","没有管理权限");}
 public void lock(long actor){mapper.sql().queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,actor);}
 public <T>T execute(SessionPrincipal actor,String uuid,String operation,Object payload,Class<T> type,Supplier<T> work){
  long user=actor(actor);WallRules.uuid(uuid);lock(user);String serialized=json.writeValueAsString(payload),hash=hash(operation+"\n"+serialized);
  var found=mapper.sql().queryForList("SELECT request_hash,result_json FROM wall_request WHERE user_id=? AND request_id=? FOR UPDATE",user,uuid);
  if(!found.isEmpty()){var row=found.getFirst();if(!hash.equals(row.get("request_hash")))throw new BusinessException(409,"WALL_REQUEST_CONFLICT","请求已使用，请重新操作");return json.readValue(row.get("result_json").toString(),type);}
  T result=work.get();String body=json.writeValueAsString(result);var tree=json.readTree(body);Long target=tree.has("id")?Long.valueOf(tree.get("id").asText()):null;
  mapper.sql().update("INSERT INTO wall_request(user_id,request_id,operation,request_hash,result_json,target_id,created_at) VALUES(?,?,?,?,?,?,?)",user,uuid,operation,hash,body,target,Timestamp.from(clock.instant()));return result;
 }
 public Object status(SessionPrincipal actor,String id){long uid=actor(actor);WallRules.uuid(id);return mapper.sql().query("SELECT operation,target_id FROM wall_request WHERE user_id=? AND request_id=?",(r,n)->Map.of("completed",true,"operation",r.getString(1),"targetId",r.getString(2)==null?"":r.getString(2)),uid,id).stream().findFirst().orElseThrow(WallRules::missing);}
 public void quota(long uid,String kind){
  LocalDate day=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();mapper.sql().update("INSERT INTO wall_user_quota(user_id,quota_day) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=user_id",uid,day);
  var row=mapper.sql().queryForMap("SELECT * FROM wall_user_quota WHERE user_id=? FOR UPDATE",uid);if(!row.get("quota_day").toString().equals(day.toString())){mapper.sql().update("UPDATE wall_user_quota SET quota_day=?,message_count=0,comment_count=0,report_count=0 WHERE user_id=?",day,uid);row.put("message_count",0);row.put("comment_count",0);row.put("report_count",0);}
  String column=switch(kind){case "MESSAGE"->"message";case "COMMENT"->"comment";case "REPORT"->"report";default->throw new IllegalArgumentException();};
  int limit=kind.equals("MESSAGE")?messageDaily:kind.equals("COMMENT")?commentDaily:reportDaily,cooldown=kind.equals("MESSAGE")?messageCooldown:kind.equals("COMMENT")?commentCooldown:0;
  boolean daily=((Number)row.get(column+"_count")).intValue()>=limit;Object last=kind.equals("REPORT")?null:row.get("last_"+column);Instant previous=last instanceof Timestamp t?t.toInstant():last instanceof LocalDateTime l?l.toInstant(ZoneOffset.UTC):null;
  if(daily||(previous!=null&&clock.instant().isBefore(previous.plusSeconds(cooldown)))){long retry=daily?Duration.between(clock.instant(),day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()).toSeconds():Duration.between(clock.instant(),previous.plusSeconds(cooldown)).toSeconds()+1;throw new WallRateLimitException(daily?"今日操作次数已用完，请明天再来":"操作有些频繁，请稍后再试",retry);}
  mapper.sql().update("UPDATE wall_user_quota SET "+column+"_count="+column+"_count+1"+(kind.equals("REPORT")?"":",last_"+column+"=?")+" WHERE user_id=?",kind.equals("REPORT")?new Object[]{uid}:new Object[]{Timestamp.from(clock.instant()),uid});
 }
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
