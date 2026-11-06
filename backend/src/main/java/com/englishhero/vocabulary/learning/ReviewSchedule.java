package com.englishhero.vocabulary.learning;
import java.time.*;
public final class ReviewSchedule {
 private ReviewSchedule(){}
 public record Result(int stage,Instant dueAt,String ruleVersion){}
 public static Result next(int stage,boolean hadFailure,Instant now){
  return next(stage,hadFailure,now,"fixed-v1-binary");
 }
 public static Result next(int stage,boolean hadFailure,Instant now,String rule){
  if(stage<0||stage>7)throw new IllegalArgumentException("Invalid review stage");
  boolean v2="fixed-v2-seven".equals(rule);
  int next=hadFailure?1:Math.min(stage+1,v2?7:Math.max(5,stage));int days=(v2||next>5?new int[]{1,3,7,15,30,60,90}:new int[]{1,3,7,14,30})[next-1];
  var zone=ZoneId.of("Asia/Shanghai");
  var due=now.atZone(zone).toLocalDate().plusDays(days).atTime(5,0).atZone(zone).toInstant();
  return new Result(next,due,rule);
 }
}
