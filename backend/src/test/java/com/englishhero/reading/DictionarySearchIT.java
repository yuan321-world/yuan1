package com.englishhero.reading;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(properties="english-hero.dictionary.ecdict.enabled=true")
class DictionarySearchIT extends LearningHttpITSupport {
 @org.junit.jupiter.api.AfterEach void cleanSearchPhrases(){
  for(String name:users)for(long id:jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name))jdbc.update("DELETE FROM vocabulary_phrase WHERE user_id=?",id);
 }
 @Test void multiwordSearchAndPhraseCollectionUseThePhraseNotebook()throws Exception{
  var response=call(anonymous(),"GET","/dictionary/search?q=play%20a",null);
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
  var items=json.readTree(response.body()).path("items");
  assertThat(items.values()).anySatisfy(item->{assertThat(item.path("lemma").asText()).isEqualTo("play a role");assertThat(item.path("kind").asText()).isEqualTo("PHRASE");});
  String play=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='play'",String.class);
  assertThat(items.values()).filteredOn(item->item.path("lemma").asText().equals("play a role")).allSatisfy(item->assertThat(item.path("relatedEntryIds").values()).anySatisfy(id->assertThat(id.asText()).isEqualTo(play)));
  var detail=call(anonymous(),"GET","/dictionary/phrase?text=play%20a%20role",null);
  assertThat(json.readTree(detail.body()).path("found").asBoolean()).isTrue();
  var a=actor(false);var input=Map.of("text","play a role","meaningZh","发挥作用；扮演角色");
  var first=call(a,"POST","/vocabulary/phrases",input);
  assertThat(first.statusCode()).as(first.body()).isIn(200,201);
  var repeat=call(a,"POST","/vocabulary/phrases",input);
  assertThat(json.readTree(repeat.body()).path("id").asText()).isEqualTo(json.readTree(first.body()).path("id").asText());
  assertThat(json.readTree(repeat.body()).path("enrolled").asBoolean()).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_word WHERE user_id=?",Integer.class,uid(a))).isZero();
 }
 @Test void expandedWordsAndSurfaceFormsKeepTheCanonicalEntry()throws Exception{
  var response=call(anonymous(),"GET","/dictionary/search?q=apple",null);
  var items=json.readTree(response.body()).path("items");
  assertThat(items.get(0).path("lemma").asText()).isEqualTo("apple");
  assertThat(items.values()).anySatisfy(item->{assertThat(item.path("lemma").asText()).isEqualTo("apples");assertThat(item.path("canonicalLemma").asText()).isEqualTo("apple");assertThat(item.path("kind").asText()).isEqualTo("FORM");});
  assertThat(items.values()).anySatisfy(item->assertThat(item.path("lemma").asText()).isEqualTo("apple juice"));
  assertThat(json.readTree(call(anonymous(),"GET","/dictionary/lookup?word=applet",null).body()).path("found").asBoolean()).isTrue();
  String look=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='look'",String.class);
  var mixed=json.readTree(call(anonymous(),"GET","/dictionary/search?q=look%20al",null).body()).path("items");
  assertThat(mixed.values()).anySatisfy(item->{assertThat(item.path("lemma").asText()).isEqualToIgnoringCase("look alive");assertThat(item.path("relatedEntryIds").values()).anySatisfy(id->assertThat(id.asText()).isEqualTo(look));});
 }
 @Test void searchPrioritizesExactWordsAndOnlyReturnsLocalEntries()throws Exception{
  var response=call(anonymous(),"GET","/dictionary/search?q=AGENDA",null);
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
  var items=json.readTree(response.body()).path("items");
  assertThat(items.size()).isBetween(1,10);
  assertThat(items.get(0).path("lemma").asText()).isEqualTo("agenda");
  assertThat(items.get(0).path("definitionZh").asText()).isNotBlank();
  var prefix=json.readTree(call(anonymous(),"GET","/dictionary/search?q=ag",null).body()).path("items");
  assertThat(prefix.size()).isBetween(1,10);
  assertThat(prefix.values()).allSatisfy(item->assertThat(item.path("lemma").asText()).startsWith("ag"));
  assertThat(call(anonymous(),"GET","/dictionary/search?q=%25",null).statusCode()).isEqualTo(400);
  assertThat(json.readTree(call(anonymous(),"GET","/dictionary/search?q=zzzzzzzzzzzzzzzzzzzzzz",null).body()).path("items").isEmpty()).isTrue();
 }
 @Test void dictionaryCollectionIsOwnedIdempotentAndDoesNotEnrollTheWord()throws Exception{
  var a=actor(false);var other=actor(false);
  String entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='agenda'",String.class);
  var input=Map.of("word","agenda","entryId",entry);
  assertThat(call(anonymous(),"POST","/vocabulary/words/from-dictionary",input).statusCode()).isEqualTo(401);
  assertThat(call(a,"POST","/vocabulary/words/from-dictionary",input).statusCode()).isEqualTo(201);
  var repeat=call(a,"POST","/vocabulary/words/from-dictionary",input);
  assertThat(repeat.statusCode()).isEqualTo(200);
  String id=json.readTree(repeat.body()).path("word").path("id").asText();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_word_learning_origin WHERE word_id=? AND source_kind='DICTIONARY'",Integer.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_word WHERE user_id=?",Integer.class,uid(a))).isZero();
  assertThat(call(other,"GET","/vocabulary/words/"+id,null).statusCode()).isEqualTo(404);
  assertThat(call(a,"DELETE","/vocabulary/words/"+id,null).statusCode()).isEqualTo(204);
  assertThat(call(a,"POST","/vocabulary/words/from-dictionary",input).statusCode()).isEqualTo(200);
  assertThat(call(a,"POST","/vocabulary/words/from-dictionary",Map.of("word","summary","entryId",entry)).statusCode()).isEqualTo(400);
 }
}
