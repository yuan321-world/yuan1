package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PracticeMistakeIT extends PracticeHttpITSupport {
 @Test void retryUsesFrozenSourceMistakesAndUnlistedHistoryRemainsPrivate()throws Exception{
  var admin=actor(true);var reader=actor(false);var other=actor(false);var a=article(admin);var set=published(admin,createSet(admin,a));String sid=set.get("id").asText();var d=success(call(anonymous(),"GET","/practice/sets/"+sid,null));var t=success(call(reader,"POST","/practice/sets/"+sid+"/attempts",Map.of("setVersionId",d.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));String source=t.get("id").asText();t=success(call(reader,"POST","/practice/attempts/"+source+"/submit",Map.of("expectedVersion",t.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmUnanswered",true)));
  assertThat(success(call(reader,"GET","/practice/mistakes?status=PENDING",null)).get("total").asInt()).isEqualTo(2);
  success(call(admin,"POST","/admin/practice/sets/"+sid+"/unpublish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())));
  assertThat(call(anonymous(),"GET","/practice/sets/"+sid,null).statusCode()).isEqualTo(404);
  assertThat(call(other,"POST","/practice/attempts/"+source+"/retry-mistakes",Map.of("requestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(404);
  var retry=success(call(reader,"POST","/practice/attempts/"+source+"/retry-mistakes",Map.of("requestId",UUID.randomUUID().toString())));assertThat(retry.get("kind").asText()).isEqualTo("MISTAKES");assertThat(retry.get("setVersionId")).isEqualTo(t.get("setVersionId"));String rp="/practice/attempts/"+retry.get("id").asText();
  var answers=new ArrayList<Map<String,String>>();for(var q:retry.get("questions"))answers.add(Map.of("questionId",q.get("id").asText(),"choice","A"));retry=success(call(reader,"PUT",rp+"/draft",Map.of("expectedVersion",retry.get("version").asInt(),"answers",answers,"currentQuestionId",answers.get(0).get("questionId"))));success(call(reader,"POST",rp+"/submit",Map.of("expectedVersion",retry.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmUnanswered",false)));
  assertThat(success(call(reader,"GET","/practice/mistakes?status=RESOLVED",null)).get("total").asInt()).isEqualTo(2);assertThat(success(call(reader,"GET","/practice/attempts/"+source,null)).get("correctCount").asInt()).isZero();assertThat(success(call(reader,"GET","/practice/history?status=COMPLETED",null)).get("total").asInt()).isEqualTo(2);
 }
}
