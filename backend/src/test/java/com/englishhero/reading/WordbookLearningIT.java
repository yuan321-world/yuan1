package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import com.englishhero.vocabulary.learning.WordbookDataLoader;
import static org.assertj.core.api.Assertions.*;
class WordbookLearningIT extends LearningHttpITSupport {
 @Autowired WordbookDataLoader loader;
 byte[] manifest()throws Exception{return new ClassPathResource("vocabulary/ecdict-cet4-local/manifest.json").getContentAsByteArray();}
 byte[] entries()throws Exception{return new ClassPathResource("vocabulary/ecdict-cet4-local/entries.json").getContentAsByteArray();}
 @BeforeEach void reference()throws Exception{loader.load(manifest(),entries());jdbc.update("UPDATE vocabulary_wordbook SET status='PUBLISHED' WHERE code='CET4_REFERENCE'");}
 String book(){return jdbc.queryForObject("SELECT id FROM vocabulary_wordbook WHERE code='CET4_REFERENCE'",String.class);}
 @AfterEach void restoreBook(){jdbc.update("UPDATE vocabulary_wordbook SET status='UNLISTED' WHERE code='CET4_REFERENCE'");}
 @Test void selectingIsLazyAndExistingProgressIsShared()throws Exception{
  var a=actor(false);String b=book();long u=uid(a);assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,u)).isZero();
  var catalog=json.readTree(call(a,"GET",ROOT+"/wordbooks",null).body());assertThat(catalog.values()).anySatisfy(row->{assertThat(row.path("id").asText()).isEqualTo(b);assertThat(row.path("availableCount").asInt()).isEqualTo(3846);});
  var started=call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION"));assertThat(started.statusCode()).as(started.body()).isEqualTo(200);var s=json.readTree(started.body());assertThat(s.get("total").asInt()).isEqualTo(20);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=? AND personal_enrolled=FALSE",Integer.class,u)).isEqualTo(20);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","PERSONAL")).statusCode()).isEqualTo(409);
  String item=s.get("current").get("id").asText();var input=Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0,"itemId",item,"action","KNOWN");assertThat(call(a,"POST",ROOT+"/sessions/"+s.get("id").asText()+"/feedback",input).statusCode()).isEqualTo(200);
  long entry=jdbc.queryForObject("SELECT w.entry_id FROM vocabulary_learning_item i JOIN vocabulary_learning_word w ON w.id=i.learning_word_id WHERE i.id=?",Long.class,item);jdbc.update("INSERT INTO user_word(user_id,entry_id) VALUES(?,?)",u,entry);String word=jdbc.queryForObject("SELECT id FROM user_word WHERE user_id=? AND entry_id=?",String.class,u,entry);
  assertThat(json.readTree(call(a,"GET",ROOT+"/candidates",null).body()).get("items").get(0).get("joined").asBoolean()).isFalse();
  assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(word))).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT stage FROM vocabulary_learning_word WHERE user_id=? AND entry_id=?",Integer.class,u,entry)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT personal_enrolled FROM vocabulary_learning_word WHERE user_id=? AND entry_id=?",Boolean.class,u,entry)).isTrue();
 }
 @Test void unknownOrUnlistedBooksCannotStartNewTasksAndImportIsIdempotent()throws Exception{
  var a=actor(false);String b=book();long before=jdbc.queryForObject("SELECT COUNT(*) FROM dictionary_entry",Long.class);loader.load(manifest(),entries());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dictionary_entry",Long.class)).isEqualTo(before);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId","999999999")).statusCode()).isEqualTo(404);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(200);jdbc.update("UPDATE vocabulary_wordbook SET status='UNLISTED' WHERE id=?",b);
  assertThat(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","SPELLING")).statusCode()).isEqualTo(409);assertThat(json.readTree(call(a,"GET",ROOT+"/overview",null).body()).get("source").get("available").asBoolean()).isFalse();
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","PERSONAL")).statusCode()).isEqualTo(200);assertThat(call(anonymous(),"GET",ROOT+"/wordbooks",null).statusCode()).isEqualTo(401);
 }
 @Test void pausedBookAllowsOtherSourceWithoutDuplicatingReservedWords()throws Exception{
  var a=actor(false);long u=uid(a);String b=book();assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(200);
  var started=call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","SPELLING"));var s=json.readTree(started.body());assertThat(started.statusCode()).as(started.body()).isEqualTo(200);
  String sid=s.get("id").asText();var pause=Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",s.get("version").asInt(),"status","PAUSED");assertThat(call(a,"POST",ROOT+"/sessions/"+sid+"/state",pause).statusCode()).isEqualTo(200);
  long entry=jdbc.queryForObject("SELECT w.entry_id FROM vocabulary_learning_item i JOIN vocabulary_learning_word w ON w.id=i.learning_word_id WHERE i.session_id=? ORDER BY i.id LIMIT 1",Long.class,sid);jdbc.update("INSERT INTO user_word(user_id,entry_id) VALUES(?,?)",u,entry);String word=jdbc.queryForObject("SELECT id FROM user_word WHERE user_id=? AND entry_id=?",String.class,u,entry);assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(word))).statusCode()).isEqualTo(200);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","PERSONAL")).statusCode()).isEqualTo(200);
  var overview=json.readTree(call(a,"GET",ROOT+"/overview",null).body());assertThat(overview.get("savedSessions").size()).isEqualTo(1);
  // This personal word is already reserved by the paused book round.
  assertThat(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION")).statusCode()).isEqualTo(409);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_session WHERE user_id=?",Integer.class,u)).isEqualTo(1);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(200);
  var resumed=json.readTree(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION")).body());assertThat(resumed.get("id").asText()).isEqualTo(sid);assertThat(resumed.get("mode").asText()).isEqualTo("SPELLING");assertThat(resumed.get("current")).isEqualTo(s.get("current"));
 }
 @Test void savedRoundsDoNotConsumeAnotherSourcesNewGoal()throws Exception{
  var a=actor(false);long u=uid(a);String w=word(a,"agenda");assertThat(call(a,"POST",ROOT+"/words",Map.of("wordIds",List.of(w))).statusCode()).isEqualTo(200);call(a,"PUT",ROOT+"/settings",Map.of("newGoal",1,"reviewGoal",50));
  var s=json.readTree(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION")).body());call(a,"POST",ROOT+"/sessions/"+s.get("id").asText()+"/state",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",s.get("version").asInt(),"status","PAUSED"));assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",book())).statusCode()).isEqualTo(200);
  assertThat(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION")).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_session WHERE user_id=?",Integer.class,u)).isEqualTo(2);
 }
 @Test void savedUnlistedBookRemainsSelectableOnlyForItsOwner()throws Exception{
  var a=actor(false);var other=actor(false);String b=book();call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b));var s=json.readTree(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","RECOGNITION")).body());call(a,"POST",ROOT+"/sessions/"+s.get("id").asText()+"/state",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",s.get("version").asInt(),"status","PAUSED"));call(a,"PUT",ROOT+"/source",Map.of("kind","PERSONAL"));jdbc.update("UPDATE vocabulary_wordbook SET status='UNLISTED' WHERE id=?",b);
  assertThat(json.readTree(call(a,"GET",ROOT+"/wordbooks",null).body()).values()).anyMatch(row->b.equals(row.path("id").asText()));assertThat(json.readTree(call(other,"GET",ROOT+"/wordbooks",null).body()).values()).noneMatch(row->b.equals(row.path("id").asText()));assertThat(call(other,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(404);
  assertThat(call(a,"PUT",ROOT+"/source",Map.of("kind","BOOK","bookId",b)).statusCode()).isEqualTo(200);var resumed=json.readTree(call(a,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","SPELLING")).body());assertThat(resumed.get("id")).isEqualTo(s.get("id"));
 }
}
