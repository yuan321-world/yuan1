package com.englishhero.reading;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ReadingWordbookKeywordsIT extends LearningHttpITSupport {
 final List<String> books=new ArrayList<>();
 String book(String lemma){
  String code="KW_"+UUID.randomUUID();
  jdbc.update("INSERT INTO vocabulary_wordbook(code,data_version,title,source_url,notice,manifest_hash,original_count,available_count,missing_phonetic_count,excluded_json,status) VALUES(?,'test',?,'https://example.test','test',?,1,1,0,'[]','PUBLISHED')",code,"词书 "+lemma,"0".repeat(64));
  String id=jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code=?",String.class,code);books.add(id);
  jdbc.update("INSERT INTO vocabulary_wordbook_entry(book_id,entry_id,sort_order) SELECT ?,id,1 FROM dictionary_entry WHERE lemma=?",id,lemma);return id;
 }
 @AfterEach void cleanBooks(){for(String id:books){jdbc.update("DELETE FROM vocabulary_learning_source WHERE book_id=?",id);jdbc.update("DELETE FROM vocabulary_wordbook_entry WHERE book_id=?",id);jdbc.update("DELETE FROM vocabulary_wordbook WHERE id=?",id);}}
 String article(java.net.http.HttpClient admin)throws Exception{
  var d=draft("Wordbook keywords fixture");
  var input=new ReadingDtos.DraftInput(d.categoryId(),d.title(),d.titleZh(),d.summary(),d.difficulty(),"Small steps improve our lives. Smallness is different. STEPS help.",d.author(),d.sourceType(),d.sourceUrl(),d.usageNote());
  var created=json.readTree(call(admin,"POST","/admin/articles",input).body());String id=created.get("id").asText();
  String version=json.readTree(call(admin,"GET","/admin/articles/"+id,null).body()).get("draft").get("id").asText();
  assertThat(call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0)).statusCode()).isEqualTo(200);
  return "/reading/articles/"+id+"/versions/"+version+"/tools";
 }
 @Test void currentBookMatchesWholeTokensAndFormsWithoutAllocatingProgress()throws Exception{
  var admin=actor(true);var one=actor(false);var two=actor(false);String path=article(admin),small=book("small"),step=book("step");
  call(one,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",small));
  var first=call(one,"GET",path,null);assertThat(first.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
  var result=json.readTree(first.body());assertThat(result.path("keywordSourceKind").asText()).isEqualTo("BOOK");assertThat(result.path("keywordSource").asText()).isEqualTo("词书 small");
  assertThat(result.path("keywords").size()).isEqualTo(1);assertThat(result.path("keywords").get(0).path("forms").toString()).contains("small").doesNotContain("smallness");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid(one))).isZero();
  call(one,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",step));result=json.readTree(call(one,"GET",path,null).body());
  assertThat(result.path("keywords").size()).isEqualTo(1);assertThat(result.path("keywords").get(0).path("lemma").asText()).isEqualTo("step");assertThat(result.path("keywords").get(0).path("forms").toString()).contains("steps");
  var other=json.readTree(call(two,"GET",path,null).body());assertThat(other.path("keywordSourceKind").asText()).isEqualTo("PERSONAL");assertThat(other.path("keywords").size()).isZero();
  var guest=json.readTree(call(anonymous(),"GET",path,null).body());assertThat(guest.path("keywordSourceKind").asText()).isEqualTo("EDITORIAL");assertThat(guest.toString()).doesNotContain("词书 small","词书 step");
 }
 @Test void personalSourceUsesOnlyOwnExplicitEnrollment()throws Exception{
  var admin=actor(true);var one=actor(false);var two=actor(false);String path=article(admin),word=word(one,"small");
  assertThat(call(one,"POST",ROOT+"/words",Map.of("wordIds",List.of(word))).statusCode()).isEqualTo(200);
  var result=json.readTree(call(one,"GET",path,null).body());assertThat(result.path("keywords").size()).isEqualTo(1);assertThat(result.path("keywords").get(0).path("lemma").asText()).isEqualTo("small");
  assertThat(json.readTree(call(two,"GET",path,null).body()).path("keywords").size()).isZero();
 }
 @Test void proficiencyUsesOnlyTheCurrentUsersSharedLearningRecordAndNeverChangesProgress()throws Exception{
  var admin=actor(true);var one=actor(false);var two=actor(false);String path=article(admin),book=book("small");
  for(var actor:List.of(one,two))assertThat(call(actor,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",book)).statusCode()).isEqualTo(200);
  long entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='small'",Long.class);
  jdbc.update("INSERT INTO vocabulary_learning_word(user_id,entry_id,proficient,personal_enrolled) VALUES(?,?,TRUE,FALSE)",uid(one),entry);
  var before=jdbc.queryForMap("SELECT stage,due_at,revision,proficient FROM vocabulary_learning_word WHERE user_id=? AND entry_id=?",uid(one),entry);
  assertThat(json.readTree(call(one,"GET",path,null).body()).path("keywords").size()).isZero();
  assertThat(json.readTree(call(two,"GET",path,null).body()).path("keywords").size()).isEqualTo(1);
  assertThat(jdbc.queryForMap("SELECT stage,due_at,revision,proficient FROM vocabulary_learning_word WHERE user_id=? AND entry_id=?",uid(one),entry)).isEqualTo(before);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid(two))).isZero();
 }

}
