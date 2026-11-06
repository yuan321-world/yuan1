package com.englishhero.vocabulary.phrase;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.vocabulary.phrase.PhraseDtos.*;
@Repository
public class PhraseMapper {
 final JdbcTemplate jdbc;final ObjectMapper json;
 public PhraseMapper(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public JdbcTemplate jdbc(){return jdbc;}
 void lock(long u){jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,u);}
 String encode(Object v){return json.writeValueAsString(v);}
 long inserted(){return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
 List<Source> sources(String id){return jdbc.query("SELECT * FROM vocabulary_phrase_source WHERE phrase_id=? ORDER BY id",(r,n)->new Source(r.getString("id"),r.getString("article_id"),r.getString("article_version_id"),r.getString("title_snapshot"),r.getString("sentence_key"),r.getInt("start_offset"),r.getInt("end_offset"),r.getString("sentence_snapshot"),r.getString("practice_attempt_id"),r.getString("paper_attempt_id"),r.getString("paper_slot")),id);}
 Phrase phrase(long u,long id){return jdbc.query("SELECT * FROM vocabulary_phrase WHERE user_id=? AND id=?",(r,n)->new Phrase(r.getString("id"),r.getString("display_text"),r.getString("meaning_zh"),r.getString("note"),r.getBoolean("active"),r.getBoolean("enrolled"),r.getBoolean("exempt"),r.getInt("independent_passes"),sources(r.getString("id")),r.getInt("stage"),r.getTimestamp("due_at")==null?null:r.getTimestamp("due_at").toInstant(),r.getString("rule_version")),u,id).stream().findFirst().orElse(null);}
 Round round(long u,long id){return jdbc.query("SELECT state_json FROM vocabulary_phrase_round WHERE user_id=? AND id=?",(r,n)->json.readValue(r.getString(1),Round.class),u,id).stream().findFirst().orElse(null);}
 Round current(long u){return jdbc.query("SELECT state_json FROM vocabulary_phrase_round WHERE user_id=? AND status<>'COMPLETED' ORDER BY id DESC LIMIT 1",(r,n)->json.readValue(r.getString(1),Round.class),u).stream().findFirst().orElse(null);}
 void save(long u,Round r){jdbc.update("UPDATE vocabulary_phrase_round SET status=?,state_json=? WHERE user_id=? AND id=?",r.status(),encode(r),u,r.id());}
 Round replay(long u,String request,String hash){return jdbc.query("SELECT request_hash,response_json FROM vocabulary_phrase_command WHERE user_id=? AND request_id=?",(r,n)->{if(!hash.equals(r.getString(1)))throw PhraseService.conflict();return json.readValue(r.getString(2),Round.class);},u,request).stream().findFirst().orElse(null);}
 void remember(long u,String request,String hash,Round result){jdbc.update("INSERT INTO vocabulary_phrase_command(user_id,request_id,request_hash,response_json) VALUES(?,?,?,?)",u,request,hash,encode(result));}
}
