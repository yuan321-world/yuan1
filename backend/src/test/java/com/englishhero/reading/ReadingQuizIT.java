package com.englishhero.reading;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.englishhero.identity.token.AuthProperties;
import static org.assertj.core.api.Assertions.*;

class ReadingQuizIT extends ReadingHttpITSupport {
 @Autowired StringRedisTemplate redis;
 @Autowired AuthProperties props;
 @BeforeEach void cleanGuestFixtures(){
  assertThat(props.getNamespace()).startsWith("eh:test:");
  var keys=redis.keys(props.getNamespace()+":reading-guest:*");if(!keys.isEmpty())redis.delete(keys);
  // Recover only this test's named fixtures after a previous interrupted cleanup.
  users.addAll(jdbc.queryForList("SELECT DISTINCT u.username FROM identity_user u JOIN reading_article a ON a.created_by=u.id JOIN reading_article_version v ON v.article_id=a.id WHERE u.username LIKE 'read_%' AND v.title IN ('Quiz publication','Quiz guest','Quiz versions')",String.class));cleanup();users.clear();
 }
 Map<String,Object> question(String key,String stem){return Map.of("key",key,"type","MAIN_IDEA","stem",stem,"options",List.of("Small steps help.","Nobody learns.","We should stop.","Waiting is enough."),"correctOption","A","explanation","文章强调小步积累。","sortOrder",0);}
 Map<String,Object> input(int lock,String stem){return Map.of("expectedLockVersion",lock,"questions",List.of(question("q1",stem),question("q2","What helps us learn?")));}
 @Test void adminPublicationIsFrozenAndPublicNeverContainsAnswers()throws Exception{
  var admin=actor(true);var user=actor(false);var guest=anonymous();var article=json.readTree(call(admin,"POST","/admin/articles",draft("Quiz publication")).body());String a=article.get("id").asText(),v=article.get("draftVersionId").asText(),path="/admin/articles/"+a+"/versions/"+v+"/quiz",pub="/reading/articles/"+a+"/versions/"+v+"/quiz";
  assertThat(call(user,"PUT",path,input(0,"What is the main idea?")).statusCode()).isEqualTo(403);
  assertThat(call(admin,"PUT",path,input(0,"What is the main idea?")).statusCode()).isEqualTo(200);
  var quiz=json.readTree(call(admin,"GET",path,null).body());String id=quiz.get("id").asText();
  assertThat(call(guest,"GET",pub,null).statusCode()).isEqualTo(404);call(admin,"POST","/admin/articles/"+a+"/publish",new ReadingDtos.LockInput(0));assertThat(call(guest,"GET",pub,null).body()).isEqualTo("null");
  assertThat(call(admin,"POST","/admin/reading/quizzes/"+id+"/publish",Map.of("expectedLockVersion",1)).statusCode()).isEqualTo(200);
  var published=call(guest,"GET",pub,null);assertThat(published.statusCode()).isEqualTo(200);assertThat(published.body()).contains("What is the main idea?").doesNotContain("correctOption","explanation","文章强调","draft","lockVersion");String pv=json.readTree(published.body()).get("versionId").asText();
  assertThat(call(admin,"PUT",path,input(2,"Unpublished new stem")).statusCode()).isEqualTo(200);assertThat(call(guest,"GET",pub,null).body()).doesNotContain("Unpublished");assertThat(call(admin,"PUT",path,input(2,"Conflict")).statusCode()).isEqualTo(409);
  assertThat(call(admin,"POST","/admin/reading/quizzes/"+id+"/publish",Map.of("expectedLockVersion",3)).statusCode()).isEqualTo(200);assertThat(call(guest,"GET",pub,null).body()).contains("Unpublished new stem");assertThat(jdbc.queryForObject("SELECT stem FROM reading_question WHERE quiz_version_id=? AND question_key='q1'",String.class,pv)).isEqualTo("What is the main idea?");
  assertThat(call(admin,"POST","/admin/reading/quizzes/"+id+"/unpublish",Map.of("expectedLockVersion",4)).statusCode()).isEqualTo(200);assertThat(call(guest,"GET",pub,null).body()).isEqualTo("null");
  assertThat(call(admin,"PUT",path,Map.of("expectedLockVersion",5,"questions",List.of())).statusCode()).isEqualTo(400);
  assertThat(call(admin,"GET","/admin/articles/"+a+"/versions/99999999/quiz",null).statusCode()).isEqualTo(404);
 }
 @Test void guestSubmitsSequentiallyAndOnlySubmittedAnswerIsRevealed()throws Exception{
  var admin=actor(true);var guest=anonymous();var article=json.readTree(call(admin,"POST","/admin/articles",draft("Quiz guest")).body());String a=article.get("id").asText(),v=article.get("draftVersionId").asText(),path="/admin/articles/"+a+"/versions/"+v+"/quiz",pub="/reading/articles/"+a+"/versions/"+v+"/quiz";call(admin,"POST","/admin/articles/"+a+"/publish",new ReadingDtos.LockInput(0));var quiz=json.readTree(call(admin,"PUT",path,input(0,"What is the main idea?")).body());String id=quiz.get("id").asText();call(admin,"POST","/admin/reading/quizzes/"+id+"/publish",Map.of("expectedLockVersion",1));var published=json.readTree(call(guest,"GET",pub,null).body());String version=published.get("versionId").asText(),q1=published.get("questions").get(0).get("id").asText(),q2=published.get("questions").get(1).get("id").asText();
  var opened=call(guest,"POST",pub+"/guest-sessions",Map.of("quizVersionId",version));assertThat(opened.statusCode()).isEqualTo(201);assertThat(opened.body()).doesNotContain("correctOption","explanation");String token=json.readTree(opened.body()).get("token").asText(),submit="/reading/quiz-guest/submit";
  assertThat(call(guest,"POST",submit,Map.of("token",token,"questionId",q2,"choice","B")).statusCode()).isEqualTo(409);
  assertThat(call(guest,"POST",submit,Map.of("token",token,"questionId","99999999","choice","A")).statusCode()).isEqualTo(404);
  java.net.http.HttpResponse<String> result;
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var first=pool.submit(()->call(guest,"POST",submit,Map.of("token",token,"questionId",q1,"choice","B")));var duplicate=pool.submit(()->call(guest,"POST",submit,Map.of("token",token,"questionId",q1,"choice","B")));result=first.get();var second=duplicate.get();assertThat(second.statusCode()).isEqualTo(200);assertThat(second.body()).isEqualTo(result.body());
  }
  assertThat(result.statusCode()).isEqualTo(200);assertThat(result.body()).contains("correctOption","explanation").doesNotContain(q2);assertThat(json.readTree(result.body()).get("correct").asBoolean()).isFalse();assertThat(call(guest,"POST",submit,Map.of("token",token,"questionId",q1,"choice","B")).body()).isEqualTo(result.body());assertThat(call(guest,"POST",submit,Map.of("token",token,"questionId",q1,"choice","A")).statusCode()).isEqualTo(409);
  call(admin,"PUT",path,input(2,"Changed while guest is reading"));call(admin,"POST","/admin/reading/quizzes/"+id+"/publish",Map.of("expectedLockVersion",3));assertThat(call(guest,"POST",pub+"/guest-sessions",Map.of("quizVersionId",version)).statusCode()).isEqualTo(409);
  var last=json.readTree(call(guest,"POST",submit,Map.of("token",token,"questionId",q2,"choice","A")).body());assertThat(last.get("completed").asBoolean()).isTrue();assertThat(last.get("correctCount").asInt()).isEqualTo(1);assertThat(last.get("total").asInt()).isEqualTo(2);
  for(String key:redis.keys(props.getNamespace()+":reading-guest:session:*"))redis.delete(key);assertThat(call(guest,"POST",submit,Map.of("token",token,"questionId",q1,"choice","B")).statusCode()).isEqualTo(410);
 }
 @Test void privateOldVersionRequiresHistoryAndGuestStartsAreRateLimited()throws Exception{
  var admin=actor(true);var reader=actor(false);var guest=anonymous();var original=draft("Quiz versions");var article=json.readTree(call(admin,"POST","/admin/articles",original).body());String a=article.get("id").asText(),v=article.get("draftVersionId").asText(),path="/admin/articles/"+a+"/versions/"+v+"/quiz",pub="/reading/articles/"+a+"/versions/"+v+"/quiz";call(admin,"POST","/admin/articles/"+a+"/publish",new ReadingDtos.LockInput(0));call(reader,"POST","/reading/articles/"+a+"/start",null);String id=json.readTree(call(admin,"PUT",path,input(0,"Main idea?")).body()).get("id").asText();call(admin,"POST","/admin/reading/quizzes/"+id+"/publish",Map.of("expectedLockVersion",1));String version=json.readTree(call(guest,"GET",pub,null).body()).get("versionId").asText();for(int i=0;i<10;i++)assertThat(call(guest,"POST",pub+"/guest-sessions",Map.of("quizVersionId",version)).statusCode()).isEqualTo(201);assertThat(call(guest,"POST",pub+"/guest-sessions",Map.of("quizVersionId",version)).statusCode()).isEqualTo(429);
  call(admin,"POST","/admin/articles/"+a+"/unpublish",new ReadingDtos.LockInput(1));assertThat(call(guest,"GET",pub,null).statusCode()).isEqualTo(404);assertThat(call(reader,"GET",pub,null).statusCode()).isEqualTo(200);
 }
}
