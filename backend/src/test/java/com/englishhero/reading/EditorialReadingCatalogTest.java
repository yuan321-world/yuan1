package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
class EditorialReadingCatalogTest {
    @Test void allTenOriginalsHaveCompleteParagraphTranslationsAndPreparedSentences() throws Exception {
        var json=new ObjectMapper();var catalog=new EditorialReadingCatalog(json);
        try(var input=new ClassPathResource("reading/original-b1-b2.v1.json").getInputStream()){
            var originals=json.readTree(input).get("articles");assertThat(originals.size()).isEqualTo(10);
            int paragraphCount=0;
            for(var item:originals){
                var v=version(item.get("title").asText(),item.get("body").asText());var translated=catalog.translation(v);
                assertThat(translated.available()).as(v.title()).isTrue();
                assertThat(translated.source()).contains("待人工校对");
                assertThat(translated.paragraphs()).hasSize(v.content().paragraphs().size());
                for(int index=0;index<v.content().paragraphs().size();index++){
                    var paragraph=translated.paragraphs().get(index);assertThat(paragraph.key()).isEqualTo(v.content().paragraphs().get(index).key());
                    assertThat(paragraph.text()).isNotBlank().containsPattern("[\\p{IsHan}]");
                }
                assertThat(translated.sentences()).isNotEmpty().allSatisfy(sentence->{
                    assertThat(v.content().paragraphs().stream().flatMap(p->p.sentences().stream()).anyMatch(s->s.key().equals(sentence.key())&&s.text().equals(sentence.sourceText()))).isTrue();
                    assertThat(sentence.text()).isNotBlank().containsPattern("[\\p{IsHan}]");
                });
                assertThat(catalog.translation(version(v.title(),item.get("body").asText()+" Changed.")).available()).isFalse();
                assertThat(catalog.translation(version(v.title()+" changed",item.get("body").asText())).available()).isFalse();
                paragraphCount+=translated.paragraphs().size();
            }
            assertThat(paragraphCount).isEqualTo(60);
        }
    }
    @Test void exactOriginalTextBindsParagraphsButEditedTextNeverReusesTranslations() throws Exception {
        var json=new ObjectMapper();var catalog=new EditorialReadingCatalog(json);
        var article=json.readTree(new ClassPathResource("reading/original-b1-b2.v1.json").getInputStream()).get("articles").get(0);
        String title=article.get("title").asText(),body=article.get("body").asText();
        var version=version(title,body);
        assertThat(catalog.keywords(version)).containsExactly("habit","routine","review");
        assertThat(catalog.translation(version).available()).isTrue();
        assertThat(catalog.translation(version).paragraphs()).hasSize(version.content().paragraphs().size());
        assertThat(catalog.translation(version).sentences()).hasSize(4);
        assertThat(catalog.translation(version(title,body+" Changed.")).available()).isFalse();
        assertThat(catalog.keywords(version(title,body+" Changed."))).isEmpty();
        var originals=json.readTree(new ClassPathResource("reading/original-b1-b2.v1.json").getInputStream()).get("articles");
        for(int index:new int[]{8,9}){
            var item=originals.get(index);var published=version(item.get("title").asText(),item.get("body").asText());
            assertThat(catalog.translation(published).available()).isTrue();
            assertThat(catalog.translation(published).paragraphs()).hasSize(published.content().paragraphs().size());
            assertThat(catalog.translation(published).sentences()).hasSize(1);
        }
    }
    private ReadingDtos.ArticleVersionDto version(String title,String body){return new ReadingDtos.ArticleVersionDto("1","1",1,title,"","",new ReadingDtos.CategoryDto("1","STUDY","学习"),"B1",ArticleContent.normalize(body),391,2,"Lingua Loop","ORIGINAL",null,"原创");}
}
