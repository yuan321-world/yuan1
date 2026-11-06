package com.englishhero.reading;

import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Project-authored material. Exact normalized text binding prevents stale version matches. */
@Component
public class EditorialReadingCatalog {
    private record ReferenceSentence(String sourceText,String text){}
    private record Material(ArticleContent.Document content,List<String> keywords,List<String> translations,List<ReferenceSentence> sentences){}
    private final Map<String,Material> materials=new HashMap<>();
    public record ParagraphTranslation(String key,String text){}
    public record SentenceTranslation(String key,String sourceText,String text){}
    public record Translation(boolean available,String source,List<ParagraphTranslation> paragraphs,List<SentenceTranslation> sentences){}
    public EditorialReadingCatalog(ObjectMapper json) throws java.io.IOException {
        var bodies=read(json,"reading/original-b1-b2.v1.json").get("articles");
        var manifest=read(json,"reading/original-b1-b2.v1.manifest.json").get("articles");
        var translated=read(json,"reading/editorial-translations.v1.json").get("articles");
        for(var body:bodies){
            String title=body.get("title").asText();var document=ArticleContent.normalize(body.get("body").asText());var words=new ArrayList<String>();var texts=new ArrayList<String>();var sentences=new ArrayList<ReferenceSentence>();
            for(var entry:manifest)if(entry.get("title").asText().equals(title))for(var word:entry.get("keywords"))words.add(word.get("lemma").asText());
            for(var entry:translated)if(entry.get("title").asText().equals(title)&&ArticleContent.normalize(entry.get("body").asText()).equals(document)){
                for(var text:entry.get("paragraphs"))texts.add(text.asText());
                if(entry.has("sentences"))for(var sentence:entry.get("sentences"))sentences.add(new ReferenceSentence(sentence.get("sourceText").asText(),sentence.get("text").asText()));
            }
            if(texts.size()!=document.paragraphs().size())texts.clear();
            materials.put(title,new Material(document,List.copyOf(words),List.copyOf(texts),List.copyOf(sentences)));
        }
    }
    private static tools.jackson.databind.JsonNode read(ObjectMapper json,String path)throws java.io.IOException{try(var input=new ClassPathResource(path).getInputStream()){return json.readTree(input);}}
    private Material matching(ReadingDtos.ArticleVersionDto v){var material=materials.get(v.title());return material!=null&&material.content().equals(v.content())?material:null;}
    public List<String> keywords(ReadingDtos.ArticleVersionDto v){var material=matching(v);return material==null?List.of():material.keywords();}
    public Translation translation(ReadingDtos.ArticleVersionDto v){
        var material=matching(v);if(material==null||material.translations().isEmpty())return new Translation(false,"",List.of(),List.of());
        var result=new ArrayList<ParagraphTranslation>();for(int i=0;i<material.translations().size();i++)result.add(new ParagraphTranslation(v.content().paragraphs().get(i).key(),material.translations().get(i)));
        var sentences=new ArrayList<SentenceTranslation>();
        for(var paragraph:v.content().paragraphs())for(var sentence:paragraph.sentences())for(var reference:material.sentences())if(reference.sourceText().equals(sentence.text()))sentences.add(new SentenceTranslation(sentence.key(),sentence.text(),reference.text()));
        return new Translation(true,"项目编辑参考译文（待人工校对）",List.copyOf(result),List.copyOf(sentences));
    }
}
