package com.englishhero.reading;

import com.englishhero.identity.SessionPrincipal;
import com.englishhero.vocabulary.learning.LearningService;
import com.englishhero.vocabulary.learning.LearningDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningBoundaryIT extends LearningHttpITSupport {
 @Autowired LearningService learning;
 @MockitoSpyBean Clock clock;
 Command command(SessionDto s,String action){return new Command(UUID.randomUUID().toString(),s.version(),s.current().id(),action,null,null,null,null,null,null);}
 SessionDto finish(SessionPrincipal actor,SessionDto s){while(!s.status().equals("COMPLETED")){s=learning.mutate(actor,s.id(),"feedback",command(s,"KNOWN"));s=learning.mutate(actor,s.id(),"next",command(s,"NEXT"));}return s;}
 @Test void twentyWordBatchesAndShanghaiMidnightDoNotInventOrDuplicateTasks()throws Exception{
  var client=actor(false);long user=uid(client);var actor=new SessionPrincipal(user,"fixture",0,List.of("USER"));
  var entries=jdbc.queryForList("SELECT id FROM dictionary_entry WHERE lemma REGEXP '^[a-z]+$' AND JSON_UNQUOTE(JSON_EXTRACT(senses,'$[0].definitionZh'))<>'' ORDER BY id LIMIT 21",Long.class);assertThat(entries).hasSize(21);
  var ids=new ArrayList<String>();for(long entry:entries){jdbc.update("INSERT INTO user_word(user_id,entry_id) VALUES(?,?)",user,entry);ids.add(jdbc.queryForObject("SELECT id FROM user_word WHERE user_id=? AND entry_id=?",String.class,user,entry));}
  Instant before=Instant.parse("2026-10-19T15:59:59Z");when(clock.instant()).thenReturn(before);
  learning.join(actor,new JoinInput(ids.subList(0,20)));learning.join(actor,new JoinInput(ids.subList(20,21)));learning.settings(actor,new Settings(100,50));
  var s=learning.start(actor,new Command(UUID.randomUUID().toString(),null,null,null,null,null,null,"NEW","RECOGNITION",null));assertThat(s.total()).isEqualTo(20);finish(actor,s);
  assertThat(learning.overview(actor).newToday()).isEqualTo(20);assertThat(learning.overview(actor).due()).isZero();
  when(clock.instant()).thenReturn(before.plusSeconds(1));assertThat(learning.overview(actor).newToday()).isZero();assertThat(learning.overview(actor).unlearned()).isEqualTo(1);
  s=learning.start(actor,new Command(UUID.randomUUID().toString(),null,null,null,null,null,null,"NEW","RECOGNITION",null));assertThat(s.total()).isEqualTo(1);finish(actor,s);assertThat(learning.overview(actor).newToday()).isEqualTo(1);
  when(clock.instant()).thenReturn(Instant.parse("2026-10-20T20:59:59Z"));assertThat(learning.overview(actor).due()).isEqualTo(20);
  when(clock.instant()).thenReturn(Instant.parse("2026-10-20T21:00:00Z"));assertThat(learning.overview(actor).due()).isEqualTo(21);
  s=learning.start(actor,new Command(UUID.randomUUID().toString(),null,null,null,null,null,null,"REVIEW","RECOGNITION",null));assertThat(s.total()).isEqualTo(20);finish(actor,s);assertThat(learning.overview(actor).reviewToday()).isEqualTo(20);assertThat(learning.overview(actor).due()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_daily_checkin WHERE user_id=?",Integer.class,user)).isZero();
 }
}
