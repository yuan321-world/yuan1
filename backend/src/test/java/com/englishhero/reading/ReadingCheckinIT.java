package com.englishhero.reading;

import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReadingCheckinIT extends ReadingHttpITSupport {
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean org.springframework.jdbc.core.JdbcTemplate observedJdbc;
 record Fixture(HttpClient admin,String article,String version,String quiz,String quizVersion,List<String> questions){}
 Fixture fixture(boolean quiz)throws Exception {
  var admin=actor(true);var article=json.readTree(call(admin,"POST","/admin/articles",draft("Checkin fixture")).body());String a=article.get("id").asText(),v=article.get("draftVersionId").asText();
  assertThat(call(admin,"POST","/admin/articles/"+a+"/publish",new ReadingDtos.LockInput(0)).statusCode()).isEqualTo(200);
  if(!quiz)return new Fixture(admin,a,v,null,null,List.of());
  var questions=List.of(Map.of("key","q1","type","DETAIL","stem","What helps?","options",List.of("Practice","Waiting","Nothing","Stopping"),"correctOption","A","explanation","Practice helps.","sortOrder",0));
  String q=json.readTree(call(admin,"PUT","/admin/articles/"+a+"/versions/"+v+"/quiz",Map.of("expectedLockVersion",0,"questions",questions)).body()).get("id").asText();
  assertThat(call(admin,"POST","/admin/reading/quizzes/"+q+"/publish",Map.of("expectedLockVersion",1)).statusCode()).isEqualTo(200);
  var published=json.readTree(call(anonymous(),"GET","/reading/articles/"+a+"/versions/"+v+"/quiz",null).body());return new Fixture(admin,a,v,q,published.get("versionId").asText(),List.of(published.get("questions").get(0).get("id").asText()));
 }
 String status(Fixture f){return "/reading/articles/"+f.article()+"/versions/"+f.version()+"/checkin";}
 void read(HttpClient user,Fixture f)throws Exception{assertThat(call(user,"POST","/reading/articles/"+f.article()+"/start",null).statusCode()).isEqualTo(201);assertThat(call(user,"PUT","/reading/progress/"+f.version(),new ReadingDtos.ProgressUpdate("p0001-s0001",0,true,0)).statusCode()).isEqualTo(200);}
 String practice(HttpClient user,Fixture f)throws Exception{var begun=call(user,"POST","/reading/quizzes/"+f.quiz()+"/attempts",Map.of("quizVersionId",f.quizVersion(),"clientRequestId",UUID.randomUUID().toString()));assertThat(begun.statusCode()).isEqualTo(200);String id=json.readTree(begun.body()).get("id").asText();assertThat(call(user,"POST","/reading/attempts/"+id+"/answers/"+f.questions().get(0)+"/submit",Map.of("choice","B","expectedLockVersion",0)).statusCode()).isEqualTo(200);return id;}
 @Test void bothOrdersQualifyWithoutScoreThresholdAndPrivateStatsDoNotLeak()throws Exception {
  var f=fixture(true);var user=actor(false);var other=actor(false);String week="/reading/checkins/week";
  assertThat(call(anonymous(),"GET",week,null).statusCode()).isEqualTo(401);assertThat(call(anonymous(),"GET",status(f),null).statusCode()).isEqualTo(401);
  read(user,f);assertThat(json.readTree(call(user,"GET",week,null).body()).get("totalDays").asInt()).isZero();practice(user,f);
  var data=json.readTree(call(user,"GET",week,null).body());assertThat(data.get("totalDays").asInt()).isEqualTo(1);assertThat(data.get("todayChecked").asBoolean()).isTrue();assertThat(data.get("days").size()).isEqualTo(7);
  assertThat(json.readTree(call(user,"GET",status(f),null).body()).get("articleQualified").asBoolean()).isTrue();assertThat(json.readTree(call(other,"GET",week,null).body()).get("totalDays").asInt()).isZero();
  practice(other,f);assertThat(json.readTree(call(other,"GET",week,null).body()).get("totalDays").asInt()).isZero();read(other,f);assertThat(json.readTree(call(other,"GET",week,null).body()).get("totalDays").asInt()).isEqualTo(1);
  assertThat(call(user,"POST",week,Map.of("date","2000-01-01","userId",1)).statusCode()).isIn(403,405);
 }
 @Test void sameDayMultipleArticlesAreConsumedAndRedoOrReadsNeverBackfill()throws Exception {
  var f=fixture(true);var g=fixture(true);var user=actor(false);read(user,f);String attempt=practice(user,f);read(user,g);practice(user,g);
  var data=json.readTree(call(user,"GET","/reading/checkins/week",null).body());assertThat(data.get("totalDays").asInt()).isEqualTo(1);assertThat(data.get("qualifiedArticles").asInt()).isEqualTo(2);
  // Move only the dedicated fixture evidence to yesterday: subsequent GET/retries must not create today again.
  long uid=jdbc.queryForObject("SELECT user_id FROM user_reading_attempt WHERE id=?",Long.class,attempt);
  jdbc.update("UPDATE reading_daily_checkin SET checkin_date=DATE_SUB(checkin_date,INTERVAL 1 DAY) WHERE user_id=?",uid);
  practice(user,f);assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("todayChecked").asBoolean()).isFalse();
  assertThat(call(user,"POST","/reading/attempts/"+attempt+"/answers/"+f.questions().get(0)+"/submit",Map.of("choice","B","expectedLockVersion",0)).statusCode()).isEqualTo(200);
  assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isEqualTo(1);
 }
 @Test void articleWithoutQuizAndOrdinaryProgressCannotCheckIn()throws Exception {
  var f=fixture(false);var user=actor(false);read(user,f);var result=call(user,"GET",status(f),null);assertThat(result.statusCode()).isEqualTo(200);var data=json.readTree(result.body());assertThat(data.get("readCompleted").asBoolean()).isTrue();assertThat(data.get("hasQuiz").asBoolean()).isFalse();assertThat(data.get("articleQualified").asBoolean()).isFalse();assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isZero();
 }
 @Test void concurrentFinalConditionsProduceExactlyOneDayAndOneArticleEvidence()throws Exception {
  var f=fixture(true);var user=actor(false);var second=anonymous();access.put(second,access.get(user));
  assertThat(call(user,"POST","/reading/articles/"+f.article()+"/start",null).statusCode()).isEqualTo(201);
  var begun=call(user,"POST","/reading/quizzes/"+f.quiz()+"/attempts",Map.of("quizVersionId",f.quizVersion(),"clientRequestId",UUID.randomUUID().toString()));String attempt=json.readTree(begun.body()).get("id").asText();
  call(user,"GET","/auth/csrf",null);call(second,"GET","/auth/csrf",null);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->call(user,"PUT","/reading/progress/"+f.version(),new ReadingDtos.ProgressUpdate("p0001-s0001",0,true,0)));
   var b=pool.submit(()->call(second,"POST","/reading/attempts/"+attempt+"/answers/"+f.questions().getFirst()+"/submit",Map.of("choice","B","expectedLockVersion",0)));
   assertThat(a.get().statusCode()).isEqualTo(200);assertThat(b.get().statusCode()).isEqualTo(200);
  }
  var data=json.readTree(call(user,"GET","/reading/checkins/week",null).body());assertThat(data.get("totalDays").asInt()).isEqualTo(1);assertThat(data.get("qualifiedArticles").asInt()).isEqualTo(1);
 }
 @Test void legacyCompletionsNeedANewActionAndNeverBackfillFromGetOrIdempotentRetry()throws Exception {
  var f=fixture(true);var user=actor(false);read(user,f);String attempt=practice(user,f);
  long uid=jdbc.queryForObject("SELECT user_id FROM user_reading_attempt WHERE id=?",Long.class,attempt);
  jdbc.update("DELETE FROM reading_daily_checkin WHERE user_id=?",uid);jdbc.update("DELETE FROM reading_article_qualification WHERE user_id=?",uid); // Simulate pre-E completed evidence.
  assertThat(json.readTree(call(user,"GET",status(f),null).body()).get("practiceCompleted").asBoolean()).isTrue();
  assertThat(call(user,"POST","/reading/attempts/"+attempt+"/answers/"+f.questions().getFirst()+"/submit",Map.of("choice","B","expectedLockVersion",0)).statusCode()).isEqualTo(200);
  assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isZero();
  practice(user,f);assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isEqualTo(1);
 }
 @Test void readingAndAttemptMustBelongToTheSameArticleVersion()throws Exception {
  var f=fixture(true);var user=actor(false);read(user,f);var input=draft("A revised checkin article");
  var update=new ReadingDtos.DraftUpdate(input.categoryId(),input.title(),input.titleZh(),input.summary(),input.difficulty(),input.body(),input.author(),input.sourceType(),input.sourceUrl(),input.usageNote(),1);
  var changed=call(f.admin(),"PUT","/admin/articles/"+f.article()+"/draft",update);assertThat(changed.statusCode()).isEqualTo(200);String version=json.readTree(changed.body()).get("draftVersionId").asText();
  assertThat(call(f.admin(),"POST","/admin/articles/"+f.article()+"/publish",new ReadingDtos.LockInput(2)).statusCode()).isEqualTo(200);
  var question=Map.of("key","new","type","DETAIL","stem","New version?","options",List.of("Yes","No","Wait","Stop"),"correctOption","A","explanation","Read this version.","sortOrder",0);
  String quiz=json.readTree(call(f.admin(),"PUT","/admin/articles/"+f.article()+"/versions/"+version+"/quiz",Map.of("expectedLockVersion",0,"questions",List.of(question))).body()).get("id").asText();
  call(f.admin(),"POST","/admin/reading/quizzes/"+quiz+"/publish",Map.of("expectedLockVersion",1));var pub=json.readTree(call(user,"GET","/reading/articles/"+f.article()+"/versions/"+version+"/quiz",null).body());
  var g=new Fixture(f.admin(),f.article(),version,quiz,pub.get("versionId").asText(),List.of(pub.get("questions").get(0).get("id").asText()));
  practice(user,g);assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isZero();
  read(user,g);assertThat(json.readTree(call(user,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isEqualTo(1);
 }
 @Test void distinctUsersCanQualifyAtTheSameTimeWithoutGapLockUpgradeDeadlocks()throws Exception {
  var f=fixture(true);var one=actor(false);var two=actor(false);read(one,f);read(two,f);
  String path="/reading/quizzes/"+f.quiz()+"/attempts";
  String a=json.readTree(call(one,"POST",path,Map.of("quizVersionId",f.quizVersion(),"clientRequestId",UUID.randomUUID().toString())).body()).get("id").asText();
  String b=json.readTree(call(two,"POST",path,Map.of("quizVersionId",f.quizVersion(),"clientRequestId",UUID.randomUUID().toString())).body()).get("id").asText();
  var reached=new java.util.concurrent.CountDownLatch(2);
  // Force both old missing-row locking reads to overlap. The fixed atomic insert path needs no such read.
  org.mockito.Mockito.doAnswer(invocation->{Object result=invocation.callRealMethod();reached.countDown();if(!reached.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Missing concurrent qualification query");return result;})
   .when(observedJdbc).queryForList(org.mockito.ArgumentMatchers.eq("SELECT id FROM reading_article_qualification WHERE user_id=? AND article_id=? FOR UPDATE"),org.mockito.ArgumentMatchers.eq(Long.class),org.mockito.ArgumentMatchers.any(Object[].class));
  call(one,"GET","/auth/csrf",null);call(two,"GET","/auth/csrf",null);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var first=pool.submit(()->call(one,"POST","/reading/attempts/"+a+"/answers/"+f.questions().getFirst()+"/submit",Map.of("choice","B","expectedLockVersion",0)));
   var second=pool.submit(()->call(two,"POST","/reading/attempts/"+b+"/answers/"+f.questions().getFirst()+"/submit",Map.of("choice","B","expectedLockVersion",0)));
   assertThat(first.get().statusCode()).isEqualTo(200);assertThat(second.get().statusCode()).isEqualTo(200);
  }
  assertThat(json.readTree(call(one,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isEqualTo(1);assertThat(json.readTree(call(two,"GET","/reading/checkins/week",null).body()).get("totalDays").asInt()).isEqualTo(1);
 }
}
