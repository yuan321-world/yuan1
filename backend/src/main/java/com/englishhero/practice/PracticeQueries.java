package com.englishhero.practice;
import java.util.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;
final class PracticeQueries {
 private PracticeQueries(){}
 static Integer integer(Map<String,String> p,String key,Integer fallback){var value=p.get(key);if(value==null||value.isBlank())return fallback;try{return Integer.valueOf(value);}catch(NumberFormatException e){throw BusinessException.invalid(key,"请填写有效整数");}}
 static Query parse(Map<String,String> p,boolean admin){int page=integer(p,"page",1),size=integer(p,"pageSize",20);ArticleRules.page(page);ArticleRules.pageSize(size);String exam=p.get("exam"),state=p.get("state"),source=p.get("source"),status=p.get("status"),sort=p.getOrDefault("sort",admin?"NEWEST":"LATEST");
  allowed("exam",exam,Set.of("CET4","CET6","POSTGRAD1","POSTGRAD2"));allowed("state",state,Set.of("ALL","IN_PROGRESS","COMPLETED","NOT_STARTED"));allowed("source",source,Set.of("ORIGINAL","PAST"));allowed("status",status,Set.of("DRAFT","PUBLISHED","UNLISTED"));allowed("sort",sort,admin?Set.of("NEWEST","TITLE","COUNT"):Set.of("LATEST","TITLE"));
  String type=PracticeDraftRules.text(p.get("readingType"),"readingType",30),topic=PracticeDraftRules.text(p.get("topic"),"topic",30);allowed("section",p.get("section"),Set.of("reading","listening","writing","translation","all"));return new Query(exam,type,source,topic,state,status,integer(p,"year",null),integer(p,"month",null),integer(p,"paperNo",null),PracticeDraftRules.text(p.get("q"),"q",80),sort,page,size,p.get("section"));
 }
 static void allowed(String field,String value,Set<String> choices){if(value!=null&&!value.isBlank()&&!choices.contains(value))throw BusinessException.invalid(field,"请选择有效条件");}
}
