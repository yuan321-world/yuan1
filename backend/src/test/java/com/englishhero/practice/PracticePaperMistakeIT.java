package com.englishhero.practice;

import static org.assertj.core.api.Assertions.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.englishhero.identity.IdentityService;
import com.englishhero.identity.TestEmailConfiguration;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticePaperMistakeIT {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired IdentityService identities;
 @Autowired tools.jackson.databind.ObjectMapper json;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PracticePaperMistakeMapper observedMistakes;
 final PracticePaperIT h=new PracticePaperIT();
 HttpClient admin,learner,other;JsonNode paper;
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry r){PracticePaperIT.isolated(r);}
 @BeforeEach void prepare()throws Exception{
  h.port=port;h.jdbc=jdbc;h.identities=identities;h.json=json;h.recoverOnlyThisTestsFixtures();
  admin=h.actor(true);learner=h.actor(false);other=h.actor(false);
  var components=new ArrayList<Map<String,String>>();
  for(String slot:PracticePaperRules.SLOTS){var set=h.fixture(admin,slot);String v=jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,set.get("id").asText());components.add(Map.of("slot",slot,"setVersionId",v));}
  paper=h.success(h.call(admin,"POST","/admin/practice/papers",Map.of("title","Paper mistake fixture","examCode","CET4","year",2024,"month",6,"sourceNote","Dedicated test fixture","components",components)));
  String p="/admin/practice/papers/"+paper.get("id").asText();paper=h.success(h.call(admin,"POST",p+"/review",h.lock(paper)));paper=h.success(h.call(admin,"POST",p+"/publish",h.lock(paper)));
 }
 @AfterEach void clean(){h.cleanFixtureUsers();}
 JsonNode start()throws Exception{return h.success(h.call(learner,"POST","/practice/papers/"+paper.get("id").asText()+"/attempts",Map.of("paperVersionId",paper.get("paperVersionId").asText(),"requestId",h.requestId())));}
 Map<String,Object> submission(JsonNode a,boolean wrong){
  var responses=new ArrayList<Map<String,Object>>();
  for(var c:a.get("components")){
   String slot=c.get("slot").asText();var choices=new LinkedHashMap<String,String>();
   if(c.hasNonNull("objective")){int n=0;for(var q:c.get("objective").get("questions")){
    String answer=slot.equals("CLOZE")?""+(char)('A'+n):"A";
    if(wrong&&n==0){if(slot.equals("LISTENING")||slot.equals("DETAILED_1"))answer="B";else{n++;continue;}}
    choices.put(q.get("id").asText(),answer);n++;
   }}responses.add(Map.of("slot",slot,"text",c.hasNonNull("objective")?"":"Source text","choices",choices));
  }
  var body=new LinkedHashMap<String,Object>();body.put("responses",responses);body.put("currentSlot","WRITING");body.put("expectedLockVersion",a.get("lockVersion").asInt());body.put("confirmUnanswered",true);body.put("requestId",h.requestId());return body;
 }
 long owner(){return jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,h.usernames.get(learner));}
 int ledger(String expression){return jdbc.queryForObject("SELECT "+expression+" FROM practice_paper_mistake WHERE user_id=?",Integer.class,owner());}
 @Test void paperSubmissionCollectsWrongAndMissingExactlyOnce()throws Exception{
  var a=start();String ap="/practice/paper-attempts/"+a.get("id").asText();var command=submission(a,true);
  var done=h.success(h.call(learner,"POST",ap+"/submit",command));assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
  h.success(h.call(learner,"POST",ap+"/submit",command));
  assertThat(ledger("COUNT(*)")).isEqualTo(5);
  assertThat(jdbc.queryForList("SELECT original_number FROM practice_paper_mistake WHERE user_id=? ORDER BY original_number",Integer.class,owner())).containsExactly(1,26,36,46,51);
  assertThat(ledger("SUM(wrong_count)")).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_mistake WHERE user_id=?",Integer.class,owner())).isZero();
 }
 @Test void missingAndInvalidReviewSlotsReturn400WithoutCreatingAttempts()throws Exception{
  var a=start();String source="/practice/paper-attempts/"+a.get("id").asText();
  var done=h.success(h.call(learner,"POST",source+"/submit",submission(a,true)));
  String request=h.requestId();var missing=new LinkedHashMap<String,Object>();missing.put("requestId",request);
  var explicitNull=new LinkedHashMap<>(missing);explicitNull.put("slot",null);
  var commands=new ArrayList<Map<String,Object>>();commands.add(missing);commands.add(explicitNull);
  for(String slot:List.of(""," ","UNKNOWN","WRITING","TRANSLATION"))commands.add(Map.of("requestId",request,"slot",slot));
  for(var command:commands){
   var result=h.call(learner,"POST",source+"/retry-mistakes",command);
   assertThat(result.statusCode()).isEqualTo(400);
   var error=json.readTree(result.body());assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");assertThat(error.at("/fieldErrors/slot").asText()).isNotBlank();
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempt WHERE user_id=?",Integer.class,owner())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_request WHERE user_id=?",Integer.class,owner())).isZero();
  assertThat(ledger("SUM(wrong_count)")).isEqualTo(5);
  assertThat(h.success(h.call(learner,"GET",source,null)).toString()).isEqualTo(done.toString());
  var valid=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",request,"slot","LISTENING")));
  assertThat(valid.get("total").asInt()).isEqualTo(25);
 }
 @Test void concurrentSubmissionCollectsOnlyOnce()throws Exception{
  var a=start();String ap="/practice/paper-attempts/"+a.get("id").asText()+"/submit";var command=submission(a,true);
  var one=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return h.call(learner,"POST",ap,command);}catch(Exception e){throw new RuntimeException(e);}});
  var two=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return h.call(learner,"POST",ap,command);}catch(Exception e){throw new RuntimeException(e);}});
  h.success(one.join());h.success(two.join());assertThat(ledger("SUM(wrong_count)")).isEqualTo(5);
 }
 @Test void failedCollectionRollsBackPaperSubmission()throws Exception{
  var a=start();var calls=new java.util.concurrent.atomic.AtomicInteger();
  org.mockito.Mockito.doAnswer(invocation->{if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic collection failure");return invocation.callRealMethod();}).when(observedMistakes).assess(org.mockito.ArgumentMatchers.any());
  try{assertThat(h.call(learner,"POST","/practice/paper-attempts/"+a.get("id").asText()+"/submit",submission(a,true)).statusCode()).isEqualTo(500);}finally{org.mockito.Mockito.reset(observedMistakes);}
  assertThat(h.success(h.call(learner,"GET","/practice/paper-attempts/"+a.get("id").asText(),null)).get("status").asText()).isEqualTo("IN_PROGRESS");
  assertThat(ledger("COUNT(*)")).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_paper_response WHERE attempt_id=? AND result_json IS NOT NULL",Integer.class,a.get("id").asText())).isZero();
 }
 @Test void historicalBackfillKeepsLatestStateAndReplaysSafely()throws Exception{
  var first=start();h.success(h.call(learner,"POST","/practice/paper-attempts/"+first.get("id").asText()+"/submit",submission(first,true)));
  var second=start();h.success(h.call(learner,"POST","/practice/paper-attempts/"+second.get("id").asText()+"/submit",submission(second,false)));start();
  jdbc.update("UPDATE practice_paper_attempt SET completed_at='2026-10-25 00:00:00.000000' WHERE id IN (?,?)",first.get("id").asText(),second.get("id").asText());
  var before=jdbc.queryForList("SELECT response_json,result_json FROM practice_paper_response WHERE attempt_id IN (?,?) ORDER BY attempt_id,slot",first.get("id").asText(),second.get("id").asText());
  jdbc.update("DELETE FROM practice_paper_mistake WHERE user_id=?",owner());
  String migration=new String(getClass().getResourceAsStream("/db/migration/V27__practice_paper_mistakes.sql").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  String sql=migration.substring(migration.indexOf("INSERT INTO practice_paper_mistake\n"),migration.indexOf("-- BACKFILL-END")).trim();
  jdbc.update(sql.replace("WHERE a.status='COMPLETED'","WHERE a.status='COMPLETED' AND a.user_id=?"),owner());assertThat(jdbc.queryForList("SELECT status FROM practice_paper_mistake WHERE user_id=?",String.class,owner())).containsOnly("RESOLVED").hasSize(5);
  assertThat(ledger("SUM(wrong_count)")).isEqualTo(5);jdbc.update(sql.replace("WHERE a.status='COMPLETED'","WHERE a.status='COMPLETED' AND a.user_id=?"),owner());
  assertThat(ledger("SUM(wrong_count)")).isEqualTo(5);
  assertThat(jdbc.queryForList("SELECT response_json,result_json FROM practice_paper_response WHERE attempt_id IN (?,?) ORDER BY attempt_id,slot",first.get("id").asText(),second.get("id").asText())).isEqualTo(before);
 }
 @Test void databaseRejectsPaperReviewWithoutSlot()throws Exception{
  var a=start();String version=a.get("components").get(4).at("/objective/setVersionId").asText();
  long uid=jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,h.usernames.get(learner));
  assertThatThrownBy(()->jdbc.update("INSERT INTO practice_attempt(user_id,set_id,set_version_id,quiz_version_id,kind,total_count,source_paper_attempt_id) SELECT ?,set_id,id,quiz_version_id,'MISTAKES',5,? FROM practice_set_version WHERE id=?",uid,a.get("id").asText(),version)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("review_paper_context");
 }
 @Test void paperReviewsKeepFrozenContextAndOnlyUpdatePaperLedger()throws Exception{
  var a=start();String source="/practice/paper-attempts/"+a.get("id").asText();var done=h.success(h.call(learner,"POST",source+"/submit",submission(a,true)));
  String before=done.toString();var command=Map.of("requestId",h.requestId(),"slot","DETAILED_2");
  var review=h.success(h.call(learner,"POST",source+"/retry-mistakes",command));
  assertThat(review.get("total").asInt()).isEqualTo(1);assertThat(review.toString()).doesNotContain("SECRET explanation");
  String question=review.get("questions").get(0).get("id").asText();assertThat(review.at("/reviewSource/originalNumbers/"+question).asInt()).isEqualTo(51);
  assertThat(review.at("/reviewSource/attemptId").asText()).isEqualTo(a.get("id").asText());
  var corrected=completeReview(review,"DETAILED",false);assertThat(corrected.get("correctCount").asInt()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT status FROM practice_paper_mistake WHERE user_id=? AND original_number=51",String.class,owner())).isEqualTo("RESOLVED");
  assertThat(h.success(h.call(learner,"POST",source+"/retry-mistakes",command)).get("id")).isEqualTo(review.get("id"));
  for(String slot:List.of("LISTENING","CLOZE","MATCH")){
   var full=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot",slot)));
   assertThat(full.get("total").asInt()).isEqualTo(PracticePaperRules.count(slot));
   if(slot.equals("LISTENING"))assertThat(h.call(learner,"POST","/practice/attempts/"+full.get("id").asText()+"/audio-ticket",null).statusCode()).isEqualTo(200);
   completeReview(full,slot,slot.equals("LISTENING"));
  }
  assertThat(jdbc.queryForObject("SELECT status FROM practice_paper_mistake WHERE user_id=? AND original_number=2",String.class,owner())).isEqualTo("PENDING");
  var again=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","LISTENING")));
  assertThat(again.get("total").asInt()).isEqualTo(25);completeReview(again,"LISTENING",false);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_mistake WHERE user_id=?",Integer.class,owner())).isZero();
  assertThat(h.success(h.call(learner,"GET",source,null)).toString()).isEqualTo(before);
 }
 JsonNode completeReview(JsonNode review,String type,boolean wrongSecond)throws Exception{
  var answers=new ArrayList<Map<String,Object>>();int i=0;
  for(var q:review.get("questions")){String answer=type.equals("CLOZE")?""+(char)('A'+i):"A";if(wrongSecond&&i==1)answer="B";answers.add(Map.of("questionId",q.get("id").asText(),"choice",answer));i++;}
  String ap="/practice/attempts/"+review.get("id").asText();review=h.success(h.call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",review.get("version").asInt(),"answers",answers,"currentQuestionId",review.get("currentQuestionId").asText())));
  return h.success(h.call(learner,"POST",ap+"/submit",Map.of("requestId",h.requestId(),"expectedVersion",review.get("version").asInt(),"confirmUnanswered",true)));
 }
 @Test void paperReviewRejectsForeignSourcesAndPreservesConflictingActiveDraft()throws Exception{
  var a=start();String source="/practice/paper-attempts/"+a.get("id").asText();var request=Map.of("requestId",h.requestId(),"slot","DETAILED_1");
  assertThat(h.call(h.anonymous(),"POST",source+"/retry-mistakes",request).statusCode()).isEqualTo(401);
  assertThat(h.call(other,"POST",source+"/retry-mistakes",request).statusCode()).isEqualTo(404);
  h.success(h.call(learner,"POST",source+"/submit",submission(a,true)));
  assertThat(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","WRITING")).statusCode()).isEqualTo(400);
  var component=a.get("components").get(4).get("objective");String setId=component.get("setId").asText();
  var single=h.success(h.call(learner,"POST","/practice/sets/"+setId+"/attempts",Map.of("requestId",h.requestId(),"setVersionId",component.get("setVersionId").asText())));
  single=completeReview(single,"DETAILED",true);
  var existing=h.success(h.call(learner,"POST","/practice/attempts/"+single.get("id").asText()+"/retry-mistakes",Map.of("requestId",h.requestId())));
  String snapshot=existing.toString();assertThat(h.call(learner,"POST",source+"/retry-mistakes",request).statusCode()).isEqualTo(409);
  assertThat(h.success(h.call(learner,"GET","/practice/attempts/"+existing.get("id").asText(),null)).toString()).isEqualTo(snapshot);
 }
 @Test void paperMistakeListFiltersOwnedFrozenHistory()throws Exception{
  var first=start();h.success(h.call(learner,"POST","/practice/paper-attempts/"+first.get("id").asText()+"/submit",submission(first,true)));
  var second=start();h.success(h.call(learner,"POST","/practice/paper-attempts/"+second.get("id").asText()+"/submit",submission(second,true)));
  String url="/practice/paper-mistakes?exam=CET4&year=2024&month=6&status=PENDING&sourceAttemptId="+second.get("id").asText();
  var list=h.success(h.call(learner,"GET",url,null));assertThat(list.get("total").asInt()).isEqualTo(5);assertThat(list.get("items").get(0).get("wrongCount").asInt()).isEqualTo(2);
  assertThat(h.success(h.call(learner,"GET",url+"&readingType=DETAILED&pageSize=1",null)).get("items").size()).isEqualTo(1);
  assertThat(h.success(h.call(learner,"GET",url+"&readingType=DETAILED",null)).get("total").asInt()).isEqualTo(2);
  assertThat(h.success(h.call(learner,"GET","/practice/paper-mistakes?exam=CET4&year=2023",null)).get("total").asInt()).isZero();
  assertThat(h.call(other,"GET",url,null).statusCode()).isEqualTo(404);
  assertThat(h.call(h.anonymous(),"GET",url,null).statusCode()).isEqualTo(401);
  assertThat(h.call(learner,"GET","/practice/paper-mistakes?status=INVALID",null).statusCode()).isEqualTo(400);
 }
 @Test void frozenReviewResumesAfterUnpublishAndRejectsForeignAudio()throws Exception{
  var a=start();String source="/practice/paper-attempts/"+a.get("id").asText();
  assertThat(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","LISTENING")).statusCode()).isEqualTo(400);
  h.success(h.call(learner,"POST",source+"/submit",submission(a,true)));
  var component=a.get("components").get(1).get("objective");String sp="/admin/practice/sets/"+component.get("setId").asText();
  var set=h.success(h.call(admin,"GET",sp,null));h.success(h.call(admin,"POST",sp+"/unpublish",h.lock(set)));
  var review=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","LISTENING")));
  var resumed=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","LISTENING")));
  assertThat(resumed.get("id")).isEqualTo(review.get("id"));assertThat(resumed.get("setVersionId")).isEqualTo(component.get("setVersionId"));
  String rp="/practice/attempts/"+review.get("id").asText();assertThat(h.call(other,"POST",rp+"/audio-ticket",null).statusCode()).isEqualTo(404);
  h.success(h.call(learner,"POST",rp+"/audio-ticket",null));completeReview(review,"LISTENING",false);
  assertThat(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","LISTENING")).statusCode()).isEqualTo(409);
  assertThat(h.call(learner,"POST",rp+"/retry-mistakes",Map.of("requestId",h.requestId())).statusCode()).isEqualTo(409);
 }
 @Test void newPublishedComponentVersionKeepsIndependentMistakes()throws Exception{
  var first=start();h.success(h.call(learner,"POST","/practice/paper-attempts/"+first.get("id").asText()+"/submit",submission(first,true)));
  var old=first.get("components").get(5).get("objective");String sp="/admin/practice/sets/"+old.get("setId").asText();
  var set=h.success(h.call(admin,"GET",sp,null));h.success(h.call(admin,"POST",sp+"/publish",h.lock(set)));
  String next=jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,old.get("setId").asText());assertThat(next).isNotEqualTo(old.get("setVersionId").asText());
  var components=new ArrayList<Map<String,String>>();for(var c:first.get("components")){String slot=c.get("slot").asText();String v=jdbc.queryForObject("SELECT set_version_id FROM practice_paper_component WHERE paper_version_id=? AND slot=?",String.class,first.get("paperVersionId").asText(),slot);components.add(Map.of("slot",slot,"setVersionId",slot.equals("DETAILED_2")?next:v));}
  String pp="/admin/practice/papers/"+paper.get("id").asText();paper=h.success(h.call(admin,"PUT",pp,Map.of("title","New frozen paper","examCode","CET4","year",2024,"month",6,"sourceNote","Version independence fixture","expectedLockVersion",paper.get("lockVersion").asInt(),"components",components)));
  paper=h.success(h.call(admin,"POST",pp+"/review",h.lock(paper)));paper=h.success(h.call(admin,"POST",pp+"/publish",h.lock(paper)));
  var second=start();String source="/practice/paper-attempts/"+second.get("id").asText();h.success(h.call(learner,"POST",source+"/submit",submission(second,true)));
  assertThat(ledger("COUNT(*)")).isEqualTo(6);var review=h.success(h.call(learner,"POST",source+"/retry-mistakes",Map.of("requestId",h.requestId(),"slot","DETAILED_2")));completeReview(review,"DETAILED",false);
  assertThat(jdbc.queryForObject("SELECT status FROM practice_paper_mistake WHERE user_id=? AND set_version_id=?",String.class,owner(),old.get("setVersionId").asText())).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT status FROM practice_paper_mistake WHERE user_id=? AND set_version_id=?",String.class,owner(),next)).isEqualTo("RESOLVED");
 }
}
