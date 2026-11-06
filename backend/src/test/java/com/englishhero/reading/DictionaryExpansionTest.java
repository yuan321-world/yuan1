package com.englishhero.reading;

import com.englishhero.dictionary.*;
import static com.englishhero.dictionary.DictionaryDtos.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DictionaryExpansionTest {
    private PublicEntry word(String lemma,String pos) { return new PublicEntry("1",lemma,null,List.of(new Sense(pos,"测试释义","")),"fixture"); }
    @Test void providerUsesFallbackOnlyAfterExactMissAndCachesPublicEntries() {
        var mapper=org.mockito.Mockito.mock(DictionaryMapper.class);
        org.mockito.Mockito.when(mapper.manifest()).thenReturn("fixture-v1");
        org.mockito.Mockito.when(mapper.lookup("raising")).thenReturn(List.of());
        org.mockito.Mockito.when(mapper.lemmas(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(word("raise","v."),word("rais","n.")));
        var provider=new LocalDictionaryProvider(mapper,java.time.Clock.systemUTC());
        assertThat(provider.lookup("raising")).extracting(PublicEntry::lemma).containsExactly("raise");
        provider.lookup("raising");org.mockito.Mockito.verify(mapper,org.mockito.Mockito.times(1)).lemmas(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.when(mapper.lookup("saw")).thenReturn(List.of(word("saw","n.")));
        assertThat(provider.lookup("saw")).extracting(PublicEntry::lemma).containsExactly("saw");
        org.mockito.Mockito.verify(mapper,org.mockito.Mockito.times(1)).lemmas(org.mockito.ArgumentMatchers.anyList());
    }
    @Test void boundedCandidatesMustPassPartOfSpeechAndRoundTripValidation() {
        for (var pair : Map.of("studies","study","carried","carry","raising","raise","running","run","planned","plan","boxes","box","happier","happy","largest","large").entrySet()) {
            assertThat(MorphologyResolver.candidates(pair.getKey())).contains(pair.getValue());
            assertThat(MorphologyResolver.matches(pair.getKey(),word(pair.getValue(),"n. v. adj."))).as(pair.toString()).isTrue();
        }
        assertThat(MorphologyResolver.matches("studys",word("study","v."))).isFalse();
        assertThat(MorphologyResolver.matches("booked",word("book","n."))).isFalse();
        assertThat(MorphologyResolver.matches("unknowns",word("other","n."))).isFalse();
        for(var pair:Map.of("runing","run","stoped","stop","biger","big","hotest","hot","goed","go","childs","child","gooder","good").entrySet())
            assertThat(MorphologyResolver.matches(pair.getKey(),word(pair.getValue(),pair.getValue().equals("child")?"n.":Set.of("big","hot","good").contains(pair.getValue())?"adj.":"v."))).as("reject misspelling "+pair.getKey()).isFalse();
        assertThat(MorphologyResolver.candidates("a".repeat(64))).hasSizeLessThanOrEqualTo(16);
        assertThat(MorphologyResolver.candidates("well-being")).isEmpty();
        for(String pair:List.of("gived:give","getted:get","knowed:know","telled:tell","sayed:say","sleeped:sleep","builded:build","losed:lose","winned:win","teached:teach","catched:catch","admited:admit","prefered:prefer","begining:begin")) {
            String[] parts=pair.split(":");assertThat(MorphologyResolver.matches(parts[0],word(parts[1],"v."))).as(pair).isFalse();
        }
    }
    @Test void irregularFormsAreValidatedAgainstRealEntries() {
        assertThat(MorphologyResolver.candidates("went")).contains("go");
        assertThat(MorphologyResolver.matches("went",word("go","v."))).isTrue();
        assertThat(MorphologyResolver.matches("children",word("child","n."))).isTrue();
        assertThat(MorphologyResolver.matches("better",word("good","adj."))).isTrue();
        assertThat(MorphologyResolver.matches("went",word("be","v."))).isFalse();
    }
    @Test void offlineSubsetHasPinnedCoverageAndHonestFields() throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();
        var data=mapper.readValue(Files.readAllBytes(Path.of("../content/dictionary/ecdict-local/entries.json")),Dataset.class);
        EcdictDataLoader.validate(data);
        assertThat(data.entries()).hasSize(10000);
        for(String lemma:List.of("annual","committee","elaborate")) {
            var entry=data.entries().stream().filter(e->e.lemma().equals(lemma)).findFirst().orElseThrow();
            assertThat(entry.phonetic()).isNotBlank();
            assertThat(entry.senses()).allSatisfy(s->assertThat(s.definitionZh()).isNotBlank());
        }
        assertThat(data.sourceNote()).contains("ECDICT","MIT","本地开发");
    }
}
