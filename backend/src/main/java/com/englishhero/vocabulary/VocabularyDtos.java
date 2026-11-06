package com.englishhero.vocabulary;
import java.time.Instant;
import java.util.List;
import com.englishhero.dictionary.DictionaryDtos.Sense;
import com.englishhero.reading.ReadingDtos.PageDto;
public final class VocabularyDtos {
    private VocabularyDtos(){}
    public record CollectInput(String articleVersionId,String sentenceKey,Integer startOffset,Integer endOffset,String entryId){}
    public record LearningCollectionInput(String sessionId,String entryId){}
    public record DictionaryCollectionInput(String word,String entryId){}
    public record LearningState(String id,boolean personalEnrolled,boolean active,int stage,Instant dueAt,boolean proficient){}
    public record CollectionOrigin(String id,String kind,String title,String sessionId,Instant createdAt){}
    public record WordDto(String id,String entryId,String lemma,String phonetic,List<Sense> senses,String sourceNote,boolean active,Instant createdAt,Instant updatedAt,LearningState learning){
        public WordDto(String id,String entryId,String lemma,String phonetic,List<Sense> senses,String sourceNote,boolean active,Instant createdAt,Instant updatedAt){this(id,entryId,lemma,phonetic,senses,sourceNote,active,createdAt,updatedAt,null);}
    }
    public record SourceDto(String id,String articleId,String articleVersionId,String title,String sentenceKey,int startOffset,int endOffset,String surfaceForm,String sentence,Instant createdAt){}
    public record CollectResult(WordDto word,boolean created){}
    public record WordDetail(WordDto word,PageDto<SourceDto> sources,List<CollectionOrigin> origins,WordDifficultyService.State difficulty){
        public WordDetail(WordDto word,PageDto<SourceDto> sources){this(word,sources,List.of(),null);}
    }
}
