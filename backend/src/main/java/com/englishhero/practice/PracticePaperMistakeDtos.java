package com.englishhero.practice;
public final class PracticePaperMistakeDtos {
 private PracticePaperMistakeDtos(){}
 public record RetryInput(String requestId,String slot){}
 public record Query(String exam,Integer year,Integer month,String readingType,String status,String sourceAttemptId,int page,int pageSize){}
 public record SourceKey(long version,long question){}
 public record PaperMistake(String questionId,String setId,String setVersionId,String sourceAttemptId,String slot,int originalNumber,String paperTitle,String readingType,String status,int wrongCount,String stem,String submittedChoice,String correctOption,String explanation){}
}
