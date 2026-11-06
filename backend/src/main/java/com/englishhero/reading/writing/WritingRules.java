package com.englishhero.reading.writing;
import com.englishhero.reading.ArticleContent;
import com.englishhero.web.BusinessException;
import java.util.List;
import static com.englishhero.reading.writing.WritingDtos.*;
public final class WritingRules {
 private WritingRules(){}
 public static Content content(Input i){
  if(i==null)throw BusinessException.invalid("expression","请填写素材");
  if(i.sortOrder()==null||i.sortOrder()<0||i.sortOrder()>9999)throw BusinessException.invalid("sortOrder","排序须为0—9999的整数");
  return new Content(text(i.expression(),"expression",300),text(i.meaningZh(),"meaningZh",600),items(i.keyPhrases(),"keyPhrases"),text(i.structure(),"structure",1000),text(i.exampleEn(),"exampleEn",2000),text(i.exampleZh(),"exampleZh",2000),items(i.scenarios(),"scenarios"),i.sortOrder());
 }
 private static String text(String s,String field,int max){return ArticleContent.text(s,field,max,true);}
 private static List<String> items(List<String> values,String field){if(values==null||values.isEmpty()||values.size()>8)throw BusinessException.invalid(field,"请填写1—8项，每项最多120字符");return values.stream().map(s->text(s,field,120)).distinct().toList();}
}
