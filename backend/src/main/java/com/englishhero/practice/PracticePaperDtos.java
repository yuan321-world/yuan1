package com.englishhero.practice;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class PracticePaperDtos {
 private PracticePaperDtos(){}
 public record ComponentInput(String slot,String setVersionId){}
 public record AdminInput(Integer expectedLockVersion,String title,String examCode,Integer year,Integer month,String sourceNote,List<ComponentInput> components){}
 public record PaperQuery(String exam,Integer year,Integer month,int page,int pageSize){}
 public record LockInput(Integer expectedLockVersion){}
 public record StartInput(String paperVersionId,String requestId){}
 public record ResponseInput(String slot,String text,Map<String,String> choices){}
 public record DraftInput(Integer expectedLockVersion,String currentSlot,String currentQuestionId,List<ResponseInput> responses){}
 public record SubmitInput(String requestId,Integer expectedLockVersion,String currentSlot,String currentQuestionId,List<ResponseInput> responses,Boolean confirmUnanswered){}
 public record Candidate(String setId,String setVersionId,String title,String type,int questionCount,int revision,Integer year,Integer month,String sourceNote){}
 public record ComponentView(String slot,String setVersionId,String title,String type,int questionCount){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record PaperView(String id,String paperVersionId,String title,String examCode,Integer year,Integer month,String sourceNote,String status,int revision,int lockVersion,boolean sourceReviewed,List<ComponentInput> components,List<ComponentView> componentDetails,List<String> checks,boolean available,String unavailableReason,String currentAttemptId){}
 public record Facet(String value,long count){}
 public record Catalog(List<PaperView> items,int page,int pageSize,long total,List<Facet> years,List<Facet> months){}
 @JsonInclude(JsonInclude.Include.NON_NULL)
 public record ComponentAttempt(String slot,String type,String title,PracticeDtos.Attempt objective,PracticeSubjectiveDtos.Prompt prompt,String text,int wordCount,String reference,String notes){}
 public record Attempt(String id,String paperId,String paperVersionId,String title,String sourceNote,String status,int lockVersion,String currentSlot,String currentQuestionId,List<ResponseInput> responses,List<ComponentAttempt> components,Instant startedAt,Instant completedAt){}
 public record History(String id,String paperId,String title,String status,Instant startedAt,Instant completedAt){}
}
