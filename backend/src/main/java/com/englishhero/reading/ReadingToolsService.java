package com.englishhero.reading;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.dictionary.*;
import com.englishhero.identity.SessionPrincipal;
import static com.englishhero.reading.ReadingDtos.*;

@Service
public class ReadingToolsService {
    public record Keyword(String entryId,String lemma,String phonetic,List<DictionaryDtos.Sense> senses,String sourceNote,List<String> forms){}
    public record Tools(List<ArticleSummaryDto> related,ArticleSummaryDto previous,ArticleSummaryDto next,List<Keyword> keywords,EditorialReadingCatalog.Translation translation){}
    private final ReadingService reading;private final ReadingMapper mapper;private final ArticleMapper articles;private final EditorialReadingCatalog catalog;private final DictionaryMapper dictionary;
    public ReadingToolsService(ReadingService reading,ReadingMapper mapper,ArticleMapper articles,EditorialReadingCatalog catalog,DictionaryMapper dictionary){this.reading=reading;this.mapper=mapper;this.articles=articles;this.catalog=catalog;this.dictionary=dictionary;}
    @Transactional(readOnly=true)
    public Tools tools(SessionPrincipal actor,String articleId,String versionId){
        var version=reading.version(actor,articleId,versionId).version();long id=Long.parseLong(articleId);
        var keywords=new ArrayList<Keyword>();
        for(String lemma:catalog.keywords(version))for(var entry:dictionary.lookup(lemma))if(entry.lemma().equals(lemma)){
            var forms=new TreeSet<>(dictionary.forms(entry.entryId()));forms.add(entry.lemma());keywords.add(new Keyword(entry.entryId(),lemma,entry.phonetic(),entry.senses(),entry.sourceNote(),List.copyOf(forms)));break;
        }
        return new Tools(mapper.relatedIds(id,Long.parseLong(version.category().id())).stream().map(n->summary(actor,n)).toList(),summary(actor,mapper.neighborId(id,true)),summary(actor,mapper.neighborId(id,false)),List.copyOf(keywords),catalog.translation(version));
    }
    private ArticleSummaryDto summary(SessionPrincipal actor,Long id){return id==null?null:reading.summary(actor,articles.article(id,false));}
}
