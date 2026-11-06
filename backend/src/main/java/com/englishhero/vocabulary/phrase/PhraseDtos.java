package com.englishhero.vocabulary.phrase;
import java.util.List;
import java.time.Instant;
public final class PhraseDtos {
 private PhraseDtos(){}
 public record SourceInput(String articleVersionId,String sentenceKey,Integer startOffset,Integer endOffset,String articleId,String practiceAttemptId,String paperAttemptId,String slot){}
 public record Collect(String text,String meaningZh,String note,SourceInput source){}
 public record Edit(String meaningZh,String note){}
 public record Learning(Boolean enrolled,Boolean exempt){}
 public record Source(String id,String articleId,String articleVersionId,String title,String sentenceKey,int startOffset,int endOffset,String sentence,String practiceAttemptId,String paperAttemptId,String slot){}
 public record Phrase(String id,String text,String meaningZh,String note,boolean active,boolean enrolled,boolean exempt,int independentPasses,List<Source> sources,int stage,Instant dueAt,String ruleVersion){}
 public record Item(String phraseId,String text,String meaningZh,boolean passed,int failures,boolean assisted,boolean revealed,String draft,boolean answered,String ruleVersion){}
 public record Round(String id,int version,String mode,String status,List<Item> items,List<String> queue){}
 public record Start(String requestId,String mode,Integer limit){}
 public record Command(String requestId,Integer expectedVersion,String action,String answer,Boolean known,String draft){}
 public record Stats(long collected,long enrolled,long independentPasses,long available){}
 public record Overview(List<Phrase> items,Stats stats,int page,int pageSize,long total){}
}
