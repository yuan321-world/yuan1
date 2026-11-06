package com.englishhero.dictionary;
import java.util.List;
public final class DictionaryDtos {
    private DictionaryDtos(){}
    public record Sense(String partOfSpeech,String definitionZh,String definitionEn){}
    public record PublicEntry(String entryId,String lemma,String phonetic,List<Sense> senses,String sourceNote){}
    public record CollectionState(String wordId,boolean active){}
    public record SynonymGroup(String partOfSpeech,String meaningEn,List<String> words){ public SynonymGroup{words=List.copyOf(words);} }
    public record Phrase(String text,String translationZh){}
    public record Example(String text,String translationZh){}
    public record Candidate(String entryId,String lemma,String phonetic,List<Sense> senses,String sourceNote,CollectionState collection,List<SynonymGroup> synonyms,List<Phrase> phrases,String lexicalSourceNote,String phoneticSourceNote,List<Example> examples,String exampleSourceNote){}
    public record PhraseLookupDto(boolean found,Phrase phrase,String sourceNote){}
    public record LookupDto(boolean found,List<Candidate> candidates){}
    public record DataEntry(String lemma,String phonetic,List<String> forms,List<Sense> senses){}
    public record Dataset(int schemaVersion,String dataVersion,String sourceNote,List<DataEntry> entries){}
}
