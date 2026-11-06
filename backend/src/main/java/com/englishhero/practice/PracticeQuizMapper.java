package com.englishhero.practice;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.reading.quiz.QuizMapper;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
@Repository
public class PracticeQuizMapper {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final QuizMapper shared;
 public PracticeQuizMapper(JdbcTemplate jdbc,ObjectMapper json,QuizMapper shared){this.jdbc=jdbc;this.json=json;this.shared=shared;}
 public QuizMapper.Row get(long id,boolean lock){return jdbc.query("SELECT * FROM reading_quiz WHERE id=? AND `usage`='PRACTICE'"+(lock?" FOR UPDATE":""),(r,n)->new QuizMapper.Row(r.getLong("id"),r.getLong("article_id"),r.getLong("article_version_id"),r.getString("draft_json"),r.getObject("published_version_id",Long.class),r.getString("status"),r.getInt("revision"),r.getInt("lock_version")),id).stream().findFirst().orElse(null);}
 public long draft(long article,long version,List<QuestionInput> qs,long actor){var ids=jdbc.queryForList("SELECT id FROM reading_quiz WHERE article_id=? AND article_version_id=? AND `usage`='PRACTICE' FOR UPDATE",Long.class,article,version);if(!ids.isEmpty()){shared.save(ids.getFirst(),json.writeValueAsString(qs),actor);return ids.getFirst();}var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement("INSERT INTO reading_quiz(article_id,article_version_id,`usage`,draft_json,created_by,updated_by) VALUES(?,?,'PRACTICE',?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);s.setLong(1,article);s.setLong(2,version);s.setString(3,json.writeValueAsString(qs));s.setLong(4,actor);s.setLong(5,actor);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
 public List<QuestionInput> questions(QuizMapper.Row row){return shared.draft(row);}
 public long publish(QuizMapper.Row row,List<QuestionInput> questions,long actor){shared.publish(row,questions,actor);return get(row.id(),false).publishedVersionId();}
 public List<QuizMapper.Question> published(long version){return shared.questions(version);}
}
