package com.englishhero.reading.translation;
import java.time.*;
public final class SelectionTranslationDtos {
 private SelectionTranslationDtos(){}
 public record Input(String requestId,String articleId,String versionId,String text,String mode,String practiceAttemptId,String paperAttemptId,String slot){public Input(String requestId,String articleId,String versionId,String text){this(requestId,articleId,versionId,text,null,null,null,null);}public Input(String requestId,String articleId,String versionId,String text,String mode){this(requestId,articleId,versionId,text,mode,null,null,null);}}
 public record Reference(String text,String source){}
 public record Task(String id,String text,String status,String translation,String model,String errorCode,String budgetStatus,long estimatedCostMicrocny,String mode,tools.jackson.databind.JsonNode analysis,int sourceCharacters){}
 public record Job(long id,long user,String text,String lease,LocalDate day,long reserve,Instant created,Instant leaseUntil,String status,String mode,String model,int characters,LocalDate quotaMonth){}
}
