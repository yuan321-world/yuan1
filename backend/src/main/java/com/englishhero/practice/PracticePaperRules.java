package com.englishhero.practice;

import java.util.*;
import com.englishhero.web.BusinessException;

public final class PracticePaperRules {
 private PracticePaperRules(){}
 public static final List<String> SLOTS=List.of("WRITING","LISTENING","CLOZE","MATCH","DETAILED_1","DETAILED_2","TRANSLATION");
 public record Component(String slot,String setVersionId,String exam,String source,Integer year,Integer month,String type,int count){}
 public static String type(String slot){return slot.startsWith("DETAILED_")?"DETAILED":slot;}
 public static int count(String slot){return switch(slot){case "LISTENING"->25;case "CLOZE","MATCH"->10;case "DETAILED_1","DETAILED_2"->5;default->1;};}
 public static List<Component> validateComponents(String exam,Integer year,Integer month,List<Component> values){
  if(!"CET4".equals(exam)||year==null||year<2000||year>2100||month==null||month<1||month>12)throw BusinessException.invalid("examCode","仅支持有效年月的四级真题整卷");
  if(values==null||values.size()!=7)throw BusinessException.invalid("components","须选择七个组成部分");
  var slots=new HashSet<String>();var ids=new HashSet<String>();
  for(var v:values)if(v==null||v.slot()==null||!SLOTS.contains(v.slot())||!slots.add(v.slot())||v.setVersionId()==null||!ids.add(v.setVersionId())||!type(v.slot()).equals(v.type())||count(v.slot())!=v.count()||!exam.equals(v.exam())||!"PAST".equals(v.source())||!year.equals(v.year())||!month.equals(v.month()))throw BusinessException.invalid("components","组成须为同考试年月的已发布真题版本，类型、题数及槽位不能重复或缺失");
  return SLOTS.stream().map(s->values.stream().filter(v->s.equals(v.slot())).findFirst().orElseThrow()).toList();
 }
 public static Map<String,String> choices(String type,Map<String,String> values,List<String> ids,List<String> paragraphs){
  if(values==null||values.size()>ids.size())throw BusinessException.invalid("choices","请选择当前组成的答案");var result=new TreeMap<String,String>();
  for(var e:values.entrySet()){
   if(!ids.contains(e.getKey()))throw BusinessException.invalid("choices","题目不属于当前组成");String v=e.getValue();if(v==null||v.isEmpty())continue;
   if(!v.matches("CLOZE".equals(type)?"[A-O]":"MATCH".equals(type)?"[A-Z]":"[A-D]")||"MATCH".equals(type)&&!paragraphs.contains(v))throw BusinessException.invalid("choices","选项不属于当前题目");result.put(e.getKey(),v);
  }
  if("CLOZE".equals(type))PracticeClozeRules.validateUniqueChoices(result.values());return result;
 }
 public static String title(String s){if(s==null||s.isBlank()||s.strip().length()>180)throw BusinessException.invalid("title","标题须为1–180字");return s.strip();}
 public static String source(String s){if(s==null||s.isBlank()||s.strip().length()>2000)throw BusinessException.invalid("sourceNote","请填写不超过2000字的组合来源说明");return s.strip();}
}
