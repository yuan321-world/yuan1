package com.englishhero.reading;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LearningCollectionIT extends LearningHttpITSupport {
 @Test void collectionFromAnOwnedSnapshotIsIdempotentAndDoesNotAdvanceLearning() throws Exception {
  var learner=actor(false);var other=actor(false);String original=word(learner,"agenda");assertThat(call(learner,"POST",ROOT+"/words",Map.of("wordIds",List.of(original))).statusCode()).isEqualTo(200);
  var start=call(learner,"POST",ROOT+"/sessions",Map.of("requestId",UUID.randomUUID().toString(),"kind","NEW","mode","SPELLING"));
  assertThat(start.statusCode()).isEqualTo(200);var session=json.readTree(start.body());String sid=session.get("id").asText();
  String entry=jdbc.queryForObject("SELECT entry_id FROM vocabulary_learning_word WHERE user_id=?",String.class,uid(learner));
  String word=jdbc.queryForObject("SELECT id FROM user_word WHERE user_id=? AND entry_id=?",String.class,uid(learner),entry);
  assertThat(call(learner,"DELETE","/vocabulary/words/"+word,null).statusCode()).isEqualTo(204);
  var input=Map.of("sessionId",sid,"entryId",entry);
  assertThat(call(other,"POST","/vocabulary/words/from-learning",input).statusCode()).isEqualTo(404);
  for(int i=0;i<2;i++)assertThat(call(learner,"POST","/vocabulary/words/from-learning",input).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_word_learning_origin WHERE word_id=?",Long.class,word)).isEqualTo(1);
  var detail=json.readTree(call(learner,"GET","/vocabulary/words/"+word,null).body());
  assertThat(detail.get("origins").size()).isEqualTo(1);assertThat(detail.get("sources").get("total").asInt()).isZero();
  assertThat(detail.get("word").get("learning").get("id").isTextual()).isTrue();
  var current=json.readTree(call(learner,"GET",ROOT+"/session",null).body());
  assertThat(current.get("version")).isEqualTo(session.get("version"));assertThat(current.get("current").get("id")).isEqualTo(session.get("current").get("id"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_learning_event WHERE user_id=?",Long.class,uid(learner))).isZero();
  String unrelated=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='summary'",String.class);
  assertThat(call(learner,"POST","/vocabulary/words/from-learning",Map.of("sessionId",sid,"entryId",unrelated)).statusCode()).isEqualTo(404);
  assertThat(call(learner,"GET","/vocabulary/words?q=agenda&status=LEARNING",null).statusCode()).isEqualTo(200);
  assertThat(json.readTree(call(learner,"GET","/vocabulary/words?q=notfoundword&status=ALL",null).body()).get("total").asInt()).isZero();
  assertThat(call(learner,"GET","/vocabulary/words?status=INVALID",null).statusCode()).isEqualTo(400);
 }
}
