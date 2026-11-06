package com.englishhero.dictionary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Additive, versioned shards; the base dictionary and account records remain untouched. */
@Component @Order(22)
public class DictionarySearchDataLoader implements ApplicationRunner {
    private final EcdictDataLoader loader;
    private final boolean enabled;
    public DictionarySearchDataLoader(EcdictDataLoader loader,@Value("${english-hero.dictionary.ecdict.enabled:false}") boolean enabled){this.loader=loader;this.enabled=enabled;}
    public void run(ApplicationArguments args)throws Exception{
        if(!enabled)return;
        for(int i=0;i<5;i++)try(var in=new ClassPathResource("dictionary/search-expanded/words-"+i+".json").getInputStream()){
            loader.load(in.readAllBytes(),"dictionary_ecdict_search_"+i);
        }
    }
}
