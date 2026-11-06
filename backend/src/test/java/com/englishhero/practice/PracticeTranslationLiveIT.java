package com.englishhero.practice;

import static org.assertj.core.api.Assertions.*;
import java.net.*;import java.net.http.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.test.context.*;import org.springframework.jdbc.core.JdbcTemplate;
import com.englishhero.identity.*;import com.englishhero.practice.review.*;import tools.jackson.databind.*;

/** Three paid, original translation samples. Explicit opt-in; never run in ordinary CI. */
@EnabledIfSystemProperty(named="practice.translation.live",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=true","english-hero.practice.ai.worker-enabled=false","english-hero.practice.ai.daily-budget-microusd=100000"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticeTranslationLiveIT {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired IdentityService identities;@Autowired ObjectMapper json;
 @Autowired DeepSeekReviewClient client;@Autowired PracticeReviewBudget jobs;@Autowired PracticeReviewProperties config;
 final PracticePaperIT h=new PracticePaperIT();final List<Object> reports=new ArrayList<>();final List<Map<String,String>> sources=new ArrayList<>();int calls;boolean ready;
 final boolean quality=Boolean.getBoolean("practice.translation.live.quality");
 final boolean replay=Boolean.getBoolean("practice.translation.live.replay");
 final Path dir=Path.of("../.tool-cache/20261026-ai-live/"+(replay?"translation-quality-replay":quality?(Boolean.getBoolean("practice.translation.live.coverage")?"translation-quality-v4":"translation-quality-v3"):"translation-calibration"));
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry r){PracticePaperIT.isolated(r);}
 @BeforeEach void setup()throws Exception{
  h.port=port;h.jdbc=jdbc;h.identities=identities;h.json=json;
  assertThat(config.ready()).isTrue();assertThat(config.getDailyBudgetMicrousd()).isEqualTo(100000);
  var audit=new Properties();try(var reader=Files.newBufferedReader(Path.of("../.tool-cache/20261026-ai-live/"+(quality?"translation-quality-budget.properties":"translation-budget.properties")))){audit.load(reader);}assertThat(audit.getProperty("status")).isEqualTo(replay?"SETTLED":"RESERVED");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_subjective_review",Long.class)).isZero();
  Files.createDirectories(dir);
  if(quality&&Boolean.getBoolean("practice.translation.live.coverage.resume")){
   var prior=json.readTree(Files.readString(dir.resolve("provider-results.json")));assertThat(prior.get("liveCalls").asInt()).isEqualTo(1);assertThat(prior.get("reports").size()).isEqualTo(1);assertThat(prior.at("/reports/0/review/status").asText()).isEqualTo("SUCCEEDED");
   Files.copy(dir.resolve("provider-results.json"),dir.resolve("omission-first-result.json"));for(var r:prior.get("reports"))reports.add(r);calls=1;
  }else if(quality&&Boolean.getBoolean("practice.translation.live.recover")){
   var prior=json.readTree(Files.readString(dir.resolve("provider-results.json")));assertThat(prior.get("liveCalls").asInt()).isEqualTo(1);assertThat(prior.at("/reports/0/validationReason").asText()).isEqualTo("SENTENCE_COUNT");
   Files.copy(dir.resolve("provider-results.json"),dir.resolve("second-failed-provider-results.json"));
  }else if(quality&&Boolean.getBoolean("practice.translation.live.retry")){
   var prior=json.readTree(Files.readString(dir.resolve("provider-results.json")));assertThat(prior.get("liveCalls").asInt()).isEqualTo(1);assertThat(prior.get("reports").size()).isEqualTo(1);assertThat(prior.at("/reports/0/failure").asText()).isEqualTo("INVALID_OUTPUT");
   // Preserve the failed paid call in a separate immutable report; new run may attempt each sample once.
   Files.copy(dir.resolve("provider-results.json"),dir.resolve("first-failed-provider-results.json"));
  }else if(Boolean.getBoolean("practice.translation.live.resume")){
   var prior=json.readTree(Files.readString(dir.resolve("provider-results.json")));assertThat(prior.get("liveCalls").asInt()).isEqualTo(2);assertThat(prior.get("reports").size()).isEqualTo(2);
   for(var r:prior.get("reports")){assertThat(r.at("/review/status").asText()).isEqualTo("SUCCEEDED");reports.add(r);}calls=2;
  }else assertThat(Files.exists(dir.resolve("provider-results.json"))).as("Never overwrite paid-run evidence").isFalse();
  ready=true;
 }
 @AfterEach void clean()throws Exception{
  if(!ready)return;save();for(String name:h.users)for(long uid:jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name)){
   jdbc.update("DELETE FROM practice_subjective_review_request WHERE user_id=?",uid);jdbc.update("DELETE FROM practice_subjective_review WHERE user_id=?",uid);
  }h.cleanFixtureUsers();
 }
 void save()throws Exception{Files.createDirectories(dir);Files.writeString(dir.resolve("provider-results.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("liveCalls",replay?0:calls,"replayedResults",replay?calls:0,"reports",reports)));}
 JsonNode publish(HttpClient admin,JsonNode sample)throws Exception{
  var content=new LinkedHashMap<String,Object>();content.put("prompt",sample.get("prompt").asText());content.put("requirements","");content.put("minWords",null);content.put("maxWords",null);content.put("minutes",30);content.put("reference","Original translation reference.");content.put("notes","Original calibration; not an official answer.");
  var metadata=new LinkedHashMap<String,Object>(Map.of("title",sample.get("id").asText(),"titleZh","原创翻译校准","sourceKind","ORIGINAL","topic","education","sourceName","local calibration","sourceUrl","","usageNote","Synthetic only; dedicated test database."));
  boolean paper=sample.get("id").asText().equals("translation-clear-translation");if(paper){metadata.put("sourceKind","PAST");metadata.put("year",2024);metadata.put("month",6);metadata.put("paperNo",1);metadata.put("sourceUrl","https://example.org/synthetic");}
  var input=new LinkedHashMap<String,Object>();input.put("readingType","TRANSLATION");input.put("metadata",metadata);input.put("subjective",content);
  var set=h.success(h.call(admin,"POST","/admin/practice/subjective-sets",input));String sp="/admin/practice/sets/"+set.get("id").asText();
  for(String kind:List.of("CONTENT","SOURCE"))set=h.success(h.call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
  return h.success(h.call(admin,"POST",sp+"/publish",h.lock(set)));
 }
 String version(JsonNode set){return jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,set.get("id").asText());}
 @Test void threeOriginalTranslationsAndPaperHistory()throws Exception{
  var admin=h.actor(true);var learner=h.actor(false);var other=h.actor(false);
  var all=json.readTree(Files.readString(Path.of("../content/ai-review/original-calibration.json"))).get("samples");
  for(String wanted:List.of("translation-omission","translation-meaning","translation-clear-translation")){
   if(Boolean.getBoolean("practice.translation.live.coverage.resume")&&wanted.equals("translation-omission"))continue;
   if(Boolean.getBoolean("practice.translation.live.resume")&&!wanted.equals("translation-clear-translation"))continue;
   JsonNode sample=null;for(var candidate:all)if(candidate.get("id").asText().equals(wanted))sample=candidate;assertThat(sample).isNotNull();
   var set=publish(admin,sample);String type=wanted.equals("translation-clear-translation")?"PAPER":"SUBJECTIVE";JsonNode frozen;String ap,id;
   if(type.equals("SUBJECTIVE")){
    var a=h.success(h.call(learner,"POST","/practice/sets/"+set.get("id").asText()+"/subjective-attempts",Map.of("setVersionId",version(set),"requestId",h.requestId())));id=a.get("id").asText();ap="/practice/subjective-attempts/"+id;
    a=h.success(h.call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText",sample.get("text").asText())));frozen=h.success(h.call(learner,"POST",ap+"/submit",Map.of("expectedVersion",a.get("version").asInt(),"requestId",h.requestId(),"confirmWordRange",true)));
   }else{
    var components=new ArrayList<Map<String,String>>();for(String slot:PracticePaperRules.SLOTS){var component=slot.equals("TRANSLATION")?set:h.fixture(admin,slot);components.add(Map.of("slot",slot,"setVersionId",version(component)));}
    var paper=h.success(h.call(admin,"POST","/admin/practice/papers",Map.of("title","Original translation calibration paper","examCode","CET4","year",2024,"month",6,"sourceNote","Synthetic original test only","components",components)));String pp="/admin/practice/papers/"+paper.get("id").asText();paper=h.success(h.call(admin,"POST",pp+"/review",h.lock(paper)));paper=h.success(h.call(admin,"POST",pp+"/publish",h.lock(paper)));
    var a=h.success(h.call(learner,"POST","/practice/papers/"+paper.get("id").asText()+"/attempts",Map.of("paperVersionId",paper.get("paperVersionId").asText(),"requestId",h.requestId())));id=a.get("id").asText();ap="/practice/paper-attempts/"+id;
    var responses=new ArrayList<Map<String,Object>>();for(var c:a.get("components")){String slot=c.get("slot").asText();responses.add(Map.of("slot",slot,"text",slot.equals("TRANSLATION")?sample.get("text").asText():slot.equals("WRITING")?"A quiet study room helps students.":"","choices",Map.of()));}
    frozen=h.success(h.call(learner,"POST",ap+"/submit",Map.of("expectedLockVersion",a.get("lockVersion").asInt(),"requestId",h.requestId(),"currentSlot","WRITING","confirmUnanswered",true,"responses",responses)));
   }
   var command=Map.of("sourceType",type,"attemptId",id,"slot","TRANSLATION","requestId",h.requestId(),"consentVersion","2026-10-26");var accepted=h.call(learner,"POST","/practice/subjective-reviews",command);assertThat(accepted.statusCode()).isEqualTo(202);String rid=json.readTree(accepted.body()).get("id").asText();
   var job=jobs.claimNext().orElseThrow();assertThat(""+job.id()).isEqualTo(rid);assertThat(calls).isLessThan(3);calls++;save();long began=System.nanoTime();
   try{
    com.englishhero.practice.review.PracticeReviewDtos.ProviderResponse answer;
    if(replay){
     JsonNode saved=null;for(var r:json.readTree(Files.readString(Path.of("../.tool-cache/20261026-ai-live/translation-quality-v4/provider-results.json"))).get("reports"))if(r.get("sampleId").asText().equals(wanted))saved=r;
     assertThat(saved).isNotNull();assertThat(saved.at("/input/prompt").asText()).isEqualTo(job.source().prompt());assertThat(saved.at("/input/text").asText()).isEqualTo(job.source().text());
     var result=new PracticeReviewResultValidator(json).validate(job.source(),json.writeValueAsString(saved.at("/review/result")));
     answer=new com.englishhero.practice.review.PracticeReviewDtos.ProviderResponse(result,"saved-provider-result-replay",0L,0L);
    }else answer=client.review(job.source());
    jobs.complete(job,answer);var dto=h.success(h.call(learner,"GET","/practice/subjective-reviews/"+rid,null));
    reports.add(Map.of("sampleId",wanted,"humanExpectation",sample.get("humanExpectation").asText(),"input",job.source(),"review",dto,"conservativeMicrousd",PracticeReviewBudget.cost(answer.inputTokens(),answer.outputTokens()),"latencyMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)));save();
    assertThat(dto.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(dto.get("submittedText").asText()).isEqualTo(sample.get("text").asText());assertThat(dto.get("budgetStatus").asText()).isEqualTo(replay?"RELEASED":"SETTLED");
    if(quality&&wanted.equals("translation-meaning")){
     assertThat(answer.result().annotations().stream().anyMatch(n->n.kind().equals("meaning")&&n.quote().contains("required")&&n.sourceQuote()!=null&&n.sourceQuote().contains("自愿"))).as("Polarity/obligation error is meaning with source evidence").isTrue();
     assertThat(answer.result().omissions().stream().noneMatch(o->o.sourceQuote().contains("自愿"))).as("Mistranslated voluntary participation is not also an omission").isTrue();
    }
    if(quality&&wanted.equals("translation-omission"))assertThat(answer.result().omissions().stream().anyMatch(o->o.sourceQuote().contains("历史建筑"))||answer.result().annotations().stream().anyMatch(n->n.kind().equals("meaning")&&n.sourceQuote()!=null&&n.sourceQuote().contains("历史建筑"))).as("Coordinated historic-buildings item must have source-linked feedback").isTrue();
    if(quality&&wanted.equals("translation-clear-translation")){assertThat(answer.result().annotations().stream().noneMatch(n->n.severity().equals("ERROR"))).as("Correct paraphrase must not be marked wrong").isTrue();assertThat(answer.result().omissions()).isEmpty();}
    assertThat(h.call(other,"GET","/practice/subjective-reviews/"+rid,null).statusCode()).isEqualTo(404);assertThat(json.readTree(h.call(learner,"POST","/practice/subjective-reviews",command).body()).get("id").asText()).isEqualTo(rid);assertThat(h.success(h.call(learner,"GET",ap,null)).toString()).isEqualTo(frozen.toString());
    sources.add(Map.of("id",id,"type",type,"text",sample.get("text").asText()));System.out.println("TRANSLATION_SAMPLE "+wanted+"; score="+answer.result().score()+"; conservativeMicrousd="+PracticeReviewBudget.cost(answer.inputTokens(),answer.outputTokens()));
   }catch(DeepSeekReviewClient.Failure e){jobs.fail(job,e);reports.add(Map.of("sampleId",wanted,"failure",e.code,"validationReason",e.validationReason));save();throw new AssertionError("Provider failed safely: "+e.code+" / "+e.validationReason);}
  }
  assertThat(calls).isEqualTo(3);browser(learner);
 }
 void browser(HttpClient learner)throws Exception{
  var p=new ProcessBuilder("node","../frontend/tests/real/practice-translation-review.cjs");var env=p.environment();env.put("AI_TEST_BASE","http://127.0.0.1:"+port);env.put("AI_TEST_SOURCES",json.writeValueAsString(sources));env.put("AI_TEST_QUALITY",Boolean.toString(quality));env.put("AI_TEST_COVERAGE",Boolean.toString(Boolean.getBoolean("practice.translation.live.coverage")));env.put("AI_TEST_REPLAY",Boolean.toString(replay));
  var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("AI_TEST_COOKIES",json.writeValueAsString(cookies));
  var process=p.redirectErrorStream(true).redirectOutput(dir.resolve("browser.log").toFile()).start();boolean ended=process.waitFor(90,TimeUnit.SECONDS);if(!ended)process.destroyForcibly();assertThat(ended).isTrue();assertThat(process.exitValue()).as("Private translation browser log").isZero();
 }
}
