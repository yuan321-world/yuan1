package com.englishhero.reading.quiz;

import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.reading.quiz.QuizDtos.*;
@Repository
public class QuizMapper {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public QuizMapper(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Row(long id,long articleId,long articleVersionId,String draft,Long publishedVersionId,String status,int revision,int lockVersion){}
 public record Question(long id,long versionId,String key,String type,String stem,List<String> options,String answer,String explanation){}
 private final RowMapper<Row> row=(r,n)->new Row(r.getLong("id"),r.getLong("article_id"),r.getLong("article_version_id"),r.getString("draft_json"),r.getObject("published_version_id",Long.class),r.getString("status"),r.getInt("revision"),r.getInt("lock_version"));
 public Row article(long a,long v,boolean lock){return jdbc.query("SELECT * FROM reading_quiz WHERE article_id=? AND article_version_id=? AND `usage`='ARTICLE'"+(lock?" FOR UPDATE":""),row,a,v).stream().findFirst().orElse(null);}
 public Row get(long id,boolean lock){return jdbc.query("SELECT * FROM reading_quiz WHERE id=? AND `usage`='ARTICLE'"+(lock?" FOR UPDATE":""),row,id).stream().findFirst().orElse(null);}
 private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public void create(long a,long v,String draft,long actor){insert("INSERT INTO reading_quiz(article_id,article_version_id,draft_json,created_by,updated_by) VALUES(?,?,?,?,?)",a,v,draft,actor,actor);}
 public void save(long id,String draft,long actor){jdbc.update("UPDATE reading_quiz SET draft_json=?,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",draft,actor,id);}
 public void publish(Row r,List<QuestionInput> questions,long actor){long v=insert("INSERT INTO reading_quiz_version(quiz_id,version_no,created_by) VALUES(?,?,?)",r.id(),r.revision()+1,actor);for(var q:questions)jdbc.update("INSERT INTO reading_question(quiz_version_id,question_key,question_type,stem,options_json,correct_option,explanation,sort_order) VALUES(?,?,?,?,?,?,?,?)",v,q.key(),q.type(),q.stem(),json.writeValueAsString(q.options()),q.correctOption(),q.explanation(),q.sortOrder());jdbc.update("UPDATE reading_quiz SET status='PUBLISHED',published_version_id=?,revision=revision+1,lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",v,actor,r.id());}
 public void unpublish(long id,long actor){jdbc.update("UPDATE reading_quiz SET status='UNLISTED',lock_version=lock_version+1,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",actor,id);}
 public List<QuestionInput> draft(Row r){return Arrays.asList(json.readValue(r.draft(),QuestionInput[].class));}
 public List<Question> questions(long version){return questions(version,false);}
 public List<Question> questions(long version,boolean current){return jdbc.query("SELECT * FROM reading_question WHERE quiz_version_id=? ORDER BY sort_order,id"+(current?" FOR SHARE":""),(r,n)->new Question(r.getLong("id"),r.getLong("quiz_version_id"),r.getString("question_key"),r.getString("question_type"),r.getString("stem"),Arrays.asList(json.readValue(r.getString("options_json"),String[].class)),r.getString("correct_option"),r.getString("explanation")),version);}
}
