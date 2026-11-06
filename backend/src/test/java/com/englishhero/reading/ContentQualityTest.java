package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import com.englishhero.dictionary.*;
import static org.assertj.core.api.Assertions.*;
class ContentQualityTest {
    @Test void designatedNounKeywordsHaveTheirContextualSenses()throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();var data=mapper.readValue(Files.readAllBytes(Path.of("../content/dictionary/entries.v1.json")),DictionaryDtos.Dataset.class);
        for(String lemma:List.of("review","repair"))assertThat(data.entries().stream().filter(e->e.lemma().equals(lemma)).findFirst().orElseThrow().senses().stream().map(DictionaryDtos.Sense::partOfSpeech)).as(lemma).contains("n.");
    }
    @Test void originalPracticeSetAndReviewedDictionaryMeetTheirDeclaredScope()throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();
        var payload=mapper.readValue(Files.readAllBytes(Path.of("../content/reading/original-b1-b2.v1.json")),ArticleImportDtos.ImportPayload.class);
        var data=mapper.readValue(Files.readAllBytes(Path.of("../content/dictionary/entries.v1.json")),DictionaryDtos.Dataset.class);
        DictionaryDataLoader.validate(data);assertThat(data.entries().size()).isGreaterThanOrEqualTo(300);
        var entries=new HashMap<String,String>();for(var entry:data.entries()){entries.put(entry.lemma(),entry.lemma());for(String form:entry.forms())entries.put(DictionaryRules.normalize(form),entry.lemma());}
        assertThat(payload.schemaVersion()).isEqualTo(1);assertThat(payload.articles()).hasSize(10);
        var counts=new HashMap<String,Integer>();var titles=new HashSet<String>();
        for(var article:payload.articles()){
            ArticleRules.validate(article);assertThat(titles.add(article.title())).isTrue();counts.merge(article.categoryId(),1,Integer::sum);
            assertThat(ArticleContent.wordCount(ArticleContent.normalize(article.body()))).as(article.title()).isBetween(350,650);
            assertThat(article.sourceType()).isEqualTo("ORIGINAL");assertThat(article.author()).isEqualTo("Lingua Loop Editorial Team");assertThat(article.sourceUrl()).isNull();
        }
        assertThat(counts).hasSize(5);assertThat(counts.values()).containsOnly(2);
        var manifest=mapper.readTree(Files.readAllBytes(Path.of("../content/reading/original-b1-b2.v1.manifest.json")));
        assertThat(manifest.get("articles").size()).isEqualTo(10);
        for(var item:manifest.get("articles")){
            var article=payload.articles().stream().filter(a->a.title().equals(item.get("title").asText())).findFirst().orElseThrow();
            for(var keyword:item.get("keywords")){
                String surface=keyword.get("surface").asText();assertThat(entries.get(DictionaryRules.normalize(surface))).isEqualTo(keyword.get("lemma").asText());
                assertThat(ArticleContent.WORD.matcher(article.body()).results().map(java.util.regex.MatchResult::group).anyMatch(word->word.equalsIgnoreCase(surface))).as(surface+" in "+article.title()).isTrue();
            }
        }
    }
}
