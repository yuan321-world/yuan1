package com.englishhero.reading;
import com.englishhero.dictionary.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DictionaryRulesTest {
    @Test void searchAcceptsPhrasesWithoutRelaxingSingleWordLookup(){
        assertThat(DictionaryRules.normalizeSearch("  Play   a ROLE ")).isEqualTo("play a role");
        assertThat(DictionaryRules.normalizeSearch("learner’s guide")).isEqualTo("learner's guide");
        for(String bad:List.of("","%","apple_","play a role.","a ".repeat(11),"a".repeat(81)))assertThatThrownBy(()->DictionaryRules.normalizeSearch(bad)).isInstanceOf(com.englishhero.web.BusinessException.class);
        assertThatThrownBy(()->DictionaryRules.normalize("play a role")).isInstanceOf(com.englishhero.web.BusinessException.class);
    }
    @Test void sharedTokenCasesAgreeWithFrontend()throws Exception {
        var data=new tools.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../content/english-token-cases.v1.json")));
        for(var sample:data.get("cases")){
            if(sample.get("normalized").isNull())assertThatThrownBy(()->DictionaryRules.normalize(sample.get("surface").asText())).isInstanceOf(com.englishhero.web.BusinessException.class);
            else assertThat(DictionaryRules.normalize(sample.get("surface").asText())).isEqualTo(sample.get("normalized").asText());
        }
    }
    @Test void tokensAreWholeAndConservative(){
        assertThat(DictionaryRules.normalize("learner’s")).isEqualTo("learner's");
        assertThat(DictionaryRules.normalize("can't")).isEqualTo("can't");
        assertThat(DictionaryRules.normalize("well-being")).isEqualTo("well-being");
        for(String bad:List.of("two words","study.","a".repeat(65),"\uD800"))assertThatThrownBy(()->DictionaryRules.normalize(bad)).isInstanceOf(com.englishhero.web.BusinessException.class);
    }
    @Test void cacheIsBoundedExpiresAndChangesWithDataset(){
        var mapper=mock(DictionaryMapper.class);when(mapper.manifest()).thenReturn("v1");when(mapper.lookup(anyString())).thenReturn(List.of());
        var clock=new MutableClock();var provider=new LocalDictionaryProvider(mapper,clock);
        provider.lookup("missing");provider.lookup("missing");verify(mapper,times(1)).lookup("missing");
        clock.now=clock.now.plusSeconds(301);provider.lookup("missing");verify(mapper,times(2)).lookup("missing");
        when(mapper.manifest()).thenReturn("v2");provider.lookup("missing");verify(mapper,times(3)).lookup("missing");
        for(int i=0;i<520;i++)provider.lookup("key"+i);
        provider.lookup("missing");verify(mapper,times(4)).lookup("missing");
    }
    static class MutableClock extends Clock { Instant now=Instant.parse("2026-10-16T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;} }
}
