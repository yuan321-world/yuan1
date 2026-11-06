package com.englishhero.reading;
import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class VocabularyInsightsIT extends LearningHttpITSupport {
 JsonNode mutate(HttpClient a,JsonNode s,String op,String action,String spelling)throws Exception{var input=new HashMap<String,Object>();input.put("requestId",UUID.randomUUID().toString());input.put("expectedVersion",s.get("version").asInt());input.put("itemId",s.get("current").get("id").asText());input.put("attemptId",s.get("current").get("attemptId").asInt());input.put("action",action);if(spelling!=null)input.put("spelling",spelling);var r=call(a,"POST",ROOT+"/sessions/"+s.get("id").asText()+"/"+op,input);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return json.readTree(r.body());}
 @Test void difficultyCountsAttemptsNotRepeatedAuxiliaryRequestsAndStatsUseActualCompletions()throws Exception{
  var a=actor(false);var other=actor(false);String word=word(a,"agenda");call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(word)));
  var r=call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","SPELLING","flowVersion","v2"));assertThat(r.statusCode()).isEqualTo(200);var s=json.readTree(r.body());
  for(int i=0;i<3;i++){s=mutate(a,s,"feedback","ERROR","");s=mutate(a,s,"feedback","ERROR","");s=mutate(a,s,"feedback","SPELLING","agenda");s=mutate(a,s,"next","NEXT",null);}
  String learning=jdbc.queryForObject("SELECT id FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(a));String path=ROOT+"/words/"+learning+"/difficulty";
  var difficulty=json.readTree(call(a,"GET",path,null).body());assertThat(difficulty.get("difficult").asBoolean()).isTrue();assertThat(difficulty.get("attempts").asInt()).isEqualTo(3);assertThat(difficulty.get("assistedAttempts").asInt()).isEqualTo(3);
  assertThat(call(other,"GET",path,null).statusCode()).isEqualTo(404);assertThat(call(other,"PUT",path,Map.of("difficult",true)).statusCode()).isEqualTo(404);
  var no=json.readTree(call(a,"PUT",path,Map.of("difficult",false)).body());assertThat(no.get("difficult").asBoolean()).isFalse();
  var automatic=new HashMap<String,Object>();automatic.put("difficult",null);assertThat(json.readTree(call(a,"PUT",path,automatic).body()).get("difficult").asBoolean()).isTrue();
  assertThat(json.readTree(call(a,"GET","/vocabulary/words?status=DIFFICULT",null).body()).get("total").asInt()).isEqualTo(1);
  var stats=json.readTree(call(a,"GET",ROOT+"/stats",null).body());assertThat(stats.get("streakDays").asInt()).isZero();assertThat(stats.get("totalLearned").asInt()).isZero();
  s=mutate(a,s,"feedback","SPELLING","agenda");stats=json.readTree(call(a,"GET",ROOT+"/stats",null).body());assertThat(stats.get("streakDays").asInt()).isEqualTo(1);assertThat(stats.get("longestStreak").asInt()).isEqualTo(1);assertThat(stats.get("recentDays").size()).isEqualTo(30);assertThat(stats.get("difficult").asInt()).isEqualTo(1);assertThat(stats.get("independentReviewPasses").asInt()).isZero();
 }
}
