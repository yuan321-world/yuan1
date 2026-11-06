package com.englishhero.dictionary;

import java.util.*;
import com.englishhero.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionarySearchService {
    public record Item(String entryId, String lemma, String partOfSpeech, String definitionZh,String kind,String canonicalLemma,List<String> relatedEntryIds) {}
    public record Result(List<Item> items) {}
    private final DictionaryMapper dictionary;
    private final IdentityService identities;
    private final DictionaryLexicalCatalog lexical;

    public DictionarySearchService(DictionaryMapper dictionary, IdentityService identities,DictionaryLexicalCatalog lexical) {
        this.dictionary = dictionary;
        this.identities = identities;
        this.lexical=lexical;
    }

    @Transactional(readOnly = true)
    public Result search(SessionPrincipal actor, String query) {
        if (actor != null) identities.current(actor);
        String normalized = DictionaryRules.normalizeSearch(query);
        var found=new LinkedHashMap<String,Item>();
        var words=normalized.contains(" ")?List.<DictionaryDtos.PublicEntry>of():dictionary.search(normalized).stream().filter(e->e.lemma().startsWith(normalized)).toList();
        words.stream().filter(e->e.lemma().equals(normalized)).forEach(e->found.put(e.lemma(),word(e,e.lemma(),"WORD")));
        if(!normalized.contains(" "))for(var form:dictionary.searchForms(normalized))found.putIfAbsent(form.text(),word(form.entry(),form.text(),"FORM"));
        words.stream().limit(6).forEach(e->found.putIfAbsent(e.lemma(),word(e,e.lemma(),"WORD")));
        var phrases=lexical.searchPhrases(normalized);
        // Reserve room for phrases without letting variants hide the exact headword.
        var result=new ArrayList<Item>(found.values().stream().limit(phrases.isEmpty()?10:6).toList());
        for(var phrase:phrases)if(result.size()<10)result.add(new Item("phrase:"+phrase.text(),phrase.text(),"",phrase.translationZh(),"PHRASE",phrase.text(),dictionary.relatedIds(phrase.text())));
        return new Result(List.copyOf(result));
    }
    private Item word(DictionaryDtos.PublicEntry entry,String surface,String kind){
        var sense=entry.senses().stream().filter(s->s.definitionZh()!=null&&!s.definitionZh().isBlank()).findFirst();
        return new Item(entry.entryId(),surface,sense.map(DictionaryDtos.Sense::partOfSpeech).orElse(""),sense.map(DictionaryDtos.Sense::definitionZh).orElse(""),kind,entry.lemma(),List.of(entry.entryId()));
    }
}
