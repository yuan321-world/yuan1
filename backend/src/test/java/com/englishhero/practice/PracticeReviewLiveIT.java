package com.englishhero.practice;

import static org.assertj.core.api.Assertions.*;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.test.context.*;import org.springframework.jdbc.core.JdbcTemplate;
import com.englishhero.identity.*;import com.englishhero.practice.review.*;import tools.jackson.databind.*;

/** Explicitly opt-in, paid external calls. Never enabled by the ordinary test suite. */
@EnabledIfSystemProperty(named="practice.ai.live",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=true","english-hero.practice.ai.worker-enabled=false"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticeReviewLiveIT {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired IdentityService identities;@Autowired ObjectMapper json;
 @Autowired DeepSeekReviewClient client;@Autowired PracticeReviewBudget jobs;@Autowired PracticeReviewProperties config;
 final PracticeSubjectiveIT h=new PracticeSubjectiveIT();final List<Object> reports=new ArrayList<>();int liveCalls=0;
 final boolean resume=Boolean.getBoolean("practice.ai.live.resume");
 final Path report=Path.of("../.tool-cache/20261026-ai-live/"+(resume?"provider-results-cny.json":"provider-results.json"));
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry r){PracticeSubjectiveIT.isolated(r);}
 @BeforeEach void prepare()throws Exception{h.port=port;h.jdbc=jdbc;h.identities=identities;h.json=json;assertThat(config.ready()).as("Local AI configuration must contain a key; never print it").isTrue();
  assertThat(config.getDailyBudgetMicrousd()).isLessThanOrEqualTo(150000);
  if(resume){
   // Restore the actual run's prior spend after the offline fixture cleanup; never reset a live account budget.
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_subjective_review",Long.class)).as("Controlled test review tables must be empty").isZero();
   var prior=json.readTree(Files.readString(Path.of("../.tool-cache/20261026-ai-live/provider-results.json")));assertThat(prior.get("liveCalls").asInt()).isEqualTo(4);long carry=50000; // one interrupted external call, outcome unknown: keep its full reservation
   for(var r:prior.get("reports"))carry+=r.get("conservativeMicrousd").asLong();
   var day=LocalDate.now(ZoneId.of("Asia/Shanghai"));jdbc.update("DELETE FROM practice_ai_daily_budget WHERE budget_day=?",day);jdbc.update("INSERT INTO practice_ai_daily_budget(budget_day,used_microusd) VALUES(?,?)",day,carry);
   System.out.println("LIVE_CNY_CARRY microUSD="+carry+"; capped daily microUSD="+config.getDailyBudgetMicrousd());
  }
 }
 @AfterEach void cleanup()throws Exception{
  Files.createDirectories(report.getParent());Files.writeString(report,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("liveCalls",liveCalls,"reports",reports)));
  for(String name:h.users){var ids=jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name);for(long uid:ids){jdbc.update("DELETE FROM practice_subjective_review_request WHERE user_id=?",uid);jdbc.update("DELETE FROM practice_subjective_review WHERE user_id=?",uid);}}
  h.cleanFixtures();
 }
 @Test void realProviderCalibratesTwentyOriginalInputsWithoutChangingDevRecords()throws Exception{
  var admin=h.actor(true);var writing=h.actor(false);var translation=h.actor(false);
  var samples=json.readTree(Files.readString(Path.of("../content/ai-review/original-calibration.json"))).get("samples");assertThat(samples.size()).isEqualTo(20);
  var browserSources=new ArrayList<Map<String,String>>();
  for(int sampleIndex=0;sampleIndex<samples.size();sampleIndex++){var sample=samples.get(sampleIndex);
   if(resume&&sampleIndex!=1&&sampleIndex<6)continue; // one grammar recheck +14 remaining samples; <=20 total calls including interruption
   String type=sample.get("slot").asText(),sampleId=sample.get("id").asText();var learner="WRITING".equals(type)?writing:translation;
   var content=new LinkedHashMap<String,Object>();content.put("prompt",sample.get("prompt").asText());content.put("requirements","");content.put("minWords","WRITING".equals(type)?120:null);content.put("maxWords","WRITING".equals(type)?180:null);content.put("minutes",30);content.put("reference","Original calibration reference, not an official answer.");content.put("notes","Synthetic calibration only.");
   var input=Map.of("readingType",type,"metadata",Map.of("title",sampleId,"titleZh","原创校准 "+sampleId,"sourceKind","ORIGINAL","topic","education","sourceName","local calibration","sourceUrl","","usageNote","Original synthetic sample, test database only."),"subjective",content);
   var set=h.success(h.call(admin,"POST","/admin/practice/subjective-sets",input));String sp="/admin/practice/sets/"+set.get("id").asText();
   for(String kind:List.of("CONTENT","SOURCE"))set=h.success(h.call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
   set=h.success(h.call(admin,"POST",sp+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())));
   String version=jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,set.get("id").asText());
   var a=h.success(h.call(learner,"POST","/practice/sets/"+set.get("id").asText()+"/subjective-attempts",Map.of("requestId",UUID.randomUUID().toString(),"setVersionId",version)));String ap="/practice/subjective-attempts/"+a.get("id").asText();
   a=h.success(h.call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText",sample.get("text").asText())));a=h.success(h.call(learner,"POST",ap+"/submit",Map.of("expectedVersion",a.get("version").asInt(),"requestId",UUID.randomUUID().toString(),"confirmWordRange",true)));
   var command=Map.of("requestId",UUID.randomUUID().toString(),"sourceType","SUBJECTIVE","attemptId",a.get("id").asText(),"slot",type,"consentVersion","2026-10-26");
   var started=h.call(learner,"POST","/practice/subjective-reviews",command);assertThat(started.statusCode()).as("Create sample %s",sampleId).isEqualTo(202);String id=json.readTree(started.body()).get("id").asText();
   var job=jobs.claimNext().orElseThrow();assertThat(""+job.id()).isEqualTo(id);assertThat(liveCalls).isLessThan(resume?15:20);liveCalls++;long start=System.nanoTime();
   try{var answer=client.review(job.source());jobs.complete(job,answer);var dto=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+id,null));
    reports.add(Map.of("sampleId",sampleId,"humanExpectation",sample.get("humanExpectation").asText(),"input",job.source(),"latencyMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),"inputTokens",Objects.toString(answer.inputTokens(),"unknown"),"outputTokens",Objects.toString(answer.outputTokens(),"unknown"),"conservativeMicrousd",PracticeReviewBudget.cost(answer.inputTokens(),answer.outputTokens()),"review",dto));
    assertThat(dto.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(dto.get("submittedText").asText()).isEqualTo(a.get("answerText").asText());
    assertThat(json.readTree(h.call(learner,"POST","/practice/subjective-reviews",command).body()).get("id").asText()).isEqualTo(id);assertThat(h.success(h.call(learner,"GET",ap,null)).toString()).isEqualTo(a.toString());
    if(browserSources.stream().noneMatch(s->type.equals(s.get("type"))))browserSources.add(Map.of("id",a.get("id").asText(),"type",type,"text",a.get("answerText").asText()));
    Files.writeString(report,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("liveCalls",liveCalls,"reports",reports)));System.out.println("LIVE_SAMPLE "+sampleId+" succeeded; learningScore="+answer.result().score()+"; conservativeMicrousd="+PracticeReviewBudget.cost(answer.inputTokens(),answer.outputTokens()));
    if(resume&&sampleId.equals("writing-verb-pattern"))assertThat(answer.result().annotations().stream().anyMatch(n->"ERROR".equals(n.severity())&&n.quote().contains("to provide"))).as("Known verb pattern error must not be silently corrected").isTrue();
   }catch(DeepSeekReviewClient.Failure e){jobs.fail(job,e);reports.add(Map.of("sampleId",sampleId,"failure",e.code,"latencyMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));throw new AssertionError("Real provider failed safely: "+sampleId+" / "+e.code);}
  }
  assertThat(liveCalls).isEqualTo(resume?15:20);
  browser(writing,translation,browserSources);
 }
 void browser(HttpClient writing,HttpClient translation,List<Map<String,String>> sources)throws Exception{
  var p=new ProcessBuilder("node","../frontend/tests/real/practice-review.cjs");var env=p.environment();env.put("AI_TEST_BASE","http://127.0.0.1:"+port);env.put("AI_TEST_SOURCES",json.writeValueAsString(sources));
  for(var pair:Map.of("WRITING",writing,"TRANSLATION",translation).entrySet()){env.put("AI_TEST_TOKEN_"+pair.getKey(),h.tokens.get(pair.getValue()));var cookies=((CookieManager)pair.getValue().cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("AI_TEST_COOKIES_"+pair.getKey(),json.writeValueAsString(cookies));}
  var process=p.redirectErrorStream(true).redirectOutput(Path.of("../.tool-cache/20261026-ai-live/real-browser.log").toFile()).start();boolean ended=process.waitFor(120,TimeUnit.SECONDS);if(!ended)process.destroyForcibly();assertThat(ended).as("Real test DB browser completed").isTrue();assertThat(process.exitValue()).as("Inspect private real-browser.log").isZero();
 }
}
