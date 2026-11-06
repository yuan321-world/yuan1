package com.englishhero.reading;

import com.englishhero.dictionary.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties="english-hero.dictionary.ecdict.enabled=true")
class DictionaryExpansionIT extends ReadingHttpITSupport {
    @Test void publicFixedExamplesAreIndependentOfTheReadingContextAndDoNotWrite()throws Exception{
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class);
        var first=json.readTree(call(anonymous(),"GET","/dictionary/lookup?word=earth",null).body()).at("/candidates/0/examples");
        assertThat(first.isArray()).isTrue();assertThat(first.size()).isEqualTo(2);assertThat(first.get(0).path("translationZh").asText()).isNotBlank();
        var second=json.readTree(call(anonymous(),"GET","/dictionary/lookup?word=earth",null).body()).at("/candidates/0/examples");assertThat(second).isEqualTo(first);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class)).isEqualTo(before);
    }
    @Test void publicPhraseLookupAndMissingPhoneticsDoNotChangeStoredEntries()throws Exception{
        String before=jdbc.queryForObject("SELECT CONCAT(id,':',COALESCE(phonetic,''),':',senses,':',source_note) FROM dictionary_entry WHERE lemma='resource'",String.class);
        var word=json.readTree(call(anonymous(),"GET","/dictionary/lookup?word=resources",null).body()).get("candidates").get(0);
        assertThat(word.get("phonetic").asText()).isNotBlank();assertThat(word.get("phoneticSourceNote").asText()).contains("ECDICT");
        var response=call(anonymous(),"GET","/dictionary/phrase?text=in%20advance",null);
        assertThat(response.statusCode()).isEqualTo(200);assertThat(json.readTree(response.body()).get("phrase").get("translationZh").asText()).contains("预先");
        var miss=call(anonymous(),"GET","/dictionary/phrase?text=in%20advanc",null);assertThat(miss.statusCode()).isEqualTo(200);assertThat(json.readTree(miss.body()).get("found").asBoolean()).isFalse();
        assertThat(call(anonymous(),"GET","/dictionary/phrase?text=",null).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT CONCAT(id,':',COALESCE(phonetic,''),':',senses,':',source_note) FROM dictionary_entry WHERE lemma='resource'",String.class)).isEqualTo(before);
    }
    @Test void lexicalAdditionsArePublicBoundedAndKeepCandidateMeaningsSeparate()throws Exception{
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class);
        var response=call(anonymous(),"GET","/dictionary/lookup?word=beginning",null);assertThat(response.statusCode()).isEqualTo(200);
        var candidates=json.readTree(response.body()).get("candidates");
        boolean noun=false;
        for(var candidate:candidates){
            assertThat(candidate.get("collection").isNull()).isTrue();
            assertThat(candidate.get("synonyms").size()).isLessThanOrEqualTo(6);
            assertThat(candidate.get("phrases").size()).isLessThanOrEqualTo(5);
            assertThat(candidate.get("lexicalSourceNote").asText()).contains("WordNet 3.0","ECDICT");
            if(candidate.get("lemma").asText().equals("beginning")){
                noun=true;assertThat(candidate.get("synonyms").size()).isPositive();
                for(var group:candidate.get("synonyms"))assertThat(group.get("partOfSpeech").asText()).isEqualTo("n.");
                assertThat(candidate.get("phrases").toString()).contains("at the beginning");
            }
            if(candidate.get("lemma").asText().equals("begin"))for(var group:candidate.get("synonyms"))assertThat(group.get("partOfSpeech").asText()).isEqualTo("v.");
        }
        assertThat(noun).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class)).isEqualTo(before);
    }
    @org.springframework.beans.factory.annotation.Autowired EcdictDataLoader supplement;
    @Test void expandedEntryCollectsItsRealSourceAndRejectsOtherOwners() throws Exception {
        var admin=actor(true);var reader=actor(false);var other=actor(false);
        var input=ArticleRulesTest.draft("Expanded word source","Annual meetings help a committee plan. Children learn by running.","ORIGINAL",null);
        var created=json.readTree(call(admin,"POST","/admin/articles",input).body());String id=created.get("id").asText(),version=created.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        call(reader,"POST","/reading/articles/"+id+"/start",null);
        String entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='annual'",String.class);
        var collect=java.util.Map.of("articleVersionId",version,"sentenceKey","p0001-s0001","startOffset",0,"endOffset",6,"entryId",entry);
        var saved=call(reader,"POST","/vocabulary/words",collect);assertThat(saved.statusCode()).isEqualTo(201);
        String word=json.readTree(saved.body()).get("word").get("id").asText();
        assertThat(call(reader,"POST","/vocabulary/words",collect).statusCode()).isEqualTo(200);
        assertThat(call(other,"GET","/vocabulary/words/"+word,null).statusCode()).isEqualTo(404);
        assertThat(call(other,"DELETE","/vocabulary/words/"+word,null).statusCode()).isEqualTo(404);
        assertThat(json.readTree(call(reader,"GET","/vocabulary/words/"+word,null).body()).get("sources").get("total").asInt()).isEqualTo(1);
        assertThat(call(reader,"DELETE","/vocabulary/words/"+word,null).statusCode()).isEqualTo(204);
        assertThat(json.readTree(call(reader,"POST","/vocabulary/words",collect).body()).get("word").get("id").asText()).isEqualTo(word);
    }
    @Test void expansionIsIdempotentPreservesOriginalIdsAndServesChinese() throws Exception {
        long original=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='study'",Long.class);
        String originalNote=jdbc.queryForObject("SELECT source_note FROM dictionary_entry WHERE id=?",String.class,original);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM dictionary_entry",Long.class);
        try(var input=new org.springframework.core.io.ClassPathResource("dictionary/ecdict-local/entries.json").getInputStream()){supplement.load(input.readAllBytes());}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dictionary_entry",Long.class)).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='study'",Long.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT source_note FROM dictionary_entry WHERE id=?",String.class,original)).isEqualTo(originalNote);
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class);
        for(String surface:java.util.List.of("annual","committee","elaborate","running","children")) {
            var response=call(anonymous(),"GET","/dictionary/lookup?word="+surface,null);
            assertThat(response.statusCode()).isEqualTo(200);
            var body=json.readTree(response.body()); assertThat(body.get("found").asBoolean()).as(surface).isTrue();
            assertThat(body.get("candidates").get(0).get("senses").get(0).get("definitionZh").asText()).isNotBlank();
            assertThat(body.get("candidates").get(0).get("collection").isNull()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class)).isEqualTo(before);
    }
}
