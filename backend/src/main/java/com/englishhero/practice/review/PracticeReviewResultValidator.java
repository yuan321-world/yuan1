package com.englishhero.practice.review;
import java.util.*;import org.springframework.stereotype.Component;import tools.jackson.databind.ObjectMapper;
import static com.englishhero.practice.review.PracticeReviewDtos.*;
@Component
public class PracticeReviewResultValidator {
 private final ObjectMapper json;public PracticeReviewResultValidator(ObjectMapper json){this.json=json;}
 public Result validate(Source source,String content){
  Result r=validate(source.text(),source.prompt(),content);if(!"TRANSLATION".equals(source.slot()))return r;
  String summaries=r.summaryZh()+" "+r.summaryEn();
  if(java.util.regex.Pattern.compile("[一二三四五六七八九十百零〇两0-9]+\\s*(?:个\\s*)?(?:中文|原文|源文|英文|完整)?\\s*句(?:子)?|\\b(?:[0-9]+|one|two|three|four|five|six|seven|eight|nine|ten|first|second|third|fourth|fifth)\\s+(?:(?:source|Chinese|English|original|translated)\\s+)*sentences?\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(summaries).find()){
   long corrections=r.annotations().stream().filter(a->"ERROR".equals(a.severity())).count();int missing=r.omissions().size();
   r=new Result(r.score(),"本次反馈列出 "+corrections+" 项需修正表达、"+missing+" 项待补充信息，请结合下方原文定位核对。","Corrections listed: "+corrections+"; missing-information items listed: "+missing+". Check the source-linked details below.",r.dimensions(),r.annotations(),r.omissions(),r.revisedText());
  }
  for(var a:r.annotations()){
   if(!Set.of("grammar","lexical","meaning","style","structure").contains(a.kind()))throw new IllegalArgumentException("Unknown translation annotation kind");
   if("meaning".equals(a.kind())){
    required(a.sourceQuote(),2000);
    for(var o:r.omissions())if(o.sourceQuote().contains(a.sourceQuote())||a.sourceQuote().contains(o.sourceQuote()))throw new IllegalArgumentException("Meaning correction duplicated as omission");
   }
  }
  return r;
 }
 public Result validate(String text,String prompt,String content){
  try{var tree=json.readTree(content);if(!tree.path("score").isIntegralNumber())throw new IllegalArgumentException();var r=json.treeToValue(tree,Result.class);
   if(r.score()==null||r.score()<0||r.score()>15||r.dimensions()==null||r.dimensions().size()!=3||r.annotations()==null||r.annotations().size()>20||r.omissions()==null||r.omissions().size()>10)throw new IllegalArgumentException();
   required(r.summaryZh(),2000);required(r.summaryEn(),2000);required(r.revisedText(),20000);
   for(var d:r.dimensions()){required(d.name(),40);if(!Set.of("GOOD","FAIR","IMPROVE").contains(Objects.toString(d.level(),"")))throw new IllegalArgumentException();}
   for(var a:r.annotations()){required(a.kind(),40);if(!Set.of("ERROR","SUGGESTION").contains(Objects.toString(a.severity(),"")))throw new IllegalArgumentException();required(a.quote(),2000);required(a.replacement(),2000);required(a.reasonZh(),1000);required(a.reasonEn(),1000);if(a.occurrence()==null||a.occurrence()<1||a.occurrence()>20000)throw new IllegalArgumentException();int start=-1;for(int i=0;i<a.occurrence();i++){start=text.indexOf(a.quote(),start+1);if(start<0)throw new IllegalArgumentException();}if(a.sourceQuote()!=null&&!a.sourceQuote().isBlank()&&!prompt.contains(a.sourceQuote()))throw new IllegalArgumentException();}
   for(var o:r.omissions()){required(o.sourceQuote(),2000);if(!prompt.contains(o.sourceQuote()))throw new IllegalArgumentException();required(o.reasonZh(),1000);required(o.reasonEn(),1000);required(o.suggestion(),2000);}return r;
  }catch(RuntimeException e){throw new IllegalArgumentException("Invalid review output");}
 }
 private void required(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw new IllegalArgumentException();}
}
