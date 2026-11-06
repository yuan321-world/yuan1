package com.englishhero.reading;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.vocabulary.learning.*;
import static com.englishhero.vocabulary.learning.LearningDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class VocabularyStatsIT extends LearningHttpITSupport {
 @Autowired VocabularyStatsService stats;@Autowired LearningService learning;@MockitoSpyBean Clock clock;
 Command command(SessionDto s,String action){return new Command(UUID.randomUUID().toString(),s.version(),s.current().id(),action,null,null,null,null,null,null);}
 @Test void realEventsArePrivateReadOnlyAndUseShanghaiDays()throws Exception{
  var a=actor(false);var b=actor(false);long u=uid(a),other=uid(b);String w=word(a,"agenda");var actor=new SessionPrincipal(u,"a",0,List.of("USER"));var second=new SessionPrincipal(other,"b",0,List.of("USER"));
  assertThat(call(anonymous(),"GET",ROOT+"/stats",null).statusCode()).isEqualTo(401);var empty=call(a,"GET",ROOT+"/stats",null);assertThat(empty.statusCode()).as(empty.body()).isEqualTo(200);
  var now=Instant.parse("2026-10-19T15:59:59Z");when(clock.instant()).thenReturn(now);learning.join(actor,new JoinInput(List.of(w)));
  var s=learning.start(actor,new Command(UUID.randomUUID().toString(),null,null,null,null,null,null,"NEW","RECOGNITION",null));s=learning.mutate(actor,s.id(),"feedback",command(s,"UNKNOWN"));s=learning.mutate(actor,s.id(),"next",command(s,"NEXT"));var answer=command(s,"KNOWN");s=learning.mutate(actor,s.id(),"feedback",answer);learning.mutate(actor,s.id(),"feedback",answer);s=learning.mutate(actor,s.id(),"next",command(s,"NEXT"));
  var data=stats.stats(actor);assertThat(data.totalLearned()).isEqualTo(1);assertThat(data.totalReviewPasses()).isZero();assertThat(data.completedRounds()).isEqualTo(1);assertThat(data.days().stream().mapToInt(VocabularyStatsService.Day::newLearned).sum()).isEqualTo(1);assertThat(stats.stats(second).totalLearned()).isZero();
  when(clock.instant()).thenReturn(now.plusSeconds(1));data=stats.stats(actor);assertThat(data.today()).isEqualTo(LocalDate.parse("2026-10-20"));var day=data.today();assertThat(data.days().stream().filter(d->d.date().equals(day)).findFirst().orElseThrow().newLearned()).isZero();
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Long.class,u);stats.stats(actor);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Long.class,u)).isEqualTo(count);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_daily_checkin WHERE user_id=?",Integer.class,u)).isZero();
 }
}
