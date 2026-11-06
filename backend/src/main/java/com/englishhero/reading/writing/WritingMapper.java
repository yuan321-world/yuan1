package com.englishhero.reading.writing;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
@Repository
public class WritingMapper {
 private final JdbcTemplate jdbc;
 public WritingMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Row(long id,long articleId,long versionId,String draft,String published,String status,int revision,int lockVersion){}
 private static final RowMapper<Row> ROW=(r,n)->new Row(r.getLong("id"),r.getLong("article_id"),r.getLong("article_version_id"),r.getString("content_json"),r.getString("published_content_json"),r.getString("status"),r.getInt("revision"),r.getInt("lock_version"));
 public Row get(long id,boolean lock){return jdbc.query("SELECT * FROM reading_writing_material WHERE id=?"+(lock?" FOR UPDATE":""),ROW,id).stream().findFirst().orElse(null);}
 public List<Row> list(long article,long version,boolean published){return jdbc.query("SELECT * FROM reading_writing_material WHERE article_id=? AND article_version_id=? AND "+(published?"status='PUBLISHED'":"status<>'ARCHIVED'")+" ORDER BY id",ROW,article,version);}
 public long count(long article,long version){return jdbc.queryForObject("SELECT COUNT(*) FROM reading_writing_material WHERE article_id=? AND article_version_id=? AND status<>'ARCHIVED'",Long.class,article,version);}
 public long create(long article,long version,String content,long actor){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement("INSERT INTO reading_writing_material(article_id,article_version_id,content_json,created_by,updated_by) VALUES(?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);s.setLong(1,article);s.setLong(2,version);s.setString(3,content);s.setLong(4,actor);s.setLong(5,actor);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public void save(long id,String content,long actor){jdbc.update("UPDATE reading_writing_material SET content_json=?,updated_by=?,updated_at=UTC_TIMESTAMP(6),lock_version=lock_version+1 WHERE id=?",content,actor,id);}
 public void status(long id,String status,long actor){
  if(status.equals("PUBLISHED"))jdbc.update("UPDATE reading_writing_material SET published_content_json=content_json,status='PUBLISHED',revision=revision+1,lock_version=lock_version+1,updated_by=?,published_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",actor,id);
  else jdbc.update("UPDATE reading_writing_material SET status=?,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",status,actor,id);
 }
}
