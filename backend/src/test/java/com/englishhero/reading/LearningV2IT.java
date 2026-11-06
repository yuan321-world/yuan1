package com.englishhero.reading;
import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class LearningV2IT extends LearningHttpITSupport {
 Map<String,Object> start(String kind,String mode){var m=new LinkedHashMap<String,Object>();m.put("requestId",UUID.randomUUID().toString());m.put("kind",kind);m.put("mode",mode);m.put("flowVersion","v2");return m;}
 Map<String,Object> command(JsonNode s,String action){var m=new LinkedHashMap<String,Object>();m.put("requestId",UUID.randomUUID().toString());m.put("expectedVersion",s.get("version").asInt());m.put("itemId",s.get("current").get("id").asText());m.put("attemptId",s.get("current").get("attemptId").asInt());m.put("action",action);return m;}
 JsonNode post(HttpClient c,String path,Object input)throws Exception{var r=call(c,"POST",ROOT+path,input);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 void join(HttpClient c,String...lemmas)throws Exception{var ids=new ArrayList<String>();for(String lemma:lemmas)ids.add(word(c,lemma));assertThat(call(c,"POST",ROOT+"/words",Map.of("wordIds",ids)).statusCode()).isEqualTo(200);}
 JsonNode overview(HttpClient a)throws Exception{return json.readTree(call(a,"GET",ROOT+"/overview",null).body());}
 void due(HttpClient a,int stage)throws Exception{jdbc.update("UPDATE vocabulary_learning_word SET stage=?,due_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE user_id=?",stage,uid(a));}
 @Test void hintEvidenceSurvivesReloadReplayAndLaterCleanAttemptKeepsFailure()throws Exception{
  var a=actor(false);var b=actor(false);join(a,"agenda");due(a,4);var s=post(a,"/sessions",start("REVIEW","SPELLING"));String path="/sessions/"+s.get("id").asText();var hint=command(s,"HINT");s=post(a,path+"/feedback",hint);assertThat(s.get("phase").asText()).isEqualTo("QUESTION");assertThat(s.get("current").get("hintPrefix").asText()).isEqualTo("a");assertThat(post(a,path+"/feedback",hint)).isEqualTo(s);assertThat(json.readTree(call(a,"GET",ROOT+path,null).body())).isEqualTo(s);assertThat(call(b,"POST",ROOT+path+"/feedback",command(s,"ERROR")).statusCode()).isEqualTo(404);
  var spell=command(s,"SPELLING");spell.put("spelling","agenda");s=post(a,path+"/feedback",spell);assertThat(s.get("current").get("outcome").get("correct").asBoolean()).isFalse();assertThat(overview(a).get("reviewToday").asInt()).isZero();assertThat(s.get("current").get("detail").get("examples").isArray()).isTrue();
  s=post(a,path+"/next",command(s,"NEXT"));assertThat(s.get("current").get("attemptId").asInt()).isEqualTo(1);assertThat(s.get("current").get("assisted").asBoolean()).isFalse();var stale=command(s,"HINT");stale.put("attemptId",0);assertThat(call(a,"POST",ROOT+path+"/feedback",stale).statusCode()).isEqualTo(409);
  spell=command(s,"SPELLING");spell.put("spelling","AGENDA");s=post(a,path+"/feedback",spell);assertThat(s.get("current").get("outcome").get("stage").asInt()).isEqualTo(1);assertThat(overview(a).get("reviewToday").asInt()).isEqualTo(1);s=post(a,path+"/next",command(s,"NEXT"));assertThat(s.get("results").get(0).get("failures").asInt()).isEqualTo(1);assertThat(s.get("results").get(0).get("definition").get("entryId").asText()).isNotBlank();
 }
 @Test void wrongSpellingAndExplicitErrorPersistButCopyCannotUseHints()throws Exception{
  var a=actor(false);join(a,"agenda");var s=post(a,"/sessions",start("NEW","SPELLING"));String path="/sessions/"+s.get("id").asText();assertThat(s.get("current").get("meaning").asText()).isEmpty();assertThat(call(a,"POST",ROOT+path+"/feedback",command(s,"HINT")).statusCode()).isEqualTo(400);
  var wrong=command(s,"SPELLING");wrong.put("spelling","wrong");s=post(a,path+"/feedback",wrong);assertThat(s.get("current").get("assisted").asBoolean()).isTrue();assertThat(post(a,path+"/feedback",wrong)).isEqualTo(s);var spell=command(s,"SPELLING");spell.put("spelling","agenda");s=post(a,path+"/feedback",spell);assertThat(s.get("passed").asInt()).isZero();assertThat(overview(a).get("newToday").asInt()).isZero();s=post(a,path+"/next",command(s,"NEXT"));spell=command(s,"SPELLING");spell.put("spelling","agenda");s=post(a,path+"/feedback",spell);assertThat(overview(a).get("newToday").asInt()).isEqualTo(1);assertThat(s.get("current").get("outcome").get("stage").asInt()).isEqualTo(1);
 }
 @Test void manualProficiencySkipsWithoutCompletionAndRestoresOriginalSchedule()throws Exception{
  var a=actor(false);var b=actor(false);join(a,"agenda");due(a,3);String before=jdbc.queryForObject("SELECT due_at FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(a));var s=post(a,"/sessions",start("REVIEW","RECOGNITION"));String id=s.get("current").get("learningWordId").asText(),path="/sessions/"+s.get("id").asText();s=post(a,path+"/feedback",command(s,"PROFICIENT"));assertThat(s.get("status").asText()).isEqualTo("COMPLETED");assertThat(s.get("passed").asInt()).isZero();assertThat(overview(a).get("reviewToday").asInt()).isZero();assertThat(overview(a).get("due").asInt()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Integer.class,uid(a))).isZero();assertThat(call(b,"PUT",ROOT+"/words/"+id+"/proficiency",Map.of("proficient",false)).statusCode()).isEqualTo(404);assertThat(call(a,"PUT",ROOT+"/words/"+id+"/proficiency",Map.of("proficient",false)).statusCode()).isEqualTo(200);assertThat(overview(a).get("due").asInt()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT due_at FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(a))).isEqualTo(before);
 }
 @Test void legacySessionStaysV1AndHighStageWordsFreezeV2Rule()throws Exception{
  var a=actor(false);join(a,"agenda");due(a,6);var input=start("REVIEW","SPELLING");input.remove("flowVersion");var s=post(a,"/sessions",input);assertThat(s.get("flowVersion").asText()).isEqualTo("v1");assertThat(jdbc.queryForObject("SELECT rule_version FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a))).isEqualTo("fixed-v2-seven");String path="/sessions/"+s.get("id").asText();var spell=command(s,"SPELLING");spell.put("spelling","agenda");s=post(a,path+"/feedback",spell);assertThat(s.get("current").get("outcome").get("stage").asInt()).isEqualTo(7);
 }
 @Test void pastedReviewIsAssistedAndUnknownReturnsDefinition()throws Exception{
  var a=actor(false);join(a,"agenda");due(a,2);var s=post(a,"/sessions",start("REVIEW","SPELLING"));String path="/sessions/"+s.get("id").asText();var spell=command(s,"SPELLING");spell.put("spelling","agenda");spell.put("assisted",true);s=post(a,path+"/feedback",spell);assertThat(s.get("current").get("outcome").get("correct").asBoolean()).isFalse();s=post(a,path+"/next",command(s,"NEXT"));s=post(a,path+"/feedback",command(s,"UNKNOWN"));assertThat(s.get("phase").asText()).isEqualTo("DEFINITION");assertThat(s.get("current").get("detail").isObject()).isTrue();
 }
 @Test void settingsPersistButReviewGoalIsAdvisoryAndBacklogRemainsAvailable()throws Exception{
  var a=actor(false);assertThat(overview(a).get("settings").get("reviewGoal").asInt()).isEqualTo(100);assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",20,"reviewGoal",1,"autoNext",false,"reviewScope","ALL")).statusCode()).isEqualTo(200);join(a,"agenda","summary");due(a,2);var s=post(a,"/sessions",start("REVIEW","RECOGNITION"));assertThat(s.get("total").asInt()).isEqualTo(2);String path="/sessions/"+s.get("id").asText();for(int n=0;n<2;n++){s=post(a,path+"/feedback",command(s,"KNOWN"));s=post(a,path+"/next",command(s,"NEXT"));}assertThat(overview(a).get("reviewToday").asInt()).isEqualTo(2);due(a,2);var more=post(a,"/sessions",start("REVIEW","RECOGNITION"));assertThat(more.get("total").asInt()).isEqualTo(2);assertThat(overview(a).get("settings").get("autoNext").asBoolean()).isFalse();
 }

 @Test void newWordsBeyondTheDailyGoalStillReceiveTheirFirstReview()throws Exception{
  var a=actor(false);
  join(a,"agenda","summary");
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",100,"batchSize",1)).statusCode()).isEqualTo(200);
  for(int learned=1;learned<=2;learned++){
   var session=post(a,"/sessions",start("NEW","RECOGNITION"));
   assertThat(session.path("total").asInt()).isEqualTo(1);
   String path="/sessions/"+session.path("id").asText();
   session=post(a,path+"/feedback",command(session,"KNOWN"));
   var outcome=session.path("current").path("outcome");
   assertThat(outcome.path("stage").asInt()).isEqualTo(1);
   assertThat(outcome.path("dueAt").asText()).isNotBlank();
   post(a,path+"/next",command(session,"NEXT"));
   assertThat(overview(a).path("newToday").asInt()).isEqualTo(learned);
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND stage=1 AND HOUR(due_at)=21 AND MINUTE(due_at)=0 AND SECOND(due_at)=0 AND DATE(due_at+INTERVAL 8 HOUR)=DATE(last_passed_at+INTERVAL 8 HOUR)+INTERVAL 1 DAY",Integer.class,uid(a))).isEqualTo(2);
 }
 @Test void currentScopeIsFrozenAndAllSavedRoundsRemainVisible()throws Exception{
  var a=actor(false);join(a,"agenda","summary");long u=uid(a);due(a,2);jdbc.update("UPDATE vocabulary_learning_word SET personal_enrolled=FALSE WHERE user_id=? AND entry_id=(SELECT id FROM dictionary_entry WHERE lemma='summary')",u);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",20,"reviewGoal",100,"reviewScope","CURRENT")).statusCode()).isEqualTo(200);assertThat(overview(a).get("due").asInt()).isEqualTo(1);var current=post(a,"/sessions",start("REVIEW","SPELLING"));assertThat(current.get("total").asInt()).isEqualTo(1);assertThat(current.get("reviewScope").asText()).isEqualTo("CURRENT");String currentPath="/sessions/"+current.get("id").asText();var pause=command(current,"STATE");pause.put("status","PAUSED");current=post(a,currentPath+"/state",pause);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",20,"reviewGoal",100,"reviewScope","ALL")).statusCode()).isEqualTo(200);var global=post(a,"/sessions",start("REVIEW","SPELLING"));assertThat(global.get("id")).isNotEqualTo(current.get("id"));assertThat(global.get("total").asInt()).isEqualTo(1);assertThat(overview(a).get("savedSessions").size()).isEqualTo(2);assertThat(json.readTree(call(a,"GET",ROOT+currentPath,null).body()).get("reviewScope").asText()).isEqualTo("CURRENT");
 }
 @Test void openingSavedLegacyRoundThroughV2NeverRewritesItsFlowOrSnapshot()throws Exception{
  var a=actor(false);join(a,"agenda");var legacyInput=start("NEW","SPELLING");legacyInput.remove("flowVersion");var legacy=post(a,"/sessions",legacyInput);assertThat(jdbc.queryForObject("SELECT rule_version FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a))).isEqualTo("fixed-v2-seven");/* Simulate an existing pre-upgrade saved item; only those retain v1 scheduling. */jdbc.update("UPDATE vocabulary_learning_item SET rule_version='fixed-v1-binary' WHERE user_id=?",uid(a));String path="/sessions/"+legacy.get("id").asText();var original=jdbc.queryForObject("SELECT snapshot FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a));var pause=command(legacy,"STATE");pause.put("status","PAUSED");post(a,path+"/state",pause);var resumed=post(a,"/sessions",start("NEW","RECOGNITION"));assertThat(resumed.get("id")).isEqualTo(legacy.get("id"));assertThat(resumed.get("flowVersion").asText()).isEqualTo("v1");assertThat(resumed.get("mode").asText()).isEqualTo("SPELLING");assertThat(jdbc.queryForObject("SELECT snapshot FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a))).isEqualTo(original);assertThat(jdbc.queryForObject("SELECT rule_version FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a))).isEqualTo("fixed-v1-binary");
 }
 @Test void hintAdvancesAfterValidatedTypedPrefixAndPersistsThatPrefix()throws Exception{
  var a=actor(false);join(a,"agenda");due(a,2);var s=post(a,"/sessions",start("REVIEW","SPELLING"));String path="/sessions/"+s.get("id").asText();var hint=command(s,"HINT");hint.put("spelling","agE");s=post(a,path+"/feedback",hint);assertThat(s.get("current").get("hintPrefix").asText()).isEqualTo("agen");assertThat(s.get("current").get("hintCount").asInt()).isEqualTo(4);assertThat(post(a,path+"/feedback",hint)).isEqualTo(s);hint=command(s,"HINT");hint.put("spelling","wrong");assertThat(call(a,"POST",ROOT+path+"/feedback",hint).statusCode()).isEqualTo(400);assertThat(json.readTree(call(a,"GET",ROOT+path,null).body())).isEqualTo(s);
 }
 @Test void legacyInteractionCompletionsNeverFabricateIndependentEvidence()throws Exception{
  var a=actor(false);join(a,"agenda");due(a,3);var input=start("REVIEW","RECOGNITION");input.remove("flowVersion");var s=post(a,"/sessions",input);String path="/sessions/"+s.get("id").asText();s=post(a,path+"/feedback",command(s,"KNOWN"));assertThat(overview(a).get("reviewToday").asInt()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.evidenceKind')) FROM vocabulary_learning_event WHERE user_id=?",String.class,uid(a))).isEqualTo("LEGACY");var stats=json.readTree(call(a,"GET",ROOT+"/stats",null).body());assertThat(stats.get("independentReviewPasses").asInt()).isZero();
 }
 @Test void fiveAmMigrationAlignsFutureDatesAndPreservesOverdueWords()throws Exception{
  var a=actor(false);
  join(a,"agenda","summary","horizon","enter");
  long user=uid(a);
  var ids=jdbc.queryForList("SELECT id FROM vocabulary_learning_word WHERE user_id=? ORDER BY id",Long.class,user);
  jdbc.update("UPDATE vocabulary_learning_word SET stage=1,due_at='2037-01-10 15:23:46' WHERE id=?",ids.get(0));
  jdbc.update("UPDATE vocabulary_learning_word SET stage=7,due_at='2037-01-09 17:00:00' WHERE id=?",ids.get(1));
  jdbc.update("UPDATE vocabulary_learning_word SET stage=1,due_at='2000-01-01 15:23:46' WHERE id=?",ids.get(2));
  String sql=new org.springframework.core.io.ClassPathResource("db/migration/V41__word_reviews_at_five_am.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  jdbc.update(sql.replace("WHERE stage > 0","WHERE user_id = "+user+" AND stage > 0"));
  var dates=jdbc.queryForList("SELECT COALESCE(DATE_FORMAT(due_at,'%Y-%m-%d %H:%i:%s'),'NONE') FROM vocabulary_learning_word WHERE user_id=? ORDER BY id",String.class,user);
  assertThat(dates).containsExactly("2037-01-09 21:00:00","2037-01-09 21:00:00","2000-01-01 15:23:46","NONE");
 }
 @Test void configurableBatchSizePersistsAndOnlyChangesFutureRounds()throws Exception{
  var a=actor(false);var other=actor(false);
  assertThat(overview(a).path("settings").path("batchSize").asInt()).isEqualTo(20);
  join(a,"agenda","summary","horizon");
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",1,"batchSize",2)).statusCode()).isEqualTo(200);
  assertThat(overview(other).path("settings").path("batchSize").asInt()).isEqualTo(20);
  var s=post(a,"/sessions",start("NEW","RECOGNITION"));
  assertThat(s.path("total").asInt()).isEqualTo(2);
  String path="/sessions/"+s.path("id").asText();
  var pause=command(s,"STATE");pause.put("status","PAUSED");post(a,path+"/state",pause);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",1,"batchSize",1)).statusCode()).isEqualTo(200);
  s=post(a,"/sessions",start("NEW","RECOGNITION"));
  assertThat(s.path("total").asInt()).isEqualTo(2);
  for(int n=0;n<2;n++){s=post(a,path+"/feedback",command(s,"KNOWN"));s=post(a,path+"/next",command(s,"NEXT"));}
  s=post(a,"/sessions",start("NEW","RECOGNITION"));
  assertThat(s.path("total").asInt()).isEqualTo(1);
  path="/sessions/"+s.path("id").asText();
  s=post(a,path+"/feedback",command(s,"KNOWN"));post(a,path+"/next",command(s,"NEXT"));
  due(a,1);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",1,"batchSize",2)).statusCode()).isEqualTo(200);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",2,"reviewGoal",2)).statusCode()).isEqualTo(200);
  assertThat(overview(a).path("settings").path("batchSize").asInt()).isEqualTo(2);
  assertThat(post(a,"/sessions",start("REVIEW","SPELLING")).path("total").asInt()).isEqualTo(2);
  for(int invalid:new int[]{0,51}){
   assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",1,"batchSize",invalid)).statusCode()).isEqualTo(400);
   assertThat(overview(a).path("settings").path("batchSize").asInt()).isEqualTo(2);
  }
 } @Test void dictionaryLookupIsPersistedAsAssistanceAndRetriesAreIdempotent()throws Exception{
  var a=actor(false);var other=actor(false);join(a,"agenda");due(a,2);
  var s=post(a,"/sessions",start("REVIEW","SPELLING"));
  String path="/sessions/"+s.path("id").asText();
  var lookup=command(s,"LOOKUP");
  assertThat(call(other,"POST",ROOT+path+"/feedback",lookup).statusCode()).isEqualTo(404);
  s=post(a,path+"/feedback",lookup);
  assertThat(s.path("current").path("assisted").asBoolean()).isTrue();
  assertThat(s.path("current").path("hintCount").asInt()).isZero();
  assertThat(post(a,path+"/feedback",lookup)).isEqualTo(s);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=? AND action='LOOKUP'",Integer.class,uid(a))).isEqualTo(1);
  var spell=command(s,"SPELLING");spell.put("spelling","agenda");
  s=post(a,path+"/feedback",spell);
  assertThat(s.path("current").path("outcome").path("correct").asBoolean()).isFalse();
  assertThat(overview(a).path("reviewToday").asInt()).isZero();
 }
}