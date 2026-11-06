package com.englishhero.reading;
import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class LearningDashboardIT extends LearningSessionIT {
 @Test void exitedNewAndReviewResumeIndependentlyAndKeepDefinitions()throws Exception{
  var a=actor(false);join(a,"agenda","summary");long u=uid(a);
  jdbc.update("UPDATE vocabulary_learning_word SET stage=2,due_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE user_id=? AND entry_id=(SELECT id FROM dictionary_entry WHERE lemma='summary')",u);
  var fresh=post(a,"/sessions",start("NEW","SPELLING"));String freshPath="/sessions/"+fresh.get("id").asText();
  var pause=command(fresh,"STATE");pause.put("status","PAUSED");fresh=post(a,freshPath+"/state",pause);
  var review=post(a,"/sessions",start("REVIEW","RECOGNITION"));assertThat(review.get("id")).isNotEqualTo(fresh.get("id"));assertThat(review.get("kind").asText()).isEqualTo("REVIEW");
  review=post(a,"/sessions/"+review.get("id").asText()+"/feedback",command(review,"KNOWN"));var definition=review.get("current");
  pause=command(review,"STATE");pause.put("status","PAUSED");review=post(a,"/sessions/"+review.get("id").asText()+"/state",pause);
  var resumed=post(a,"/sessions",start("NEW","RECOGNITION"));assertThat(resumed.get("id")).isEqualTo(fresh.get("id"));assertThat(resumed.get("mode").asText()).isEqualTo("SPELLING");
  // A stale tab cannot resume another round while this one is active.
  var otherResume=command(review,"STATE");otherResume.put("status","ACTIVE");assertThat(call(a,"POST",ROOT+"/sessions/"+review.get("id").asText()+"/state",otherResume).statusCode()).isEqualTo(409);
  pause=command(resumed,"STATE");pause.put("status","PAUSED");post(a,freshPath+"/state",pause);
  resumed=post(a,"/sessions",start("REVIEW","SPELLING"));assertThat(resumed.get("phase").asText()).isEqualTo("DEFINITION");assertThat(resumed.get("current")).isEqualTo(definition);
  var b=actor(false);assertThat(call(b,"GET",ROOT+freshPath,null).statusCode()).isEqualTo(404);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=? AND correct=TRUE",Integer.class,u)).isEqualTo(1);
 }
}
