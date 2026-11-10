package com.englishhero.reading;

import java.util.*;
import java.net.http.HttpClient;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

class WordbookWordsIT extends LearningHttpITSupport {
 String book;
 @BeforeEach void bookFixture(){
  String code="WORDS_IT_"+UUID.randomUUID();
  jdbc.update("INSERT INTO vocabulary_wordbook(code,data_version,title,source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json,status) VALUES(?,'test','词表测试书','https://example.test','test',?,3,3,0,'[]','PUBLISHED')",code,"0".repeat(64));
  book=jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code=?",String.class,code);
  int order=0;for(String lemma:List.of("summary","agenda","horizon"))jdbc.update("INSERT INTO vocabulary_wordbook_entry(book_id,entry_id,sort_order) SELECT ?,id,? FROM dictionary_entry WHERE lemma=?",book,++order,lemma);
 }
 @AfterEach void removeBook(){jdbc.update("DELETE FROM vocabulary_learning_source WHERE book_id=?",book);jdbc.update("DELETE FROM vocabulary_wordbook_entry WHERE book_id=?",book);jdbc.update("DELETE FROM vocabulary_wordbook WHERE id=?",book);}
 JsonNode list(HttpClient a,String suffix)throws Exception{var r=call(a,"GET",ROOT+"/wordbooks/"+book+"/words"+suffix,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 String entry(String lemma){return jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma=?",String.class,lemma);}
 @Test void browsingIsLazyAndFiltersCountsAreOwnerScoped()throws Exception{
  var a=actor(false);var b=actor(false);long u=uid(a);
  var initial=list(a,"");assertThat(initial.path("counts").path("total").asInt()).isEqualTo(3);assertThat(initial.path("counts").path("unlearned").asInt()).isEqualTo(3);
  assertThat(initial.path("items").get(0).path("lemma").asText()).isEqualTo("summary");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,u)).isZero();
  assertThat(json.readTree(call(a,"GET",ROOT+"/source",null).body()).path("kind").asText()).isEqualTo("PERSONAL");
  word(a,"agenda");jdbc.update("INSERT INTO vocabulary_learning_word(user_id,entry_id,stage,due_at,active) VALUES(?,?,2,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY),FALSE)",u,entry("agenda"));
  jdbc.update("INSERT INTO vocabulary_learning_word(user_id,entry_id,proficient) VALUES(?,?,TRUE)",u,entry("horizon"));
  var all=list(a,"");assertThat(all.path("counts").path("reviewing").asInt()).isEqualTo(1);assertThat(all.path("counts").path("proficient").asInt()).isEqualTo(1);
  assertThat(list(a,"?status=REVIEW&collectedOnly=true").path("items").get(0).path("lemma").asText()).isEqualTo("agenda");
  assertThat(list(a,"?q=agenda").path("total").asInt()).isEqualTo(1);
  assertThat(list(a,"?q=%25").path("total").asInt()).isZero();
  assertThat(list(a,"?q=definitionZh").path("total").asInt()).isZero();
  assertThat(list(a,"?sort=ALPHA&pageSize=1&page=2").path("items").get(0).path("lemma").asText()).isEqualTo("horizon");
  assertThat(list(b,"").path("counts").path("unlearned").asInt()).isEqualTo(3);
  assertThat(json.readTree(call(b,"GET",ROOT+"/wordbooks/personal/words",null).body()).path("total").asInt()).isZero();
 }
 @Test void proficientIsSharedAndRestoreDoesNotEnrollOrInventLearning()throws Exception{
  var a=actor(false);var b=actor(false);String path=ROOT+"/wordbooks/"+book+"/words/"+entry("agenda")+"/proficiency";
  assertThat(call(a,"PUT",path,Map.of("proficient",true)).statusCode()).isEqualTo(200);
  assertThat(list(a,"?status=PROFICIENT").path("total").asInt()).isEqualTo(1);
  assertThat(list(b,"?status=PROFICIENT").path("total").asInt()).isZero();
  // A second real book references the same entries; mastery belongs to the word/user.
  jdbc.update("INSERT INTO vocabulary_wordbook(code,data_version,title,source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json,status) SELECT CONCAT(code,'B'),data_version,'重叠词书',source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json,status FROM vocabulary_wordbook WHERE id=?",book);
  String second=jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code=(SELECT CONCAT(code,'B') FROM (SELECT code FROM vocabulary_wordbook WHERE id=?) original)",String.class,book);
  try{
   jdbc.update("INSERT INTO vocabulary_wordbook_entry(book_id,entry_id,sort_order) SELECT ?,entry_id,sort_order FROM vocabulary_wordbook_entry WHERE book_id=?",second,book);
   var shared=json.readTree(call(a,"GET",ROOT+"/wordbooks/"+second+"/words?status=PROFICIENT",null).body());assertThat(shared.path("total").asInt()).isEqualTo(1);assertThat(shared.path("items").get(0).path("entryId").asText()).isEqualTo(entry("agenda"));
  }finally{jdbc.update("DELETE FROM vocabulary_wordbook_entry WHERE book_id=?",second);jdbc.update("DELETE FROM vocabulary_wordbook WHERE id=?",second);}
  assertThat(call(a,"PUT",path,Map.of("proficient",false)).statusCode()).isEqualTo(200);
  assertThat(list(a,"?status=NEW").path("total").asInt()).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT personal_enrolled FROM vocabulary_learning_word WHERE user_id=? AND entry_id=?",Boolean.class,uid(a),entry("agenda"))).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Integer.class,uid(a))).isZero();
  assertThat(call(a,"PUT",ROOT+"/wordbooks/"+book+"/words/"+entry("benefit")+"/proficiency",Map.of("proficient",true)).statusCode()).isEqualTo(404);
 }
 @Test void unavailableAndInvalidQueriesAreRejected()throws Exception{
  var a=actor(false);String path=ROOT+"/wordbooks/"+book+"/words";
  assertThat(call(anonymous(),"GET",path,null).statusCode()).isEqualTo(401);
  for(String q:List.of("?status=PAUSED","?sort=invalid","?page=0","?pageSize=51"))assertThat(call(a,"GET",path+q,null).statusCode()).isEqualTo(400);
  jdbc.update("UPDATE vocabulary_wordbook SET status='UNLISTED' WHERE id=?",book);
  assertThat(call(a,"GET",path,null).statusCode()).isEqualTo(404);
  assertThat(call(a,"PUT",path+"/"+entry("agenda")+"/proficiency",Map.of("proficient",true)).statusCode()).isEqualTo(404);
 }
 @Test void restoringKeepsStageAndDueDateAndPersonalListsOnlyOwnEnrollment()throws Exception{
  var a=actor(false);var b=actor(false);long u=uid(a);String word=word(a,"agenda");
  call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(word)));
  jdbc.update("UPDATE vocabulary_learning_word SET stage=3,due_at='2026-12-01 21:00:00' WHERE user_id=?",u);
  var due=jdbc.queryForObject("SELECT due_at FROM vocabulary_learning_word WHERE user_id=?",java.sql.Timestamp.class,u);
  String path=ROOT+"/wordbooks/"+book+"/words/"+entry("agenda")+"/proficiency";
  assertThat(call(a,"PUT",path,Map.of("proficient",true)).statusCode()).isEqualTo(200);
  var restored=json.readTree(call(a,"PUT",path,Map.of("proficient",false)).body());assertThat(restored.path("word").path("state").asText()).isEqualTo("REVIEW");
  assertThat(jdbc.queryForObject("SELECT stage FROM vocabulary_learning_word WHERE user_id=?",Integer.class,u)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT due_at FROM vocabulary_learning_word WHERE user_id=?",java.sql.Timestamp.class,u)).isEqualTo(due);
  var personal=json.readTree(call(a,"GET",ROOT+"/wordbooks/personal/words",null).body());assertThat(personal.path("total").asInt()).isEqualTo(1);assertThat(personal.path("items").get(0).path("entryId").asText()).isEqualTo(entry("agenda"));
  assertThat(call(b,"PUT",ROOT+"/wordbooks/personal/words/"+entry("agenda")+"/proficiency",Map.of("proficient",true)).statusCode()).isEqualTo(404);
 }
}
