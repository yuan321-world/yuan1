package com.englishhero.reading;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class LearningSessionIT extends LearningHttpITSupport {
 @Test void choiceAnswerUsesPrivateImmutableSnapshotAndValidatesOptions()throws Exception{
  var a=actor(false);join(a,"agenda");var s=post(a,"/sessions",start("NEW","RECOGNITION"));String path="/sessions/"+s.get("id").asText();
  String choice=jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.correctOptionId')) FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a));
  var wrong=command(s,"CHOICE");wrong.put("optionId","not-an-option");assertThat(call(a,"POST",ROOT+path+"/feedback",wrong).statusCode()).isEqualTo(400);
  var correct=command(s,"CHOICE");correct.put("optionId",choice);var result=post(a,path+"/feedback",correct);assertThat(result.get("current").get("outcome").get("correct").asBoolean()).isTrue();
  // The immutable stored snapshot, not a new dictionary lookup, supplies resumed definitions.
  var stored=json.readTree(jdbc.queryForObject("SELECT snapshot FROM vocabulary_learning_item WHERE user_id=?",String.class,uid(a)));assertThat(result.get("current").get("definition")).isEqualTo(stored.get("entry"));
  String learning=jdbc.queryForObject("SELECT id FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(a));assertThat(call(a,"PATCH",ROOT+"/words/"+learning,Map.of("active",false)).statusCode()).isEqualTo(409);
 }
 Map<String,Object> start(String kind,String mode){return Map.of("requestId",UUID.randomUUID().toString(),"kind",kind,"mode",mode);}
 Map<String,Object> command(JsonNode s,String action){var m=new LinkedHashMap<String,Object>();m.put("requestId",UUID.randomUUID().toString());m.put("expectedVersion",s.get("version").asInt());m.put("itemId",s.get("current").get("id").asText());m.put("action",action);return m;}
 JsonNode post(HttpClient c,String path,Object input)throws Exception{var r=call(c,"POST",ROOT+path,input);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 void join(HttpClient c,String...lemmas)throws Exception{var ids=new ArrayList<String>();for(String lemma:lemmas)ids.add(word(c,lemma));assertThat(call(c,"POST",ROOT+"/words",Map.of("wordIds",ids)).statusCode()).isEqualTo(200);}
 @Test void recognitionRetriesDefinitionRecoveryAndCommandsAreIdempotent()throws Exception{
  var a=actor(false);var b=actor(false);join(a,"agenda","summary");var input=start("NEW","RECOGNITION");var s=post(a,"/sessions",input);String id=s.get("id").asText();String path="/sessions/"+id;
  assertThat(s.get("total").asInt()).isEqualTo(2);assertThat(s.toString()).doesNotContain("correctOptionId");assertThat(s.get("current").get("meaning").asText()).isEmpty();
  assertThat(call(b,"GET",ROOT+path,null).statusCode()).isEqualTo(404);
  assertThat(post(a,"/sessions",input)).isEqualTo(s);
  var wrong=command(s,"UNKNOWN");var failed=post(a,path+"/feedback",wrong);assertThat(failed.get("phase").asText()).isEqualTo("DEFINITION");
  assertThat(post(a,path+"/feedback",wrong)).isEqualTo(failed);assertThat(json.readTree(call(a,"GET",ROOT+"/session",null).body())).isEqualTo(failed);
  var conflict=new LinkedHashMap<>(wrong);conflict.put("action","KNOWN");assertThat(call(a,"POST",ROOT+path+"/feedback",conflict).statusCode()).isEqualTo(409);
  String first=s.get("current").get("id").asText();s=post(a,path+"/next",command(failed,"NEXT"));assertThat(s.get("queue").get(1).get("id").asText()).isEqualTo(first);
  for(int n=0;n<2;n++){var answer=command(s,"KNOWN");s=post(a,path+"/feedback",answer);assertThat(post(a,path+"/feedback",answer)).isEqualTo(s);assertThat(s.get("current").get("outcome").get("stage").asInt()).isEqualTo(1);s=post(a,path+"/next",command(s,"NEXT"));}
  assertThat(s.get("status").asText()).isEqualTo("COMPLETED");assertThat(s.get("passed").asInt()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=? AND correct=TRUE",Integer.class,uid(a))).isEqualTo(2);
  assertThat(call(a,"POST",ROOT+"/sessions",start("REVIEW","RECOGNITION")).statusCode()).isEqualTo(409);
 }
 @Test void spellingJudgedByServerAndReviewUsesDueRecordsAndFailureResetsStage()throws Exception{
  var a=actor(false);join(a,"agenda","summary");jdbc.update("UPDATE vocabulary_learning_word SET stage=3,due_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE user_id=? AND entry_id=(SELECT id FROM dictionary_entry WHERE lemma='agenda')",uid(a));
  var s=post(a,"/sessions",start("REVIEW","SPELLING"));assertThat(s.get("total").asInt()).isEqualTo(1);assertThat(s.get("current").get("lemma").isNull()).isTrue();String path="/sessions/"+s.get("id").asText();var input=command(s,"SPELLING");input.put("spelling","wrong");assertThat(call(a,"POST",ROOT+path+"/feedback",input).statusCode()).isEqualTo(400);
  input.put("spelling","AGENDA");input.put("hadSpellingError",true);s=post(a,path+"/feedback",input);assertThat(s.get("current").get("outcome").get("stage").asInt()).isEqualTo(1);
  assertThat(s.get("current").get("definition").get("lemma").asText()).isEqualTo("agenda");s=post(a,path+"/next",command(s,"NEXT"));assertThat(s.get("results").get(0).get("failures").asInt()).isEqualTo(1);
  s=post(a,"/sessions",start("NEW","RECOGNITION"));assertThat(s.get("current").get("lemma").asText()).isEqualTo("summary");
 }
 @Test void concurrentStartAndFeedbackSerializeAndStaleTabConflicts()throws Exception{
  var a=actor(false);join(a,"agenda");var second=anonymous();access.put(second,access.get(a));
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var x=pool.submit(()->post(a,"/sessions",start("NEW","RECOGNITION")));var y=pool.submit(()->post(second,"/sessions",start("NEW","SPELLING")));var s=x.get();assertThat(y.get().get("id")).isEqualTo(s.get("id"));
   String path="/sessions/"+s.get("id").asText()+"/feedback";var first=command(s,s.get("mode").asText().equals("SPELLING")?"SPELLING":"KNOWN");first.put("spelling","agenda");var other=new LinkedHashMap<>(first);other.put("requestId",UUID.randomUUID().toString());
   var f=pool.submit(()->call(a,"POST",ROOT+path,first));var g=pool.submit(()->call(second,"POST",ROOT+path,other));assertThat(List.of(f.get().statusCode(),g.get().statusCode())).containsExactlyInAnyOrder(200,409);
  }
 }
 @Test void pauseSnapshotCsrfAndGetCannotChangeLearning()throws Exception{
  var a=actor(false);join(a,"agenda");var s=post(a,"/sessions",start("NEW","RECOGNITION"));String path="/sessions/"+s.get("id").asText();var pause=command(s,"STATE");pause.put("status","PAUSED");s=post(a,path+"/state",pause);assertThat(s.get("status").asText()).isEqualTo("PAUSED");assertThat(call(a,"POST",ROOT+path+"/feedback",command(s,"KNOWN")).statusCode()).isEqualTo(409);
  var resume=command(s,"STATE");resume.put("status","ACTIVE");s=post(a,path+"/state",resume);assertThat(s.get("phase").asText()).isEqualTo("QUESTION");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Integer.class,uid(a))).isZero();
  var raw=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+ROOT+"/words")).header("Authorization","Bearer "+access.get(a)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"wordIds\":[]}")).build();assertThat(a.send(raw,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
 }
}
