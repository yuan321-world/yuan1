package com.englishhero.vocabulary.phrase;
import java.time.*;
/** Phrase-only schedule; legacy saved items without a rule use its first released schedule. */
public final class PhraseSchedule {
 public static final String RULE="phrase-v1-seven";
 private static final int[] DAYS={1,3,7,15,30,60,90};
 private PhraseSchedule(){}
 public record Result(int stage,Instant dueAt,String ruleVersion){}
 public static Result next(int stage,boolean failure,Instant now,String rule){
  if(stage<0||stage>7)throw new IllegalArgumentException("Invalid phrase stage");
  if(rule!=null&&!RULE.equals(rule))throw new IllegalArgumentException("Unknown phrase schedule");
  int next=failure?1:Math.min(7,stage+1);
  return new Result(next,now.atZone(ZoneId.of("Asia/Shanghai")).plusDays(DAYS[next-1]).toInstant(),RULE);
 }
}
