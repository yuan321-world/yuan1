package com.englishhero.reading.checkin;

import com.englishhero.identity.*;
import com.englishhero.reading.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CheckinService {
 private final JdbcTemplate jdbc;
 private final Clock clock;
 private final IdentityService identities;
 private final ReadingService reading;
 public CheckinService(JdbcTemplate jdbc,Clock clock,IdentityService identities,ReadingService reading){this.jdbc=jdbc;this.clock=clock;this.identities=identities;this.reading=reading;}
 public record Day(LocalDate date,boolean checked){}
 public record Week(LocalDate today,LocalDate weekStart,List<Day> days,boolean todayChecked,long totalDays,long qualifiedArticles,long completedReadings,long completedAttempts){}
 public record Status(boolean readCompleted,boolean practiceCompleted,boolean hasQuiz,boolean articleQualified,LocalDate qualifiedDate,boolean todayChecked){}
 public static LocalDate date(Instant instant){return instant.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();}
 public static LocalDate monday(LocalDate day){return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
 // Both completion entry points lock this row before taking their progress/attempt locks.
 @Transactional(propagation=Propagation.MANDATORY)
 public void lockUser(long user){jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,user);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void completed(long user,long version){
  var progress=jdbc.queryForList("SELECT id,article_id FROM reading_progress WHERE user_id=? AND article_version_id=? AND status='COMPLETED' FOR UPDATE",user,version);
  if(progress.isEmpty())return;
  long article=((Number)progress.getFirst().get("article_id")).longValue();
  var attempts=jdbc.queryForList("SELECT id FROM user_reading_attempt WHERE user_id=? AND article_version_id=? AND status='COMPLETED' ORDER BY id DESC LIMIT 1 FOR SHARE",Long.class,user,version);
  if(attempts.isEmpty())return;
  Instant now=clock.instant();LocalDate day=date(now);
  // Atomic inserts avoid missing-row gap-lock upgrades between distinct users under MySQL RR.
  // A duplicate retains its original evidence/date, so redo can never create a later checkin day.
  jdbc.update("INSERT INTO reading_article_qualification(user_id,article_id,article_version_id,progress_id,attempt_id,qualified_date,qualified_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",user,article,version,progress.getFirst().get("id"),attempts.getFirst(),day,Timestamp.from(now));
  var evidence=jdbc.queryForMap("SELECT id,qualified_date FROM reading_article_qualification WHERE user_id=? AND article_id=? FOR UPDATE",user,article);
  if(!((java.sql.Date)evidence.get("qualified_date")).toLocalDate().equals(day))return;
  jdbc.update("INSERT INTO reading_daily_checkin(user_id,checkin_date,qualification_id,created_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE qualification_id=qualification_id",user,day,evidence.get("id"),Timestamp.from(now));
 }
 @Transactional(readOnly=true)
 public Week week(SessionPrincipal actor){
  identities.current(actor);long user=actor.userId();LocalDate today=date(clock.instant()),start=monday(today);
  Set<LocalDate> dates=new HashSet<>(jdbc.query("SELECT checkin_date FROM reading_daily_checkin WHERE user_id=? AND checkin_date>=? AND checkin_date<?",(r,n)->r.getDate(1).toLocalDate(),user,start,start.plusDays(7)));
  var days=java.util.stream.IntStream.range(0,7).mapToObj(i->new Day(start.plusDays(i),dates.contains(start.plusDays(i)))).toList();
  return new Week(today,start,days,dates.contains(today),count("reading_daily_checkin",user,""),count("reading_article_qualification",user,""),count("reading_progress",user," AND status='COMPLETED'"),count("user_reading_attempt",user," AND status='COMPLETED'"));
 }
 private long count(String table,long user,String suffix){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?"+suffix,Long.class,user);}
 @Transactional(readOnly=true)
 public Status status(SessionPrincipal actor,String article,String version){
  reading.version(actor,article,version);long a=ArticleRules.id(article,"articleId"),v=ArticleRules.id(version,"versionId"),u=actor.userId();
  boolean read=jdbc.queryForObject("SELECT COUNT(*)>0 FROM reading_progress WHERE user_id=? AND article_version_id=? AND status='COMPLETED'",Boolean.class,u,v);
  boolean practice=jdbc.queryForObject("SELECT COUNT(*)>0 FROM user_reading_attempt WHERE user_id=? AND article_version_id=? AND status='COMPLETED'",Boolean.class,u,v);
  boolean quiz=jdbc.queryForObject("SELECT COUNT(*)>0 FROM reading_quiz WHERE article_id=? AND article_version_id=? AND status='PUBLISHED' AND published_version_id IS NOT NULL",Boolean.class,a,v);
  var qualified=jdbc.query("SELECT qualified_date FROM reading_article_qualification WHERE user_id=? AND article_id=?",(r,n)->r.getDate(1).toLocalDate(),u,a);
  boolean today=jdbc.queryForObject("SELECT COUNT(*)>0 FROM reading_daily_checkin WHERE user_id=? AND checkin_date=?",Boolean.class,u,date(clock.instant()));
  return new Status(read,practice,quiz,!qualified.isEmpty(),qualified.isEmpty()?null:qualified.getFirst(),today);
 }
}
