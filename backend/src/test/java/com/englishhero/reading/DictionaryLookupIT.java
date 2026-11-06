package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DictionaryLookupIT extends ReadingHttpITSupport {
    @org.springframework.beans.factory.annotation.Autowired com.englishhero.dictionary.DictionaryDataLoader loader;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.englishhero.dictionary.LocalDictionaryProvider provider;
    @Test void packagedDataIsIdempotentAndServiceFailureIsLocal()throws Exception {
        var learner=actor(false);
        byte[] data=new org.springframework.core.io.ClassPathResource("dictionary/entries.v1.json").getInputStream().readAllBytes();
        long entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='study'",Long.class);
        loader.load(data);assertThat(jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='study'",Long.class)).isEqualTo(entry);
        byte[] changed=new String(data,java.nio.charset.StandardCharsets.UTF_8).replace("学习用途","语言学习用途").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(()->loader.load(changed)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture unavailable")).when(provider).lookup("failure");
        try{assertThat(call(learner,"GET","/dictionary/lookup?word=failure",null).statusCode()).isEqualTo(503);}
        finally{org.mockito.Mockito.reset(provider);}
        assertThat(json.readTree(call(learner,"GET","/dictionary/lookup?word=studys",null).body()).get("found").asBoolean()).isFalse();
    }
    @Test void dictionaryUsesExplicitFormsWithoutCreatingPersonalRecords()throws Exception {
        var learner=actor(false);
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class);
        var result=call(learner,"GET","/dictionary/lookup?word=studies",null);
        assertThat(result.statusCode()).isEqualTo(200);
        var body=json.readTree(result.body());assertThat(body.get("found").asBoolean()).isTrue();
        assertThat(body.get("candidates").get(0).get("lemma").asText()).isEqualTo("study");
        assertThat(body.get("candidates").get(0).get("collection").isNull()).isTrue();
        assertThat(json.readTree(call(learner,"GET","/dictionary/lookup?word=zzzzunknown",null).body()).get("found").asBoolean()).isFalse();
        assertThat(call(learner,"GET","/dictionary/lookup?word=two%20words",null).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Long.class)).isEqualTo(before);
        assertThat(call(anonymous(),"GET","/dictionary/lookup?word=study",null).statusCode()).isEqualTo(200);
    }
}
