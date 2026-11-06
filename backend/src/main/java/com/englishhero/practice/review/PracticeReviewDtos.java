package com.englishhero.practice.review;
import java.time.Instant;import java.util.List;
public final class PracticeReviewDtos {
 private PracticeReviewDtos(){}
 public record CreateReview(String requestId,String sourceType,String attemptId,String slot,String consentVersion){}
 public record Source(String sourceType,String attemptId,String slot,String setVersionId,String prompt,String requirements,String text){}
 public record Dimension(String name,String level){}
 public record Annotation(String kind,String severity,String quote,Integer occurrence,String replacement,String reasonZh,String reasonEn,String sourceQuote){}
 public record Omission(String sourceQuote,String reasonZh,String reasonEn,String suggestion){}
 public record Result(Integer score,String summaryZh,String summaryEn,List<Dimension> dimensions,List<Annotation> annotations,List<Omission> omissions,String revisedText){}
 public record ReviewDto(String id,String sourceType,String attemptId,String slot,String status,String errorCode,Instant createdAt,Instant completedAt,String model,String promptVersion,String rubricVersion,String submittedText,Result result,String budgetStatus,long budgetAmountMicrocny){}
 public record ProviderResponse(Result result,String model,Long inputTokens,Long outputTokens){}
}
