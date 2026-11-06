package com.englishhero.identity.admin;
import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AdminUserMapper {
 private final JdbcTemplate jdbc;
 public AdminUserMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 JdbcTemplate sql(){return jdbc;}
 record Row(long id,String username,String displayName,boolean enabled,int version,Instant createdAt,String email,boolean emailVerified){}
 Row row(long id,boolean detail){return jdbc.query("SELECT id,username,display_name,enabled,security_version,created_at,"+(detail?"email,email_verified_at":"NULL AS email,NULL AS email_verified_at")+" FROM identity_user WHERE id=?",(r,n)->new Row(r.getLong("id"),r.getString("username"),r.getString("display_name"),r.getBoolean("enabled"),r.getInt("security_version"),r.getTimestamp("created_at").toInstant(),r.getString("email"),r.getTimestamp("email_verified_at")!=null),id).stream().findFirst().orElseThrow(AdminUserRules::missing);}
 List<String> roles(long id){return jdbc.queryForList("SELECT role FROM identity_user_role WHERE user_id=? ORDER BY role",String.class,id);}
 void lock(long id){if(jdbc.queryForList("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,id).isEmpty())throw AdminUserRules.missing();}
 long insertCommand(long actor,String request,long target,String hash,boolean enabled,int version,Instant now){var key=new org.springframework.jdbc.support.GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO admin_user_command(actor_id,request_id,target_id,request_hash,result_enabled,result_version,created_at) VALUES(?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);p.setLong(1,actor);p.setString(2,request);p.setLong(3,target);p.setString(4,hash);p.setBoolean(5,enabled);p.setInt(6,version);p.setTimestamp(7,java.sql.Timestamp.from(now));return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public void insertAudit(long command,long actor,long target,boolean before,boolean after,int version,String reason,Instant now){jdbc.update("INSERT INTO admin_user_audit(command_id,actor_id,target_id,before_enabled,after_enabled,before_version,after_version,reason,created_at) VALUES(?,?,?,?,?,?,?,?,?)",command,actor,target,before,after,version,version+1,reason,java.sql.Timestamp.from(now));}
}
