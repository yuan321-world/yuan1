package com.englishhero.reading.quiz;

import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
@Repository
public class AttemptMapper {
 private final JdbcTemplate jdbc;public AttemptMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Row(long id,long userId,long quizId,long quizVersionId,long articleId,long articleVersionId,int attemptNo,String status,int correctCount,int total,int lockVersion,Instant startedAt,Instant completedAt){}
 public record Answer(long questionId,String draftChoice,String submittedChoice,Boolean correct,Instant submittedAt){}
 private static final RowMapper<Row> ROW=(r,n)->new Row(r.getLong("id"),r.getLong("user_id"),r.getLong("quiz_id"),r.getLong("quiz_version_id"),r.getLong("article_id"),r.getLong("article_version_id"),r.getInt("attempt_no"),r.getString("status"),r.getInt("correct_count"),r.getInt("total_count"),r.getInt("lock_version"),r.getTimestamp("started_at").toInstant(),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant());
 public void lockUser(long id){jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,id);}
 public Row get(long owner,long id,boolean lock){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND id=?"+(lock?" FOR UPDATE":""),ROW,owner,id).stream().findFirst().orElse(null);}
 public Row request(long owner,String request){return jdbc.query("SELECT a.* FROM user_reading_attempt_request r JOIN user_reading_attempt a ON a.id=r.attempt_id AND a.user_id=r.user_id WHERE r.user_id=? AND r.client_request_id=? FOR UPDATE",ROW,owner,request).stream().findFirst().orElse(null);}
 public void remember(long owner,String request,long attempt){jdbc.update("INSERT INTO user_reading_attempt_request(user_id,client_request_id,attempt_id) VALUES(?,?,?)",owner,request,attempt);}
 public Row active(long owner,long quiz){return active(owner,quiz,false);}
 public Row active(long owner,long quiz,boolean lock){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND active_quiz_id=?"+(lock?" FOR UPDATE":""),ROW,owner,quiz).stream().findFirst().orElse(null);}
 public Row latestCompleted(long owner,long version){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND article_version_id=? AND status='COMPLETED' ORDER BY id DESC LIMIT 1",ROW,owner,version).stream().findFirst().orElse(null);}
 public Row first(long owner,long articleVersion){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND article_version_id=? AND status='COMPLETED' ORDER BY id LIMIT 1",ROW,owner,articleVersion).stream().findFirst().orElse(null);}
 public Row latest(long owner,long articleVersion){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND article_version_id=? ORDER BY id DESC LIMIT 1",ROW,owner,articleVersion).stream().findFirst().orElse(null);}
 public List<Row> history(long owner,long version,int page,int size){return jdbc.query("SELECT * FROM user_reading_attempt WHERE user_id=? AND article_version_id=? ORDER BY id DESC LIMIT ? OFFSET ?",ROW,owner,version,size,(long)(page-1)*size);}
 public long count(long owner,long version){return jdbc.queryForObject("SELECT COUNT(*) FROM user_reading_attempt WHERE user_id=? AND article_version_id=?",Long.class,owner,version);}
 public long create(long owner,QuizMapper.Row quiz,String request,List<QuizMapper.Question> questions){var numbers=jdbc.queryForList("SELECT attempt_no FROM user_reading_attempt WHERE user_id=? AND quiz_id=? ORDER BY attempt_no DESC LIMIT 1 FOR UPDATE",Integer.class,owner,quiz.id());int number=numbers.isEmpty()?1:numbers.getFirst()+1;var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement("INSERT INTO user_reading_attempt(user_id,quiz_id,quiz_version_id,article_id,article_version_id,attempt_no,client_request_id,total_count) VALUES(?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);Object[] args={owner,quiz.id(),quiz.publishedVersionId(),quiz.articleId(),quiz.articleVersionId(),number,request,questions.size()};for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);long id=Objects.requireNonNull(key.getKey()).longValue();for(var q:questions)jdbc.update("INSERT INTO user_reading_answer(attempt_id,quiz_version_id,question_id) VALUES(?,?,?)",id,quiz.publishedVersionId(),q.id());return id;}
 public List<Answer> answers(long attempt){return answers(attempt,false);}
 public List<Answer> answers(long attempt,boolean lock){return jdbc.query("SELECT * FROM user_reading_answer WHERE attempt_id=? ORDER BY question_id"+(lock?" FOR UPDATE":""),(r,n)->new Answer(r.getLong("question_id"),r.getString("draft_choice"),r.getString("submitted_choice"),r.getObject("is_correct",Boolean.class),r.getTimestamp("submitted_at")==null?null:r.getTimestamp("submitted_at").toInstant()),attempt);}
 public void draft(long attempt,long question,String choice){jdbc.update("UPDATE user_reading_answer SET draft_choice=? WHERE attempt_id=? AND question_id=?",choice,attempt,question);jdbc.update("UPDATE user_reading_attempt SET lock_version=lock_version+1 WHERE id=?",attempt);}
 public void submit(long attempt,long question,String choice,boolean correct,boolean completed){jdbc.update("UPDATE user_reading_answer SET draft_choice=?,submitted_choice=?,is_correct=?,submitted_at=UTC_TIMESTAMP(6) WHERE attempt_id=? AND question_id=?",choice,choice,correct,attempt,question);jdbc.update("UPDATE user_reading_attempt SET correct_count=correct_count+?,status=?,completed_at=IF(?,UTC_TIMESTAMP(6),NULL),lock_version=lock_version+1 WHERE id=?",correct?1:0,completed?"COMPLETED":"IN_PROGRESS",completed,attempt);}
}
