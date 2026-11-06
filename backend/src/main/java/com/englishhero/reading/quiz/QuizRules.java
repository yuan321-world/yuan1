package com.englishhero.reading.quiz;

import java.util.*;
import com.englishhero.reading.ArticleContent;
import com.englishhero.web.BusinessException;
import static com.englishhero.reading.quiz.QuizDtos.*;
public final class QuizRules {
 private QuizRules(){}
 public static List<QuestionInput> questions(Input input){
  if(input==null||input.questions()==null||input.questions().isEmpty()||input.questions().size()>10)throw BusinessException.invalid("questions","请录入1—10道题目");
  var keys=new HashSet<String>();var result=new ArrayList<QuestionInput>();int index=0;
  for(var q:input.questions()){
   String field="questions["+index+++"]";
   if(q==null||q.key()==null||!q.key().matches("[A-Za-z0-9_-]{1,64}")||!keys.add(q.key()))throw BusinessException.invalid("questions","题目标识须唯一且有效");
   if(!Set.of("MAIN_IDEA","DETAIL","INFERENCE","VOCABULARY","ATTITUDE").contains(q.type()==null?"":q.type()))throw BusinessException.invalid(field+".type","请选择题型");
   if(q.options()==null||q.options().size()!=4)throw BusinessException.invalid(field+".options","请填写四个不同的选项");
   var options=q.options().stream().map(s->ArticleContent.text(s,field+".options",1000,true)).toList();
   if(options.stream().distinct().count()!=4)throw BusinessException.invalid(field+".options","四个选项不能重复");
   if(q.correctOption()==null||!q.correctOption().matches("[A-D]"))throw BusinessException.invalid(field+".correctOption","请选择正确答案");
   if(q.sortOrder()==null||q.sortOrder()<0||q.sortOrder()>9999)throw BusinessException.invalid(field+".sortOrder","顺序须为0—9999");
   result.add(new QuestionInput(q.key(),q.type(),ArticleContent.text(q.stem(),field+".stem",2000,true),options,q.correctOption(),ArticleContent.text(q.explanation(),field+".explanation",4000,true),q.sortOrder()));
  }
  return result.stream().sorted(Comparator.comparingInt(QuestionInput::sortOrder)).toList();
 }
}
