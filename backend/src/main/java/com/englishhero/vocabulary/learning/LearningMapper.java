package com.englishhero.vocabulary.learning;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.vocabulary.learning.LearningDtos.*;

@Repository
public class LearningMapper {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public LearningMapper(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public JdbcTemplate jdbc(){return jdbc;}
 public record Session(long id,long userId,String kind,String mode,String status,String phase,int version,List<String> queue,String flowVersion,String reviewScope){}
 public record Item(long id,long wordId,Snapshot snapshot,boolean passed,int failures,Outcome outcome,String ruleVersion,int attemptId,boolean assisted,boolean proficient,int hintCount){}
 public String encode(Object value){return json.writeValueAsString(value);}
 public <T>T decode(String value,Class<T> type){return value==null?null:json.readValue(value,type);}
 public long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
 public void lock(long user){jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,user);}
 public long inserted(){return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
 public Settings settings(long user){return jdbc.query("SELECT * FROM vocabulary_learning_setting WHERE user_id=?",(r,n)->new Settings(r.getInt("new_goal"),r.getInt("review_goal"),r.getBoolean("auto_next"),r.getString("review_scope"),r.getInt("batch_size")),user).stream().findFirst().orElse(new Settings(20,100));}
 public Session session(long user,long id){return jdbc.query("SELECT * FROM vocabulary_learning_session WHERE user_id=? AND id=?",(r,n)->new Session(r.getLong("id"),user,r.getString("kind"),r.getString("mode"),r.getString("status"),r.getString("phase"),r.getInt("version"),new ArrayList<>(List.of(decode(r.getString("queue_json"),String[].class))),r.getString("flow_version"),r.getString("review_scope")),user,id).stream().findFirst().orElse(null);}
 public Long openId(long user){return jdbc.queryForList("SELECT id FROM vocabulary_learning_session WHERE user_id=? AND status<>'COMPLETED' ORDER BY (status='ACTIVE') DESC,id DESC LIMIT 1",Long.class,user).stream().findFirst().orElse(null);}
 public Long activeId(long user){return jdbc.queryForList("SELECT id FROM vocabulary_learning_session WHERE user_id=? AND status='ACTIVE'",Long.class,user).stream().findFirst().orElse(null);}
 public Long savedId(long user,String kind,String source){return jdbc.queryForList("SELECT id FROM vocabulary_learning_session WHERE user_id=? AND kind=? AND source_key=? AND status<>'COMPLETED'",Long.class,user,kind,source).stream().findFirst().orElse(null);}
 public List<Item> items(long user,long session){return jdbc.query("SELECT * FROM vocabulary_learning_item WHERE user_id=? AND session_id=? ORDER BY id",(r,n)->new Item(r.getLong("id"),r.getLong("learning_word_id"),decode(r.getString("snapshot"),Snapshot.class),r.getBoolean("passed"),r.getInt("failure_count"),decode(r.getString("outcome"),Outcome.class),r.getString("rule_version"),r.getInt("attempt_id"),r.getBoolean("assisted"),r.getBoolean("proficient"),r.getInt("hint_count")),user,session);}
 public Instant instant(String sql,Object...args){return jdbc.query(sql,(r,n)->r.getTimestamp(1)==null?null:r.getTimestamp(1).toInstant(),args).stream().filter(Objects::nonNull).findFirst().orElse(null);}
 public SessionDto replay(long user,String request,String hash){return jdbc.query("SELECT request_hash,response_json FROM vocabulary_learning_command WHERE user_id=? AND request_id=?",(r,n)->{
  if(!hash.equals(r.getString(1)))throw LearningService.conflict("相同操作编号不能用于不同请求，请重新加载");return decode(r.getString(2),SessionDto.class);
 },user,request).stream().findFirst().orElse(null);}
 public void command(long user,String request,String hash,SessionDto result,Instant now){jdbc.update("INSERT INTO vocabulary_learning_command(user_id,request_id,request_hash,response_json,created_at) VALUES(?,?,?,?,?)",user,request,hash,encode(result),Timestamp.from(now));}
 public void sessionUpdate(Session s,String status,String phase,List<String> queue,Instant completed){jdbc.update("UPDATE vocabulary_learning_session SET status=?,phase=?,queue_json=?,version=version+1,completed_at=? WHERE user_id=? AND id=?",status,phase,encode(queue),completed==null?null:Timestamp.from(completed),s.userId(),s.id());}
}
