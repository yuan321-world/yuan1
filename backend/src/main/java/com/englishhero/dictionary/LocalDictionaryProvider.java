package com.englishhero.dictionary;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.englishhero.dictionary.DictionaryDtos.*;
@Service
public class LocalDictionaryProvider implements DictionaryProvider {
    private record Cached(List<PublicEntry> entries,Instant expires){}
    private final DictionaryMapper mapper;private final Clock clock;
    private final LinkedHashMap<String,Cached> cache=new LinkedHashMap<>(16,.75f,true);
    private String manifest;
    public LocalDictionaryProvider(DictionaryMapper mapper,Clock clock){this.mapper=mapper;this.clock=clock;}
    public synchronized List<PublicEntry> lookup(String word){
        String current=mapper.manifest();if(!Objects.equals(manifest,current)){cache.clear();manifest=current;}
        var now=clock.instant();var saved=cache.get(word);if(saved!=null&&now.isBefore(saved.expires()))return saved.entries();
        var entries=List.copyOf(mapper.lookup(word));
        if(entries.isEmpty())entries=mapper.lemmas(MorphologyResolver.candidates(word)).stream().filter(entry->MorphologyResolver.matches(word,entry)).toList();
        cache.put(word,new Cached(entries,now.plusSeconds(entries.isEmpty()?300:86400)));
        while(cache.size()>512)cache.remove(cache.keySet().iterator().next());return entries;
    }
}
