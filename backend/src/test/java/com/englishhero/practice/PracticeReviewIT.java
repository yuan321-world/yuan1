package com.englishhero.practice;
import static org.assertj.core.api.Assertions.*;
import java.net.http.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertySource;import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import com.englishhero.identity.*;import tools.jackson.databind.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=true","english-hero.practice.ai.api-key=stub-test-only","english-hero.practice.ai.worker-enabled=false"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticeReviewIT {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired IdentityService identities;@Autowired ObjectMapper json;
 @Autowired com.englishhero.practice.review.PracticeReviewBudget jobs;
 final PracticeSubjectiveIT h=new PracticeSubjectiveIT();HttpClient admin,learner,other;JsonNode attempt;
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry r){PracticeSubjectiveIT.isolated(r);}
 @BeforeEach void setup()throws Exception{h.port=port;h.jdbc=jdbc;h.identities=identities;h.json=json;cleanReviewFixtures();h.recoverOnlyThisTestsFixtures();admin=h.actor(true);learner=h.actor(false);other=h.actor(false);
 var input=Map.of("readingType","WRITING","metadata",Map.of("title","Review fixture","titleZh","AI测试原创","sourceKind","ORIGINAL","topic","education","sourceName","test","sourceUrl","","usageNote","Synthetic only"),"subjective",Map.of("prompt","Propose a study space.","requirements","Explain why.","minWords",1,"maxWords",180,"minutes",30,"reference","A reference.","notes","Test only"));
 var set=h.success(h.call(admin,"POST","/admin/practice/subjective-sets",input));String path="/admin/practice/sets/"+set.get("id").asText();for(String kind:List.of("CONTENT","SOURCE"))set=h.success(h.call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));set=h.success(h.call(admin,"POST",path+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())));
 String version=jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,set.get("id").asText());attempt=h.success(h.call(learner,"POST","/practice/sets/"+set.get("id").asText()+"/subjective-attempts",Map.of("requestId",UUID.randomUUID().toString(),"setVersionId",version)));
 }
 @AfterEach void cleanup(){cleanReviewFixtures();h.cleanFixtures();}
 void cleanReviewFixtures(){for(String table:List.of("practice_subjective_review_request","practice_subjective_review")){if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,table)>0)jdbc.update("DELETE FROM "+table+" WHERE user_id IN (SELECT id FROM identity_user WHERE username LIKE 'subj!_%' ESCAPE '!')");}if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='practice_ai_daily_budget'",Integer.class)>0)jdbc.update("DELETE b FROM practice_ai_daily_budget b WHERE b.budget_day<>'1970-01-01' AND NOT EXISTS(SELECT 1 FROM practice_subjective_review r WHERE r.budget_day=b.budget_day)");}
 Map<String,Object> command(){return Map.of("requestId",UUID.randomUUID().toString(),"sourceType","SUBJECTIVE","attemptId",attempt.get("id").asText(),"slot","WRITING","consentVersion","2026-10-26");}
 void complete()throws Exception{String p="/practice/subjective-attempts/"+attempt.get("id").asText();attempt=h.success(h.call(learner,"PUT",p+"/draft",Map.of("expectedVersion",0,"answerText","Students finds a quiet room useful.")));attempt=h.success(h.call(learner,"POST",p+"/submit",Map.of("expectedVersion",1,"requestId",UUID.randomUUID().toString(),"confirmWordRange",true)));}
 @Test void frozenSourceIsPrivateAndRequestsAreIdempotent()throws Exception{
 var c=command();assertThat(h.call(h.anonymous(),"POST","/practice/subjective-reviews",c).statusCode()).isEqualTo(401);
 assertThat(h.call(other,"POST","/practice/subjective-reviews",c).statusCode()).isEqualTo(404);
 assertThat(h.call(learner,"POST","/practice/subjective-reviews",c).statusCode()).isEqualTo(400);complete();
 var first=h.call(learner,"POST","/practice/subjective-reviews",c);assertThat(first.statusCode()).isEqualTo(202);var value=json.readTree(first.body());String id=value.get("id").asText();assertThat(value.get("status").asText()).isEqualTo("QUEUED");
 assertThat(json.readTree(h.call(learner,"POST","/practice/subjective-reviews",c).body()).get("id").asText()).isEqualTo(id);
  assertThat(json.readTree(h.call(learner,"POST","/practice/subjective-reviews",command()).body()).get("id").asText()).isEqualTo(id);
  var alias=new HashMap<>(command());alias.put("attemptId","0"+attempt.get("id").asText());assertThat(h.call(learner,"POST","/practice/subjective-reviews",alias).statusCode()).isEqualTo(400);
 var changed=new HashMap<>(c);changed.put("slot","TRANSLATION");assertThat(h.call(learner,"POST","/practice/subjective-reviews",changed).statusCode()).isEqualTo(409);
 assertThat(h.call(other,"GET","/practice/subjective-reviews/"+id,null).statusCode()).isEqualTo(404);
 assertThat(h.success(h.call(learner,"GET","/practice/subjective-attempts/"+attempt.get("id").asText(),null)).toString()).isEqualTo(attempt.toString());
 assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_subjective_review WHERE id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void concurrentRequestsReserveOnceAndExpiredLeaseNeverResends()throws Exception{
  complete();var commands=java.util.stream.IntStream.range(0,20).mapToObj(n->command()).toList();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
  try{var tasks=commands.stream().map(c->pool.submit(()->h.call(learner,"POST","/practice/subjective-reviews",c))).toList();var ids=new HashSet<String>();for(var t:tasks){var r=t.get();assertThat(r.statusCode()).isEqualTo(202);ids.add(json.readTree(r.body()).get("id").asText());}assertThat(ids).hasSize(1);
   long id=Long.parseLong(ids.iterator().next());jdbc.update("UPDATE practice_subjective_review SET status='RUNNING',lease_token='dead',lease_until='2000-01-01 00:00:00' WHERE id=?",id);
   // Recovery runs even when worker dispatch is disabled; no network is configured in this fixture.
   jobs.recover();var job=jdbc.queryForMap("SELECT reserved_microusd,status,error_code FROM practice_subjective_review WHERE id=?",id);assertThat(((Number)job.get("reserved_microusd")).longValue()).isEqualTo(50000);assertThat(job.get("status")).isEqualTo("FAILED");assertThat(job.get("error_code")).isEqualTo("OUTCOME_UNKNOWN");assertThat(jobs.claimNext()).isEmpty();
  }finally{pool.shutdownNow();}
 }
 @Test void workerLeaseFreezesResultsAndSettlesUsageWithoutChangingAttempt()throws Exception{
  complete();var value=json.readTree(h.call(learner,"POST","/practice/subjective-reviews",command()).body());long id=value.get("id").asLong();
  var job=jobs.claimNext().orElseThrow();assertThat(job.id()).isEqualTo(id);assertThat(jobs.claimNext()).isEmpty();
  var validator=new com.englishhero.practice.review.PracticeReviewResultValidator(json);
  var result=validator.validate(job.source().text(),job.source().prompt(),"""
  {"score":11,"summaryZh":"主谓一致","summaryEn":"Agreement.","dimensions":[{"name":"内容","level":"GOOD"},{"name":"结构","level":"GOOD"},{"name":"语言","level":"FAIR"}],"annotations":[],"omissions":[],"revisedText":"Students find a quiet room useful."}
  """);
  jobs.complete(job,new com.englishhero.practice.review.PracticeReviewDtos.ProviderResponse(result,"deepseek-flash",3000L,2000L));
  var done=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+id,null));assertThat(done.at("/result/score").asInt()).isEqualTo(11);assertThat(done.get("submittedText").asText()).isEqualTo(attempt.get("answerText").asText());
  assertThat(jdbc.queryForObject("SELECT settled_microusd FROM practice_subjective_review WHERE id=?",Long.class,id)).isEqualTo(3300);
  jobs.fail(job,"OUTCOME_UNKNOWN");assertThat(h.success(h.call(learner,"GET","/practice/subjective-reviews/"+id,null)).get("status").asText()).isEqualTo("SUCCEEDED");
  assertThat(h.success(h.call(learner,"GET","/practice/subjective-attempts/"+attempt.get("id").asText(),null)).toString()).isEqualTo(attempt.toString());
 }
 @Test void limitsAndQueuedExpiryDoNotSendOrLoseSubmittedText()throws Exception{
  complete();var value=json.readTree(h.call(learner,"POST","/practice/subjective-reviews",command()).body());String id=value.get("id").asText();
  var wrong=new HashMap<>(command());wrong.put("slot","LISTENING");assertThat(h.call(learner,"POST","/practice/subjective-reviews",wrong).statusCode()).isEqualTo(400);
  var wrongConsent=new HashMap<>(command());wrongConsent.put("consentVersion","old");assertThat(h.call(learner,"POST","/practice/subjective-reviews",wrongConsent).statusCode()).isEqualTo(400);
  jdbc.update("UPDATE practice_subjective_review SET created_at='2000-01-01 00:00:00' WHERE id=?",id);jobs.recover();var expired=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+id,null));assertThat(expired.get("errorCode").asText()).isEqualTo("QUEUE_EXPIRED");assertThat(jdbc.queryForObject("SELECT settled_microusd FROM practice_subjective_review WHERE id=?",Long.class,id)).isZero();assertThat(jobs.claimNext()).isEmpty();
  jdbc.update("UPDATE practice_ai_daily_budget SET used_microusd=1000000 WHERE budget_day=(SELECT budget_day FROM practice_subjective_review WHERE id=?)",id);assertThat(h.call(learner,"POST","/practice/subjective-reviews",command()).statusCode()).isEqualTo(429);
 }
 @Test void unsentExpiredQueuesDoNotUseTheThreeCallAllowance()throws Exception{
  complete();for(int n=0;n<4;n++){var response=h.call(learner,"POST","/practice/subjective-reviews",command());assertThat(response.statusCode()).isEqualTo(202);String id=json.readTree(response.body()).get("id").asText();jdbc.update("UPDATE practice_subjective_review SET created_at='2000-01-01 00:00:00' WHERE id=?",id);jobs.recover();}
 }
 @Test void invalidFeedbackSettlesTrustedUsageOnceWithoutShowingAScore()throws Exception{
  complete();var start=h.call(learner,"POST","/practice/subjective-reviews",command());assertThat(start.statusCode()).isEqualTo(202);var created=json.readTree(start.body());var job=jobs.claimNext().orElseThrow();
  var failure=new com.englishhero.practice.review.DeepSeekReviewClient.Failure("INVALID_OUTPUT",3000L,2000L,false);
  jobs.fail(job,failure);jobs.fail(job,failure);
  var dto=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+created.get("id").asText(),null));
  assertThat(dto.get("status").asText()).isEqualTo("FAILED");assertThat(dto.get("result").isNull()).isTrue();assertThat(dto.get("budgetStatus").asText()).isEqualTo("SETTLED");assertThat(dto.get("budgetAmountMicrocny").asLong()).isEqualTo(22000);
  assertThat(jdbc.queryForObject("SELECT used_microusd FROM practice_ai_daily_budget WHERE budget_day=?",Long.class,job.day())).isEqualTo(3300);
 }
 @Test void rejectedRequestReleasesReserveButExpiredUnknownLeaseCannotReleaseIt()throws Exception{
  complete();assertThat(h.call(learner,"POST","/practice/subjective-reviews",command()).statusCode()).isEqualTo(202);var job=jobs.claimNext().orElseThrow();
  var rejection=new com.englishhero.practice.review.DeepSeekReviewClient.Failure("PROVIDER_AUTH",null,null,true);jobs.fail(job,rejection);jobs.fail(job,rejection);
  assertThat(jdbc.queryForObject("SELECT used_microusd FROM practice_ai_daily_budget WHERE budget_day=?",Long.class,job.day())).isZero();
  var released=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+job.id(),null));assertThat(released.get("budgetStatus").asText()).isEqualTo("RELEASED");
  jdbc.update("UPDATE practice_subjective_review SET created_at='2000-01-01 00:00:00' WHERE id=?",job.id());assertThat(h.call(learner,"POST","/practice/subjective-reviews",command()).statusCode()).isEqualTo(202);var unknown=jobs.claimNext().orElseThrow();
  jdbc.update("UPDATE practice_subjective_review SET lease_until='2000-01-01 00:00:00' WHERE id=?",unknown.id());jobs.fail(unknown,rejection);jobs.recover();jobs.fail(unknown,rejection);
  var held=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+unknown.id(),null));assertThat(held.get("budgetStatus").asText()).isEqualTo("UNKNOWN");assertThat(held.get("errorCode").asText()).isEqualTo("OUTCOME_UNKNOWN");
  assertThat(jdbc.queryForObject("SELECT used_microusd FROM practice_ai_daily_budget WHERE budget_day=?",Long.class,job.day())).isEqualTo(50000);
 }
}
