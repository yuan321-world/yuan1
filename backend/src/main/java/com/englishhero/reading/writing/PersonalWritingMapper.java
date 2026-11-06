package com.englishhero.reading.writing;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PersonalWritingMapper {
 private final JdbcTemplate jdbc;public PersonalWritingMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Favorite(boolean active,String snapshot){}
 public record Imitation(String text,int materialRevision,String snapshot,int lockVersion){}
 public void lockUser(long user){jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,user);}
 public java.util.Map<String,Boolean> states(long user,long article,long version){var result=new HashMap<String,Boolean>();jdbc.query("SELECT f.material_id FROM user_writing_favorite f JOIN reading_writing_material m ON m.id=f.material_id WHERE f.user_id=? AND f.active=TRUE AND m.article_id=? AND m.article_version_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getString(1),true),user,article,version);return result;}
 public Favorite favorite(long user,long material,boolean lock){return jdbc.query("SELECT active,content_snapshot_json FROM user_writing_favorite WHERE user_id=? AND material_id=?"+(lock?" FOR UPDATE":""),(r,n)->new Favorite(r.getBoolean(1),r.getString(2)),user,material).stream().findFirst().orElse(null);}
 public Imitation imitation(long user,long material,boolean lock){return jdbc.query("SELECT text_content,material_revision,content_snapshot_json,lock_version FROM user_writing_imitation WHERE user_id=? AND material_id=?"+(lock?" FOR UPDATE":""),(r,n)->new Imitation(r.getString(1),r.getInt(2),r.getString(3),r.getInt(4)),user,material).stream().findFirst().orElse(null);}
 public void favorite(long user,long material,String snapshot){jdbc.update("INSERT INTO user_writing_favorite(user_id,material_id,content_snapshot_json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE active=TRUE,content_snapshot_json=?,updated_at=UTC_TIMESTAMP(6)",user,material,snapshot,snapshot);}
 public void cancel(long user,long material){jdbc.update("UPDATE user_writing_favorite SET active=FALSE,updated_at=UTC_TIMESTAMP(6) WHERE user_id=? AND material_id=? AND active=TRUE",user,material);}
 public void save(long user,long material,String text,int revision,String snapshot,boolean exists){if(exists)jdbc.update("UPDATE user_writing_imitation SET text_content=?,lock_version=lock_version+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=? AND material_id=?",text,user,material);else jdbc.update("INSERT INTO user_writing_imitation(user_id,material_id,text_content,material_revision,content_snapshot_json) VALUES(?,?,?,?,?)",user,material,text,revision,snapshot);}
 public List<Long> ids(long user,String view,int page,int size){String table=view.equals("favorites")?"user_writing_favorite":"user_writing_imitation";return jdbc.queryForList("SELECT material_id FROM "+table+" WHERE user_id=?"+(view.equals("favorites")?" AND active=TRUE":"")+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",Long.class,user,size,(page-1)*size);}
 public long count(long user,String view){return jdbc.queryForObject("SELECT COUNT(*) FROM "+(view.equals("favorites")?"user_writing_favorite":"user_writing_imitation")+" WHERE user_id=?"+(view.equals("favorites")?" AND active=TRUE":""),Long.class,user);}
}
