package com.englishhero.practice;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.englishhero.practice.PracticeDtos.Metadata;

public final class PracticeSubjectiveDtos {
 private PracticeSubjectiveDtos(){}
 public record Content(String prompt,String requirements,Integer minWords,Integer maxWords,Integer minutes,String reference,String notes) {
  public Prompt publicPrompt(){return new Prompt(prompt,requirements,minWords,maxWords,minutes);}
 }
 public record Prompt(String prompt,String requirements,Integer minWords,Integer maxWords,Integer minutes){}
 public record AdminInput(String readingType,Metadata metadata,Content subjective,Integer expectedLockVersion){}
 public record DraftInput(Integer expectedVersion,String answerText){}
 public record SubmitInput(Integer expectedVersion,String requestId,Boolean confirmWordRange){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record Attempt(String id,String setId,String setVersionId,String parentAttemptId,String title,String readingType,
   Prompt prompt,String answerText,int wordCount,String status,int version,Instant startedAt,Instant updatedAt,Instant completedAt,String reference,String notes){}
 public record History(String id,String setId,String title,String readingType,String status,int wordCount,Instant startedAt,Instant completedAt){}
}
