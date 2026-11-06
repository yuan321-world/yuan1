package com.englishhero.practice;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PracticePaperMistakeMapper {
 private final JdbcTemplate jdbc;
 public PracticePaperMistakeMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public java.util.Set<Long> pending(long user,long version){return new java.util.HashSet<>(jdbc.queryForList("SELECT question_id FROM practice_paper_mistake WHERE user_id=? AND set_version_id=? AND status='PENDING'",Long.class,user,version));}
 private record Where(String sql,List<Object> args){}
 private Where where(long user,PracticePaperMistakeDtos.Query q,Set<PracticePaperMistakeDtos.SourceKey> scope){
  var args=new ArrayList<Object>();args.add(user);String sql=" FROM practice_paper_mistake m JOIN practice_set_version v ON v.id=m.set_version_id JOIN practice_set s ON s.id=m.set_id JOIN practice_paper_attempt a ON a.id=m.source_paper_attempt_id JOIN practice_paper_version p ON p.id=a.paper_version_id JOIN reading_question z ON z.id=m.question_id AND z.quiz_version_id=m.quiz_version_id WHERE m.user_id=?";
  sql+=" AND s.exam_code=? AND m.status=?";args.add(q.exam());args.add(q.status());
  if(q.year()!=null){sql+=" AND JSON_UNQUOTE(JSON_EXTRACT(v.metadata_json,'$.year'))=?";args.add(q.year().toString());}
  if(q.month()!=null){sql+=" AND JSON_UNQUOTE(JSON_EXTRACT(v.metadata_json,'$.month'))=?";args.add(q.month().toString());}
  if(q.readingType()!=null&&!q.readingType().isBlank()){sql+=" AND v.reading_type=?";args.add(q.readingType());}
  if(scope!=null){if(scope.isEmpty())sql+=" AND 1=0";else{sql+=" AND (m.set_version_id,m.question_id) IN ("+String.join(",",Collections.nCopies(scope.size(),"(?,?)"))+")";for(var key:scope){args.add(key.version());args.add(key.question());}}}
  return new Where(sql,args);
 }
 public long count(long user,PracticePaperMistakeDtos.Query q,Set<PracticePaperMistakeDtos.SourceKey> scope){var w=where(user,q,scope);return jdbc.queryForObject("SELECT COUNT(*)"+w.sql(),Long.class,w.args().toArray());}
 public List<PracticePaperMistakeDtos.PaperMistake> list(long user,PracticePaperMistakeDtos.Query q,Set<PracticePaperMistakeDtos.SourceKey> scope){
  var w=where(user,q,scope);var args=new ArrayList<>(w.args());args.add(q.pageSize());args.add((q.page()-1)*q.pageSize());
  return jdbc.query("SELECT m.*,v.reading_type,z.stem,z.correct_option,z.explanation,JSON_UNQUOTE(JSON_EXTRACT(p.metadata_json,'$.title')) paper_title"+w.sql()+" ORDER BY m.last_completed_at DESC,m.question_id DESC LIMIT ? OFFSET ?",(r,n)->new PracticePaperMistakeDtos.PaperMistake(r.getString("question_id"),r.getString("set_id"),r.getString("set_version_id"),r.getString("source_paper_attempt_id"),r.getString("slot"),r.getInt("original_number"),r.getString("paper_title"),r.getString("reading_type"),r.getString("status"),r.getInt("wrong_count"),r.getString("stem"),r.getString("submitted_choice"),r.getString("correct_option"),r.getString("explanation")),args.toArray());
 }
 public record Assessment(long user,long set,long setVersion,long quizVersion,long question,String slot,int number,long sourcePaper,Long paper,Long review,String choice,boolean correct,Instant at){}
 public void assess(Assessment a){
  if(a.correct())jdbc.update("UPDATE practice_paper_mistake SET status='RESOLVED',latest_paper_attempt_id=?,latest_review_attempt_id=?,submitted_choice=?,last_completed_at=? WHERE user_id=? AND set_version_id=? AND question_id=?",a.paper(),a.review(),a.choice(),java.sql.Timestamp.from(a.at()),a.user(),a.setVersion(),a.question());
  else jdbc.update("INSERT INTO practice_paper_mistake(user_id,set_id,set_version_id,quiz_version_id,question_id,source_paper_attempt_id,slot,original_number,latest_paper_attempt_id,latest_review_attempt_id,status,wrong_count,submitted_choice,first_wrong_at,last_completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING',1,?,?,?) ON DUPLICATE KEY UPDATE status='PENDING',wrong_count=wrong_count+1,latest_paper_attempt_id=VALUES(latest_paper_attempt_id),latest_review_attempt_id=VALUES(latest_review_attempt_id),submitted_choice=VALUES(submitted_choice),last_completed_at=VALUES(last_completed_at)",a.user(),a.set(),a.setVersion(),a.quizVersion(),a.question(),a.sourcePaper(),a.slot(),a.number(),a.paper(),a.review(),a.choice(),java.sql.Timestamp.from(a.at()),java.sql.Timestamp.from(a.at()));
 }
}
