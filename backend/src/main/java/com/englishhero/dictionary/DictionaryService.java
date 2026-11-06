package com.englishhero.dictionary;
import com.englishhero.identity.*;
import com.englishhero.web.BusinessException;
import org.springframework.stereotype.Service;
import static com.englishhero.dictionary.DictionaryDtos.*;
@Service
public class DictionaryService {
    private final DictionaryProvider provider;private final IdentityService identities;private final CollectionStateProvider collections;private final DictionaryLexicalCatalog lexical;
    public DictionaryService(DictionaryProvider provider,IdentityService identities,CollectionStateProvider collections,DictionaryLexicalCatalog lexical){this.provider=provider;this.identities=identities;this.collections=collections;this.lexical=lexical;}
    public LookupDto lookup(SessionPrincipal actor,String word){
        if(actor!=null)identities.current(actor);String normalized=DictionaryRules.normalize(word);
        java.util.List<PublicEntry> entries;
        try{entries=provider.lookup(normalized);}catch(RuntimeException failure){throw new BusinessException(503,"DICTIONARY_UNAVAILABLE","词典暂时不可用，请稍后重试");}
        java.util.Map<String,CollectionState> states=actor==null?java.util.Map.of():collections.states(actor,entries.stream().map(PublicEntry::entryId).toList());
        return new LookupDto(!entries.isEmpty(),entries.stream().map(e->new Candidate(e.entryId(),e.lemma(),lexical.phonetic(e),e.senses(),e.sourceNote(),states.get(e.entryId()),lexical.synonyms(e),lexical.phrases(e.lemma()),lexical.sourceNote(),(e.phonetic()==null||e.phonetic().isBlank())&&lexical.phonetic(e)!=null?"音标补充来源：ECDICT离线词条，原释义来源说明中的音标缺失描述不适用于此补充。":null,lexical.examples(e.lemma()),lexical.exampleSourceNote())).toList());
    }
    public PhraseLookupDto phrase(SessionPrincipal actor,String text){
        if(actor!=null)identities.current(actor);
        if(text==null||text.isBlank()||text.length()>2000)throw BusinessException.invalid("text","请选择1—2000个字符的英文内容");
        var found=lexical.findPhrase(text);
        return new PhraseLookupDto(found.isPresent(),found.orElse(null),"本地离线短语释义（ECDICT及原创补充）；未调用实时翻译服务");
    }
}
