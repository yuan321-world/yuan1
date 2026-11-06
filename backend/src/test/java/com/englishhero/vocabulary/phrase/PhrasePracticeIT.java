package com.englishhero.vocabulary.phrase;
import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class PhrasePracticeIT extends PhraseHttpITSupport {
 static final String ROOT="/vocabulary/phrases";
 JsonNode post(HttpClient c,String path,Object body)throws Exception {var r=call(c,"POST",ROOT+path,body);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 Map<String,Object> command(JsonNode r,String action){return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",r.get("version").asInt(),"action",action));}
 @Test void hintsErrorsVersionsSaveResumeAndIndependentRetriesPersistWithoutWordStats()throws Exception {
  var a=actor(false);var b=actor(false);String id=post(a,"",Map.of("text","look forward to","meaningZh","期待")).get("id").asText();assertThat(call(a,"PUT",ROOT+"/"+id+"/learning",Map.of("enrolled",true)).statusCode()).isEqualTo(200);
  var input=Map.of("requestId",UUID.randomUUID().toString(),"mode","WRITE","limit",1);var s=post(a,"/practice",input);assertThat(post(a,"/practice",input)).isEqualTo(s);String path="/practice/"+s.get("id").asText()+"/commands";
  assertThat(call(b,"POST",ROOT+path,command(s,"HINT")).statusCode()).isEqualTo(404);
  var draft=command(s,"DRAFT");draft.put("draft","look for");s=post(a,path,draft);s=post(a,path,command(s,"SAVE"));assertThat(json.readTree(call(a,"GET",ROOT+"/practice",null).body()).get("items").get(0).get("draft").asText()).isEqualTo("look for");s=post(a,path,command(s,"RESUME"));
  var hint=command(s,"HINT");s=post(a,path,hint);assertThat(post(a,path,hint)).isEqualTo(s);var stale=new LinkedHashMap<>(hint);stale.put("requestId",UUID.randomUUID().toString());assertThat(call(a,"POST",ROOT+path,stale).statusCode()).isEqualTo(409);
  var answer=command(s,"PASS");answer.put("answer","look forward to");s=post(a,path,answer);assertThat(s.get("items").get(0).get("passed").asBoolean()).isFalse();assertThat(call(a,"POST",ROOT+path,command(s,"PASS")).statusCode()).isEqualTo(409);s=post(a,path,command(s,"NEXT"));
  answer=command(s,"PASS");answer.put("answer","LOOK FORWARD TO");s=post(a,path,answer);assertThat(s.get("items").get(0).get("passed").asBoolean()).isTrue();assertThat(post(a,path,answer)).isEqualTo(s);s=post(a,path,command(s,"NEXT"));assertThat(s.get("status").asText()).isEqualTo("COMPLETED");
  var stats=json.readTree(call(a,"GET",ROOT,null).body()).get("stats");assertThat(stats.get("independentPasses").asInt()).isZero();
  long uid=jdbc.queryForObject("SELECT user_id FROM vocabulary_phrase WHERE id=?",Long.class,id);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Integer.class,uid)).isZero();
 }
 @Test void concurrentDuplicateCommandsReplayAndDifferentCommandsConflict()throws Exception {
  var a=actor(false);String id=post(a,"",Map.of("text","take part in","meaningZh","参加")).get("id").asText();call(a,"PUT",ROOT+"/"+id+"/learning",Map.of("enrolled",true));var s=post(a,"/practice",Map.of("requestId",UUID.randomUUID().toString(),"mode","WRITE"));String path=ROOT+"/practice/"+s.get("id").asText()+"/commands";var second=anonymous();access.put(second,access.get(a));var input=command(s,"PASS");input.put("answer","take part in");
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var x=pool.submit(()->call(a,"POST",path,input));var y=pool.submit(()->call(second,"POST",path,input));assertThat(x.get().statusCode()).isEqualTo(200);assertThat(json.readTree(x.get().body())).isEqualTo(json.readTree(y.get().body()));}
  var changed=new LinkedHashMap<>(input);changed.put("answer","wrong words");assertThat(call(a,"POST",path,changed).statusCode()).isEqualTo(409);
 }
}
