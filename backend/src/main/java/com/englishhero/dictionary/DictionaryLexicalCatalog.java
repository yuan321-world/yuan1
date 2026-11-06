package com.englishhero.dictionary;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.reading.ArticleContent;
import static com.englishhero.dictionary.DictionaryDtos.*;

/** Read-only public lexical context. Never contains account state or invents definitions. */
@Component
public class DictionaryLexicalCatalog {
    private record Entry(List<SynonymGroup> synonyms,List<Phrase> phrases){}
    private record Payload(int schemaVersion,String dataVersion,String synonymSource,String phraseSource,Map<String,Entry> entries){}
    private record ExamplePayload(int schemaVersion,String dataVersion,String sourceNote,String sourceSha256,Map<String,List<Example>> entries){}
    private final Map<String,List<Example>> examples;private final String exampleSource;
    private final Map<String,Entry> entries;private final boolean phrasesEnabled;
    private final Map<String,String> phonetics;private final NavigableMap<String,Phrase> phraseIndex;
    private final Map<String,Integer> phraseRanks;
    private record SearchPhrases(int schemaVersion,String dataVersion,String sourceSha256,List<Phrase> entries){}
    public DictionaryLexicalCatalog(ObjectMapper json,@Value("${english-hero.dictionary.ecdict.enabled:false}") boolean phrasesEnabled)throws java.io.IOException{
        this.phrasesEnabled=phrasesEnabled;
        try(var input=new ClassPathResource("dictionary/fixed-examples/entries.json").getInputStream()){
            var data=json.readValue(input,ExamplePayload.class);
            if(data.schemaVersion()!=1||data.entries()==null||data.entries().size()>11000||data.dataVersion()==null||data.sourceSha256()==null||!data.sourceSha256().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid fixed example dataset");
            exampleSource=ArticleContent.text(data.sourceNote(),"sourceNote",500,true);
            var fixed=new HashMap<String,List<Example>>();
            for(var item:data.entries().entrySet()){
                if(!DictionaryRules.normalize(item.getKey()).equals(item.getKey())||item.getValue()==null||item.getValue().isEmpty()||item.getValue().size()>2)throw new IllegalArgumentException("Invalid example bounds");
                var unique=new HashSet<String>();for(var example:item.getValue()){
                    if(example==null||!unique.add(example.text()))throw new IllegalArgumentException("Invalid example");
                    ArticleContent.text(example.text(),"text",300,true);ArticleContent.text(example.translationZh(),"translationZh",300,true);
                    if(!example.translationZh().matches("(?s).*[\\p{IsHan}].*"))throw new IllegalArgumentException("Missing Chinese example translation");
                }
                fixed.put(item.getKey(),List.copyOf(item.getValue()));
            }
            examples=Map.copyOf(fixed);
        }
        try(var input=new ClassPathResource("dictionary/lexical-extras/entries.json").getInputStream()){
            var data=json.readValue(input,Payload.class);
            if(data.schemaVersion()!=1||data.entries()==null||data.entries().size()>11000)throw new IllegalArgumentException("Invalid lexical dataset");
            var result=new HashMap<String,Entry>();
            for(var item:data.entries().entrySet()){
                String lemma=item.getKey();if(!DictionaryRules.normalize(lemma).equals(lemma))throw new IllegalArgumentException("Invalid lexical lemma");
                var entry=item.getValue();if(entry==null||entry.synonyms()==null||entry.phrases()==null||entry.synonyms().size()>16||entry.phrases().size()>5)throw new IllegalArgumentException("Invalid lexical bounds");
                for(var group:entry.synonyms()){
                    if(!Set.of("n.","v.","adj.","adv.").contains(group.partOfSpeech())||group.words().isEmpty()||group.words().size()>6)throw new IllegalArgumentException("Invalid synonym group");
                    ArticleContent.text(group.meaningEn(),"meaningEn",400,true);
                    for(String word:group.words()){ArticleContent.text(word,"synonym",64,true);if(word.equalsIgnoreCase(lemma))throw new IllegalArgumentException("Self synonym");}
                }
                for(var phrase:entry.phrases()){
                    ArticleContent.text(phrase.text(),"phrase",100,true);ArticleContent.text(phrase.translationZh(),"translationZh",250,true);
                    if(!Arrays.asList(phrase.text().toLowerCase(Locale.ROOT).split(" ")).contains(lemma)||!phrase.translationZh().matches("(?s).*[\\p{IsHan}].*"))throw new IllegalArgumentException("Invalid phrase scope");
                }
                result.put(lemma,new Entry(List.copyOf(entry.synonyms()),List.copyOf(entry.phrases())));
            }
            entries=Map.copyOf(result);
        }
        var phrases=new TreeMap<String,Phrase>();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item->item.getValue().phrases().forEach(p->phrases.putIfAbsent(p.text().toLowerCase(Locale.ROOT),p)));
        var ranks=new HashMap<String,Integer>();
        if(phrasesEnabled)try(var input=new ClassPathResource("dictionary/search-expanded/phrases.json").getInputStream()){
            var data=json.readValue(input,SearchPhrases.class);
            if(data.schemaVersion()!=1||!"ecdict-search-phrases-v1".equals(data.dataVersion())||data.entries()==null||data.entries().size()>31000||!"1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf".equals(data.sourceSha256()))throw new IllegalArgumentException("Invalid search phrase dataset");
            for(var phrase:data.entries()){
                String text=DictionaryRules.normalizeSearch(phrase.text());
                if(!text.equals(phrase.text())||!text.contains(" ")||phrase.translationZh()==null||phrase.translationZh().length()>250||!phrase.translationZh().matches("(?s).*[\\p{IsHan}].*"))throw new IllegalArgumentException("Invalid search phrase");
                phrases.put(text,phrase);
                ranks.put(text,ranks.size());
            }
        }
        phraseIndex=Collections.unmodifiableNavigableMap(phrases);
        phraseRanks=Map.copyOf(ranks);
        var pronunciations=new HashMap<String,String>();
        if(phrasesEnabled)try(var input=new ClassPathResource("dictionary/ecdict-local/entries.json").getInputStream()){
            var data=json.readValue(input,Dataset.class);EcdictDataLoader.validate(data);
            for(var entry:data.entries())if(entry.phonetic()!=null&&!entry.phonetic().isBlank())pronunciations.put(entry.lemma(),entry.phonetic());
        }
        phonetics=Map.copyOf(pronunciations);
    }
    public String phonetic(PublicEntry entry){return entry.phonetic()!=null&&!entry.phonetic().isBlank()?entry.phonetic():phonetics.get(entry.lemma());}
    public Optional<Phrase> findPhrase(String text){
        if(!phrasesEnabled||text==null||text.length()>2000)return Optional.empty();
        String normalized=text.trim().toLowerCase(Locale.ROOT).replace('’','\'').replaceAll("\\s+"," ");
        if(normalized.length()>100||!normalized.matches("[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,5}"))return Optional.empty();
        return Optional.ofNullable(phraseIndex.get(normalized));
    }
    public List<Phrase> searchPhrases(String prefix){return !phrasesEnabled?List.of():phraseIndex.subMap(prefix,true,prefix+'\uffff',true).values().stream().sorted(Comparator.comparingInt((Phrase p)->p.text().equals(prefix)?0:p.text().startsWith(prefix+" ")?1:2).thenComparingInt(p->phraseRanks.getOrDefault(p.text(),Integer.MAX_VALUE)).thenComparing(Phrase::text)).limit(10).toList();}
    public List<SynonymGroup> synonyms(PublicEntry entry){
        var lexical=entries.get(entry.lemma());if(lexical==null)return List.of();
        var parts=new HashSet<String>();
        for(var sense:entry.senses()){
            String pos=sense.partOfSpeech().toLowerCase(Locale.ROOT);
            if(has(pos,"n"))parts.add("n.");if(has(pos,"v|vt|vi"))parts.add("v.");if(has(pos,"a|adj"))parts.add("adj.");if(has(pos,"ad|adv"))parts.add("adv.");
        }
        return lexical.synonyms().stream().filter(group->parts.contains(group.partOfSpeech())).limit(6).toList();
    }
    private boolean has(String pos,String labels){return java.util.regex.Pattern.compile("(?:^|[^a-z])(?:"+labels+")(?:\\.|\\b)").matcher(pos).find();}
    public List<Phrase> phrases(String lemma){var lexical=entries.get(lemma);return !phrasesEnabled||lexical==null?List.of():lexical.phrases();}
    public List<Example> examples(String lemma){return examples.getOrDefault(lemma,List.of());}
    public String exampleSourceNote(){return exampleSource;}
    public String sourceNote(){return "近义词：WordNet 3.0，Copyright 2006 Princeton University（原许可保留）；"+(phrasesEnabled?"短语：ECDICT bc015ed，本地开发离线数据。":"短语数据未启用。");}
}
