package com.englishhero.reading;

import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

abstract class LearningHttpITSupport extends ReadingHttpITSupport {
 static final String ROOT="/vocabulary/learning";
 @BeforeEach void resetDedicatedTestLoginIpFixture()throws Exception{
  // HTTP suites share localhost; isolate only their dedicated test-DB source bucket.
  byte[] key=java.security.MessageDigest.getInstance("SHA-256").digest(("LOGIN_IP"+'\0'+"127.0.0.1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  jdbc.update("DELETE FROM identity_auth_rate WHERE scope='LOGIN_IP' AND subject_hash=? AND window_seconds=900",key);
 }

 long uid(HttpClient c)throws Exception{return json.readTree(call(c,"GET","/auth/me",null).body()).get("id").asLong();}
 String word(HttpClient c,String lemma)throws Exception{
  long entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma=?",Long.class,lemma);
  jdbc.update("INSERT INTO user_word(user_id,entry_id) VALUES(?,?)",uid(c),entry);
  return jdbc.queryForObject("SELECT id FROM user_word WHERE user_id=? AND entry_id=?",String.class,uid(c),entry);
 }
 @AfterEach void cleanLearning(){for(String name:users){var ids=jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name);for(long id:ids){
  for(String table:List.of("vocabulary_learning_source","vocabulary_learning_command","vocabulary_learning_event","vocabulary_learning_item","vocabulary_learning_session","vocabulary_learning_word","vocabulary_learning_setting"))jdbc.update("DELETE FROM "+table+" WHERE user_id=?",id);
 }}}
}
class LearningEnrollmentIT extends LearningHttpITSupport {
 @Test void settingsPaginationAndExplicitPauseHaveOwnerBoundaries()throws Exception{
  var a=actor(false);var b=actor(false);String w=word(a,"agenda"),other=word(b,"summary");
  assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(w,other))).statusCode()).isEqualTo(404);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid(a))).isZero();
  assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(w))).body()).isEqualTo("null");
  String id=jdbc.queryForObject("SELECT id FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(a));
  assertThat(call(b,"PATCH",ROOT+"/words/"+id,Map.of("active",false)).statusCode()).isEqualTo(404);
  assertThat(call(a,"PATCH",ROOT+"/words/"+id,Map.of("active",false)).body()).isEqualTo("null");
  assertThat(json.readTree(call(a,"GET",ROOT+"/overview",null).body()).get("unlearned").asInt()).isZero();
  assertThat(call(a,"PATCH",ROOT+"/words/"+id,Map.of("active",true)).statusCode()).isEqualTo(200);
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",15,"reviewGoal",60)).statusCode()).isEqualTo(200);
  assertThat(json.readTree(call(a,"GET",ROOT+"/overview",null).body()).get("settings").get("newGoal").asInt()).isEqualTo(15);
  assertThat(call(a,"GET",ROOT+"/candidates?pageSize=51",null).statusCode()).isEqualTo(400);
  assertThat(call(a,"GET",ROOT+"/words?page=0",null).statusCode()).isEqualTo(400);
 }
 @Test void ownerIsolationEnrollmentIdempotencyAndCancellationPreserveLearning()throws Exception{
  var a=actor(false);var b=actor(false);String w=word(a,"agenda");
  assertThat(call(a,"GET",ROOT+"/session",null).body()).isEqualTo("null");
  assertThat(call(anonymous(),"GET",ROOT+"/overview",null).statusCode()).isEqualTo(401);
  assertThat(call(b,"POST",ROOT+"/words",Map.of("wordIds",List.of(w))).statusCode()).isEqualTo(404);
  for(int n=0;n<2;n++)assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(w))).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid(a))).isEqualTo(1);
  assertThat(call(a,"DELETE","/vocabulary/words/"+w,null).statusCode()).isEqualTo(204);
  var overview=json.readTree(call(a,"GET",ROOT+"/overview",null).body());assertThat(overview.get("unlearned").asInt()).isEqualTo(1);
  assertThat(json.readTree(call(b,"GET",ROOT+"/overview",null).body()).get("unlearned").asInt()).isZero();
  assertThat(call(a,"PUT",ROOT+"/settings",Map.of("newGoal",0,"reviewGoal",201)).statusCode()).isEqualTo(400);
 }
}


