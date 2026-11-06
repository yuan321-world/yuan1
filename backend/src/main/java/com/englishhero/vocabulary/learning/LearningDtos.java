package com.englishhero.vocabulary.learning;
import java.time.Instant;
import java.util.List;
import com.englishhero.dictionary.DictionaryDtos.PublicEntry;
import com.englishhero.vocabulary.VocabularyDtos.SourceDto;
public final class LearningDtos {
 private LearningDtos(){}
 public record JoinInput(List<String> wordIds){}
 public record ActiveInput(Boolean active){}
 public record Settings(Integer newGoal,Integer reviewGoal,Boolean autoNext,String reviewScope,Integer batchSize){
  public Settings(Integer n,Integer r){this(n,r,true,"ALL",20);}
  public Settings(Integer n,Integer r,Boolean a,String s){this(n,r,a,s,20);}
 }
 public record ProficiencyInput(Boolean proficient){}
 public record Command(String requestId,Integer expectedVersion,String itemId,String action,String optionId,String spelling,Boolean hadSpellingError,String kind,String mode,String status,String flowVersion,Integer attemptId,Boolean assisted){public Command(String r,Integer v,String i,String a,String o,String s,Boolean e,String k,String m,String t){this(r,v,i,a,o,s,e,k,m,t,null,null,null);}}
 public record Option(String id,String text){}
 public record Snapshot(PublicEntry entry,SourceDto source,List<Option> options,String correctOptionId,com.englishhero.dictionary.DictionaryDtos.Candidate detail){public Snapshot(PublicEntry e,SourceDto s,List<Option> o,String c){this(e,s,o,c,null);}}
 public record Outcome(boolean correct,String action,String selectedText,Integer stage,Instant dueAt){}
 public record QueueItem(String id,String meaning,boolean current,int failures){}
 public record Current(String id,String lemma,String phonetic,String partOfSpeech,String meaning,String extra,List<Option> options,String spellingTarget,PublicEntry definition,SourceDto source,Outcome outcome,String entryId,String learningWordId,Integer attemptId,Boolean assisted,Boolean proficient,com.englishhero.dictionary.DictionaryDtos.Candidate detail,Integer hintCount,String hintPrefix){public Current{attemptId=attemptId==null?0:attemptId;assisted=Boolean.TRUE.equals(assisted);proficient=Boolean.TRUE.equals(proficient);hintCount=hintCount==null?0:hintCount;hintPrefix=hintPrefix==null?"":hintPrefix;}public Current(String id,String l,String p,String pos,String m,String e,List<Option> o,String t,PublicEntry d,SourceDto s,Outcome out){this(id,l,p,pos,m,e,o,t,d,s,out,d==null?null:d.entryId(),null,0,false,false,null,0,"");}}
 public record ResultItem(String lemma,int failures,Outcome outcome,com.englishhero.dictionary.DictionaryDtos.Candidate definition){public ResultItem(String l,int f,Outcome o){this(l,f,o,null);}}
 public record SessionDto(String id,int version,String kind,String mode,String status,String phase,Current current,List<QueueItem> queue,int passed,int total,List<ResultItem> results,String flowVersion,String reviewScope){public SessionDto{flowVersion=flowVersion==null?"v1":flowVersion;reviewScope=reviewScope==null?"ALL":reviewScope;}public SessionDto(String id,int version,String kind,String mode,String status,String phase,Current current,List<QueueItem> queue,int passed,int total,List<ResultItem> results){this(id,version,kind,mode,status,phase,current,queue,passed,total,results,"v1","ALL");}}
 public record LearningWord(String id,String entryId,String lemma,String meaning,boolean active,int stage,Instant dueAt,boolean proficient){public LearningWord(String id,String e,String l,String m,boolean a,int s,Instant d){this(id,e,l,m,a,s,d,false);}}
 public record Candidate(String wordId,String lemma,String meaning,boolean joined,boolean paused,String unavailableReason){}
 public record SavedRound(String id,String kind,String mode,String status,String phase,int passed,int total,String sourceTitle,String sourceKey){public SavedRound(String id,String kind,String mode,String status,String phase,int passed,int total,String sourceTitle){this(id,kind,mode,status,phase,passed,total,sourceTitle,null);}}
 public record Overview(int candidates,int unlearned,int due,Instant nextDueAt,int newToday,int reviewToday,Settings settings,String sessionId,WordbookDtos.Source source,List<SavedRound> savedSessions,int reservedNew){}
}
