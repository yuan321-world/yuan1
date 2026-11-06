package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import static org.assertj.core.api.Assertions.*;
class PracticeAttemptIT extends PracticeHttpITSupport {
 JsonNode start(HttpClient reader,JsonNode set)throws Exception{var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));return success(call(reader,"POST","/practice/sets/"+set.get("id").asText()+"/attempts",Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));}
 @Test void draftAndSubmissionUseFrozenVersionsAndNeverLeakEarlyAnswers()throws Exception{
  var admin=actor(true);var reader=actor(false);var other=actor(false);var a=article(admin);var set=published(admin,createSet(admin,a));var attempt=start(reader,set);String path="/practice/attempts/"+attempt.get("id").asText();
  assertThat(attempt.toString()).doesNotContain("correctOption","explanation","correctCount");assertThat(call(other,"GET",path,null).statusCode()).isEqualTo(404);assertThat(call(anonymous(),"GET",path,null).statusCode()).isEqualTo(401);
  String q1=attempt.get("questions").get(0).get("id").asText(),q2=attempt.get("questions").get(1).get("id").asText();
  var clear=new LinkedHashMap<String,Object>();clear.put("questionId",q1);clear.put("choice",null);
  attempt=success(call(reader,"PUT",path+"/draft",Map.of("expectedVersion",attempt.get("version").asInt(),"answers",List.of(Map.of("questionId",q1,"choice","B")),"currentQuestionId",q2)));
  attempt=success(call(reader,"PUT",path+"/draft",Map.of("expectedVersion",attempt.get("version").asInt(),"answers",List.of(clear),"currentQuestionId",q2)));assertThat(attempt.get("currentQuestionId").asText()).isEqualTo(q2);
  assertThat(call(reader,"POST",path+"/submit",Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmUnanswered",false)).statusCode()).isEqualTo(400);
  attempt=success(call(reader,"PUT",path+"/draft",Map.of("expectedVersion",attempt.get("version").asInt(),"answers",List.of(Map.of("questionId",q1,"choice","A")),"currentQuestionId",q1)));
  var before=attempt;var changed=draftSet(a,set.get("lockVersion").asInt(),List.of(question("q1","Different new version"),question("q2","New second")));set=success(call(admin,"PUT","/admin/practice/sets/"+set.get("id").asText(),changed));published(admin,set);
  var submit=Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmUnanswered",true);attempt=success(call(reader,"POST",path+"/submit",submit));assertThat(attempt.get("correctCount").asInt()).isEqualTo(1);assertThat(attempt.get("total").asInt()).isEqualTo(2);assertThat(attempt.toString()).contains("What helps?","correctOption","explanation").doesNotContain("Different new version");
  assertThat(success(call(reader,"POST",path+"/submit",submit)).get("completedAt")).isEqualTo(attempt.get("completedAt"));assertThat(call(reader,"PUT",path+"/draft",Map.of("expectedVersion",before.get("version").asInt(),"answers",List.of(),"currentQuestionId",q1)).statusCode()).isEqualTo(409);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_daily_checkin WHERE user_id=(SELECT id FROM identity_user WHERE username=?)",Long.class,users.get(1))).isZero();
 }
 @Test void concurrentStartAndSubmitAreIdempotentAndRejectForeignQuestions()throws Exception{
  var admin=actor(true);var reader=actor(false);var set=published(admin,createSet(admin,article(admin)));var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));String path="/practice/sets/"+set.get("id").asText()+"/attempts";
  success(call(reader,"GET","/auth/csrf",null)); // Establish one cookie before concurrent masked-token requests.
  JsonNode attempt;
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f=pool.submit(()->call(reader,"POST",path,Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));var g=pool.submit(()->call(reader,"POST",path,Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));attempt=success(f.get());assertThat(success(g.get()).get("id")).isEqualTo(attempt.get("id"));}
  String ap="/practice/attempts/"+attempt.get("id").asText(),q=attempt.get("questions").get(0).get("id").asText();
  assertThat(call(reader,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answers",List.of(Map.of("questionId","99999999","choice","A")),"currentQuestionId",q)).statusCode()).isEqualTo(400);
  var submit=Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmUnanswered",true);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var f=pool.submit(()->call(reader,"POST",ap+"/submit",submit));var g=pool.submit(()->call(reader,"POST",ap+"/submit",submit));assertThat(success(f.get()).get("completedAt")).isEqualTo(success(g.get()).get("completedAt"));}
  assertThat(jdbc.queryForObject("SELECT MAX(wrong_count) FROM practice_mistake WHERE set_id=?",Integer.class,set.get("id").asText())).isEqualTo(1);
  var different=new LinkedHashMap<String,Object>(submit);different.put("confirmUnanswered",false);assertThat(call(reader,"POST",ap+"/submit",different).statusCode()).isEqualTo(409);
 }
}
