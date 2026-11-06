package com.englishhero.reading;

import java.util.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReadingAttemptIT extends ReadingHttpITSupport {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @org.springframework.beans.factory.annotation.Autowired com.englishhero.reading.quiz.AttemptService attempts;
 @org.springframework.beans.factory.annotation.Autowired com.englishhero.identity.token.JwtService jwt;
 record Fixture(HttpClient admin,String article,String version,String quiz,String quizVersion,String first,String second){}
 Map<String,Object> content(){return Map.of("expectedLockVersion",0,"questions",List.of(question("q1",0),question("q2",1)));}
 Map<String,Object> question(String key,int order){return Map.of("key",key,"type","DETAIL","stem","What helps us learn? "+key,"options",List.of("Small steps","Never trying","No practice","Only waiting"),"correctOption","A","explanation","文章强调小步积累。","sortOrder",order);}
 Fixture fixture()throws Exception{var admin=actor(true);var article=json.readTree(call(admin,"POST","/admin/articles",draft("Attempt fixture")).body());String a=article.get("id").asText(),v=article.get("draftVersionId").asText();call(admin,"POST","/admin/articles/"+a+"/publish",new ReadingDtos.LockInput(0));String quiz=json.readTree(call(admin,"PUT","/admin/articles/"+a+"/versions/"+v+"/quiz",content()).body()).get("id").asText();call(admin,"POST","/admin/reading/quizzes/"+quiz+"/publish",Map.of("expectedLockVersion",1));var published=json.readTree(call(anonymous(),"GET","/reading/articles/"+a+"/versions/"+v+"/quiz",null).body());return new Fixture(admin,a,v,quiz,published.get("versionId").asText(),published.get("questions").get(0).get("id").asText(),published.get("questions").get(1).get("id").asText());}
 Map<String,Object> start(Fixture f,String request){return Map.of("quizVersionId",f.quizVersion(),"clientRequestId",request);}
 String state(Fixture f){return "/reading/articles/"+f.article()+"/versions/"+f.version()+"/practice";}
 @Test void privateDraftRestoresAndCompletedAnswersAreImmutableAndOwned()throws Exception{
  var f=fixture();var user=actor(false);var other=actor(false);var guest=anonymous();String base="/reading/quizzes/"+f.quiz()+"/attempts",request=UUID.randomUUID().toString();
  assertThat(call(guest,"GET",state(f),null).statusCode()).isEqualTo(401);assertThat(call(guest,"POST",base,start(f,request)).statusCode()).isEqualTo(401);assertThat(json.readTree(call(user,"GET",state(f),null).body()).get("current").isNull()).isTrue();
  var begun=call(user,"POST",base,start(f,request));assertThat(begun.statusCode()).isEqualTo(200);String id=json.readTree(begun.body()).get("id").asText(),path="/reading/attempts/"+id;String resumedRequest=UUID.randomUUID().toString();assertThat(json.readTree(call(user,"POST",base,start(f,resumedRequest)).body()).get("id").asText()).isEqualTo(id);
  assertThat(begun.body()).doesNotContain("correctOption","explanation");assertThat(json.readTree(call(user,"POST",base,start(f,request)).body()).get("id").asText()).isEqualTo(id);
  assertThat(call(other,"GET",path,null).statusCode()).isEqualTo(404);assertThat(call(other,"PUT",path+"/answers/"+f.first(),Map.of("choice","A","expectedLockVersion",0)).statusCode()).isEqualTo(404);
  var saved=call(user,"PUT",path+"/answers/"+f.first(),Map.of("choice","B","expectedLockVersion",0));assertThat(saved.statusCode()).isEqualTo(200);assertThat(saved.body()).doesNotContain("correctOption","explanation");assertThat(call(user,"GET",path,null).body()).contains("draftChoice\":\"B");
  assertThat(call(user,"POST",path+"/answers/"+f.second()+"/submit",Map.of("choice","A","expectedLockVersion",1)).statusCode()).isEqualTo(409);
  assertThat(call(user,"PUT",path+"/answers/999999999",Map.of("choice","A","expectedLockVersion",1)).statusCode()).isEqualTo(404);
  var first=call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","B","expectedLockVersion",1));assertThat(first.statusCode()).isEqualTo(200);var data=json.readTree(first.body());assertThat(data.get("answers").get(0).get("correctOption").asText()).isEqualTo("A");assertThat(data.get("answers").get(1).has("correctOption")).isFalse();assertThat(data.get("correctCount").isNull()).isTrue();
  assertThat(call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","B","expectedLockVersion",1)).statusCode()).isEqualTo(200);assertThat(call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","A","expectedLockVersion",2)).statusCode()).isEqualTo(409);
  var finalResponse=call(user,"POST",path+"/answers/"+f.second()+"/submit",Map.of("choice","A","expectedLockVersion",2));assertThat(finalResponse.statusCode()).isEqualTo(200);var completed=json.readTree(finalResponse.body());assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");assertThat(completed.get("correctCount").asInt()).isEqualTo(1);assertThat(completed.get("total").asInt()).isEqualTo(2);assertThat(completed.get("completedAt").isNull()).isFalse();
  assertThat(json.readTree(call(user,"POST",base,start(f,resumedRequest)).body()).get("id").asText()).isEqualTo(id);var redo=json.readTree(call(user,"POST",base,start(f,UUID.randomUUID().toString())).body());assertThat(redo.get("id").asText()).isNotEqualTo(id);assertThat(redo.get("attemptNo").asInt()).isEqualTo(2);assertThat(redo.toString()).doesNotContain("correctOption","explanation");assertThat(json.readTree(call(user,"GET",state(f),null).body()).get("first").get("correctCount").asInt()).isEqualTo(1);
  assertThat(json.readTree(call(user,"GET",state(f)+"/history?page=1&pageSize=6",null).body()).get("total").asInt()).isEqualTo(2);assertThat(json.readTree(call(other,"GET",state(f)+"/history",null).body()).get("total").asInt()).isZero();
 }
 @Test void concurrentStartsAndSubmissionsAreIdempotentAndOldVersionRemainsFrozen()throws Exception{
  var f=fixture();var user=actor(false);String base="/reading/quizzes/"+f.quiz()+"/attempts";HttpResponse<String> one,two;
  // Login clears the old CSRF cookie. Establish one cookie before parallel browser-like requests.
  assertThat(call(user,"GET","/auth/csrf",null).statusCode()).isEqualTo(200);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var a=pool.submit(()->call(user,"POST",base,start(f,UUID.randomUUID().toString())));var b=pool.submit(()->call(user,"POST",base,start(f,UUID.randomUUID().toString())));one=a.get();two=b.get();}
  assertThat(one.statusCode()).isEqualTo(200);assertThat(two.statusCode()).isEqualTo(200);String id=json.readTree(one.body()).get("id").asText();assertThat(json.readTree(two.body()).get("id").asText()).isEqualTo(id);String path="/reading/attempts/"+id;
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var a=pool.submit(()->call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","A","expectedLockVersion",0)));var b=pool.submit(()->call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","A","expectedLockVersion",0)));assertThat(a.get().statusCode()).isEqualTo(200);assertThat(b.get().statusCode()).isEqualTo(200);}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_reading_answer WHERE attempt_id=? AND submitted_choice IS NOT NULL",Integer.class,id)).isEqualTo(1);
  var updated=new HashMap<String,Object>(content());updated.put("expectedLockVersion",2);updated.put("questions",List.of(Map.of("key","new","type","DETAIL","stem","New question","options",List.of("A1","B1","C1","D1"),"correctOption","D","explanation","Changed explanation","sortOrder",0)));call(f.admin(),"PUT","/admin/articles/"+f.article()+"/versions/"+f.version()+"/quiz",updated);call(f.admin(),"POST","/admin/reading/quizzes/"+f.quiz()+"/publish",Map.of("expectedLockVersion",3));assertThat(call(user,"GET",path,null).body()).doesNotContain("New question","Changed explanation");
  call(f.admin(),"POST","/admin/reading/quizzes/"+f.quiz()+"/unpublish",Map.of("expectedLockVersion",4));assertThat(json.readTree(call(user,"POST",base,start(f,UUID.randomUUID().toString())).body()).get("id").asText()).isEqualTo(id);
  assertThat(call(user,"POST",path+"/answers/"+f.second()+"/submit",Map.of("choice","A","expectedLockVersion",1)).statusCode()).isEqualTo(200);assertThat(call(user,"POST",base,start(f,UUID.randomUUID().toString())).statusCode()).isEqualTo(409);assertThat(call(user,"GET",path,null).statusCode()).isEqualTo(200);
 }
 @Test void competingDraftsConflictAndClientCannotForgeScores()throws Exception{
  var f=fixture();var user=actor(false);String base="/reading/quizzes/"+f.quiz()+"/attempts";String id=json.readTree(call(user,"POST",base,start(f,UUID.randomUUID().toString())).body()).get("id").asText(),path="/reading/attempts/"+id;
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->call(user,"PUT",path+"/answers/"+f.first(),Map.of("choice","A","expectedLockVersion",0)));var b=pool.submit(()->call(user,"PUT",path+"/answers/"+f.first(),Map.of("choice","B","expectedLockVersion",0)));assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
  }
  var current=json.readTree(call(user,"GET",path,null).body());assertThat(current.get("lockVersion").asInt()).isEqualTo(1);assertThat(current.get("answers").get(0).get("draftChoice").asText()).isIn("A","B");
  assertThat(call(user,"POST",path+"/answers/"+f.first()+"/submit",Map.of("choice","A","expectedLockVersion",1,"correctCount",99,"userId","1")).statusCode()).isEqualTo(400);
  assertThat(call(user,"PUT",path+"/answers/"+f.first(),Map.of("choice","E","expectedLockVersion",1)).statusCode()).isEqualTo(400);
  assertThat(call(user,"POST",base,Map.of("quizVersionId",f.quizVersion(),"clientRequestId","bad")).statusCode()).isEqualTo(400);
  assertThat(jdbc.queryForObject("SELECT correct_count FROM user_reading_attempt WHERE id=?",Integer.class,id)).isZero();
 }
 @Test void publicationCommittedAfterTheTransactionSnapshotCanStillStartItsCurrentQuestions()throws Exception{
  var f=fixture();var user=actor(false);var claims=jwt.verifySignatureAndClaims(access.get(user));String name=jdbc.queryForObject("SELECT username FROM identity_user WHERE id=?",String.class,claims.userId());var principal=new com.englishhero.identity.SessionPrincipal(claims.userId(),name,claims.securityVersion(),List.of("USER"));var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
  transaction.execute(status->{
   jdbc.queryForObject("SELECT COUNT(*) FROM reading_question",Long.class); // Deliberately establish the old snapshot.
   try{
    var updated=new HashMap<String,Object>(content());updated.put("expectedLockVersion",2);call(f.admin(),"PUT","/admin/articles/"+f.article()+"/versions/"+f.version()+"/quiz",updated);call(f.admin(),"POST","/admin/reading/quizzes/"+f.quiz()+"/publish",Map.of("expectedLockVersion",3));var published=json.readTree(call(anonymous(),"GET","/reading/articles/"+f.article()+"/versions/"+f.version()+"/quiz",null).body());
    var started=attempts.start(principal,f.quiz(),new com.englishhero.reading.quiz.AttemptDtos.StartInput(published.get("versionId").asText(),UUID.randomUUID().toString()));assertThat(started.quiz().questions()).hasSize(2);assertThat(started.answers()).hasSize(2);
   }catch(Exception e){throw new RuntimeException(e);}return null;
  });
 }
}
