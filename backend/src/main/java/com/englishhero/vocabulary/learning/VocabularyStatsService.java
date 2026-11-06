package com.englishhero.vocabulary.learning;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.identity.*;
import com.englishhero.vocabulary.WordDifficultyService;
@Service
public class VocabularyStatsService {
 private final LearningMapper db;private final IdentityService identities;private final Clock clock;private final WordDifficultyService difficulty;
 public VocabularyStatsService(LearningMapper db,IdentityService identities,Clock clock,WordDifficultyService difficulty){this.db=db;this.identities=identities;this.clock=clock;this.difficulty=difficulty;}
 public record Day(LocalDate date,int newLearned,int reviewed,int independentReviewed){public Day(LocalDate date,int n,int r){this(date,n,r,0);}}
 public record Stats(LocalDate today,LocalDate weekStart,List<Day> days,int totalLearned,int totalReviewPasses,int due,int paused,int completedRounds,Instant nextDueAt,int independentReviewPasses,int streakDays,int longestStreak,List<Day> recentDays,int difficult){}
 public static int streak(Set<LocalDate> days,LocalDate today){LocalDate cursor=days.contains(today)?today:today.minusDays(1);int n=0;while(days.contains(cursor)){n++;cursor=cursor.minusDays(1);}return n;}
 public static int longest(Set<LocalDate> days){int best=0,run=0;LocalDate previous=null;for(var date:new TreeSet<>(days)){run=previous!=null&&previous.plusDays(1).equals(date)?run+1:1;best=Math.max(best,run);previous=date;}return best;}
 @Transactional(readOnly=true)public Stats stats(SessionPrincipal actor){
  identities.current(actor);long u=actor.userId();var zone=ZoneId.of("Asia/Shanghai");var now=clock.instant();var today=now.atZone(zone).toLocalDate();var monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  var from=Timestamp.from(today.minusDays(29).atStartOfDay(zone).toInstant());var to=Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant());
  var counts=new HashMap<LocalDate,int[]>();
  db.jdbc().query("SELECT DATE(CONVERT_TZ(e.created_at,'+00:00','+08:00')) day,s.kind,COUNT(DISTINCT i.learning_word_id),COUNT(DISTINCT CASE WHEN s.flow_version='v2' AND s.kind='REVIEW' AND JSON_EXTRACT(e.snapshot,'$.independent')=TRUE THEN i.learning_word_id END) FROM vocabulary_learning_event e JOIN vocabulary_learning_item i ON i.id=e.item_id AND i.user_id=e.user_id JOIN vocabulary_learning_session s ON s.id=e.session_id AND s.user_id=e.user_id WHERE e.user_id=? AND e.correct=TRUE AND e.created_at>=? AND e.created_at<? GROUP BY day,s.kind",r->{var values=counts.computeIfAbsent(r.getDate(1).toLocalDate(),ignored->new int[3]);values[r.getString(2).equals("NEW")?0:1]=r.getInt(3);values[2]+=r.getInt(4);},u,from,to);
  var week=new ArrayList<Day>();for(int i=0;i<7;i++){var date=monday.plusDays(i);var c=counts.getOrDefault(date,new int[3]);week.add(new Day(date,c[0],c[1],c[2]));}
  var recent=new ArrayList<Day>();for(int i=29;i>=0;i--){var date=today.minusDays(i);var c=counts.getOrDefault(date,new int[3]);recent.add(new Day(date,c[0],c[1],c[2]));}
  int learned=(int)db.count("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND stage>0",u);
  String eventJoins=" FROM vocabulary_learning_event e JOIN vocabulary_learning_item i ON i.id=e.item_id AND i.user_id=e.user_id JOIN vocabulary_learning_session s ON s.id=e.session_id AND s.user_id=e.user_id WHERE e.user_id=? AND e.correct=TRUE AND s.kind='REVIEW'";
  int reviews=(int)db.count("SELECT COUNT(DISTINCT CONCAT(i.learning_word_id,':',DATE(CONVERT_TZ(e.created_at,'+00:00','+08:00'))))"+eventJoins,u);
  int independent=(int)db.count("SELECT COUNT(DISTINCT CONCAT(i.learning_word_id,':',DATE(CONVERT_TZ(e.created_at,'+00:00','+08:00'))))"+eventJoins+" AND s.flow_version='v2' AND JSON_EXTRACT(e.snapshot,'$.independent')=TRUE",u);
  int due=(int)db.count("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND active=TRUE AND proficient=FALSE AND stage>0 AND due_at<=?",u,Timestamp.from(now));
  int paused=(int)db.count("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND active=FALSE",u),rounds=(int)db.count("SELECT COUNT(*) FROM vocabulary_learning_session WHERE user_id=? AND status='COMPLETED'",u);
  var dates=new HashSet<LocalDate>();db.jdbc().query("SELECT DISTINCT DATE(CONVERT_TZ(created_at,'+00:00','+08:00')) FROM vocabulary_learning_event WHERE user_id=? AND correct=TRUE AND created_at<?",r->{dates.add(r.getDate(1).toLocalDate());},u,to);
  Instant next=db.instant("SELECT MIN(due_at) FROM vocabulary_learning_word WHERE user_id=? AND active=TRUE AND proficient=FALSE AND stage>0 AND due_at>?",u,Timestamp.from(now));
  int difficult=(int)difficulty.states(u).values().stream().filter(WordDifficultyService.State::difficult).count();
  return new Stats(today,monday,List.copyOf(week),learned,reviews,due,paused,rounds,next,independent,streak(dates,today),longest(dates),List.copyOf(recent),difficult);
 }
}
