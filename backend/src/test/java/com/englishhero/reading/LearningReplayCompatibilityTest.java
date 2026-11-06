package com.englishhero.reading;
import com.englishhero.vocabulary.learning.LearningDtos.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LearningReplayCompatibilityTest {
 @Test void oldPersistedResponseAndSnapshotDeserializeWithoutNewFields(){
  var json=JsonMapper.builder().build();
  var response=json.readValue("""
   {"id":"1","version":0,"kind":"NEW","mode":"SPELLING","status":"ACTIVE","phase":"QUESTION","current":{"id":"2","lemma":null,"phonetic":null,"partOfSpeech":"n.","meaning":"议程","extra":"","options":[],"spellingTarget":"agenda","definition":null,"source":null,"outcome":null},"queue":[{"id":"2","meaning":"议程","current":true,"failures":0}],"passed":0,"total":1,"results":[]}
   """,SessionDto.class);
  assertThat(response.current().id()).isEqualTo("2");assertThat(response.current().attemptId()).isZero();assertThat(response.current().assisted()).isFalse();assertThat(response.queue()).hasSize(1);
  var snapshot=json.readValue("""
   {"entry":{"entryId":"1","lemma":"agenda","phonetic":null,"senses":[{"partOfSpeech":"n.","definitionZh":"议程","definitionEn":""}],"sourceNote":"fixture"},"source":null,"options":[],"correctOptionId":null}
   """,Snapshot.class);
  assertThat(snapshot.detail()).isNull();assertThat(snapshot.entry().lemma()).isEqualTo("agenda");
 }
}
