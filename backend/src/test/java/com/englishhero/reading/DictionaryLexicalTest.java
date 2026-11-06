package com.englishhero.reading;
import com.englishhero.dictionary.*;
import static com.englishhero.dictionary.DictionaryDtos.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DictionaryLexicalTest {
    @Test void fixedExamplesAreStableBilingualAndBelongToTheActualLemma()throws Exception{
        var catalog=new DictionaryLexicalCatalog(new tools.jackson.databind.ObjectMapper(),true);
        var earth=catalog.examples("earth");assertThat(earth).hasSize(2);assertThat(earth).allSatisfy(e->{assertThat(e.text().toLowerCase()).contains("earth");assertThat(e.translationZh()).containsPattern("[\\p{IsHan}]");});
        assertThat(catalog.examples("earth")).isEqualTo(earth);assertThat(catalog.examples("zzzzunknown")).isEmpty();assertThat(catalog.examples("look")).isNotEmpty();assertThat(catalog.examples("closer")).isEmpty();assertThat(catalog.examples("mean")).isEmpty();assertThat(catalog.examples("affordable")).isEmpty();assertThat(catalog.examples("close").getFirst().text()).contains("close the window");assertThat(catalog.examples("work")).allSatisfy(e->assertThat(e.text()).doesNotContain("does not work","Working together"));
    }
    @Test void fillsOnlyMissingPhoneticsAndFindsExactOfflinePhrases()throws Exception{
        var json=new tools.jackson.databind.ObjectMapper();var catalog=new DictionaryLexicalCatalog(json,true);
        assertThat(catalog.phonetic(word("resource","n."))).isNotBlank();
        var original=new PublicEntry("fixture","resource","original",List.of(new Sense("n.","资源","")),"fixture");
        assertThat(catalog.phonetic(original)).isEqualTo("original");
        assertThat(catalog.findPhrase("  IN   advance  ")).isPresent().get().extracting(Phrase::text).isEqualTo("in advance");
        assertThat(catalog.findPhrase("in advanc")).isEmpty();
        assertThat(catalog.findPhrase("at arm’s length")).isPresent().isEqualTo(catalog.findPhrase("at arm's length"));
        assertThat(catalog.findPhrase("This is an arbitrary sentence.")).isEmpty();
        var disabled=new DictionaryLexicalCatalog(json,false);
        assertThat(disabled.phonetic(word("resource","n."))).isNull();assertThat(disabled.findPhrase("in advance")).isEmpty();
    }
    private PublicEntry word(String lemma,String pos){return new PublicEntry("fixture",lemma,null,List.of(new Sense(pos,"fixture","")),"fixture");}
    @Test void realSynsetsStayGroupedByEntryAndPartOfSpeech()throws Exception{
        var catalog=new DictionaryLexicalCatalog(new tools.jackson.databind.ObjectMapper(),true);
        var groups=catalog.synonyms(word("beginning","n."));
        assertThat(groups).isNotEmpty().allSatisfy(group->{
            assertThat(group.partOfSpeech()).isEqualTo("n.");assertThat(group.meaningEn()).isNotBlank();
            assertThat(group.words()).isNotEmpty().hasSizeLessThanOrEqualTo(6).doesNotContain("beginning");
        });
        assertThat(groups.stream().flatMap(g->g.words().stream())).contains("start");
        assertThat(catalog.synonyms(word("begin","v."))).allSatisfy(g->assertThat(g.partOfSpeech()).isEqualTo("v."));
        assertThat(catalog.synonyms(word("zzzzunknown","n."))).isEmpty();
        assertThat(catalog.synonyms(word("beginning","unclassified"))).isEmpty();
    }
    @Test void offlinePhrasesHaveChineseAndAreOptInWithWordBoundaries()throws Exception{
        var json=new tools.jackson.databind.ObjectMapper();var catalog=new DictionaryLexicalCatalog(json,true);
        assertThat(catalog.phrases("beginning")).isNotEmpty().hasSizeLessThanOrEqualTo(5).allSatisfy(phrase->{
            assertThat(phrase.translationZh()).containsPattern("[\\p{IsHan}]");
            assertThat(Arrays.asList(phrase.text().toLowerCase(Locale.ROOT).split(" "))).contains("beginning");
        });
        assertThat(catalog.phrases("beginning")).extracting(Phrase::text).contains("at the beginning");
        assertThat(catalog.phrases("zzzzunknown")).isEmpty();
        assertThat(new DictionaryLexicalCatalog(json,false).phrases("beginning")).isEmpty();
    }
}
