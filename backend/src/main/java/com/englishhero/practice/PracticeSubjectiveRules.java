package com.englishhero.practice;
import java.util.*;
import com.englishhero.reading.ArticleContent;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeSubjectiveDtos.*;
public final class PracticeSubjectiveRules {
 private PracticeSubjectiveRules(){}
 public static boolean isSubjective(String type){return "WRITING".equals(type)||"TRANSLATION".equals(type);}
 public static int words(String text){return (int)ArticleContent.WORD.matcher(text).results().count();}
 public static String answer(String text){if(text==null)text="";if(text.length()>20000)throw BusinessException.invalid("answerText","最多20000字符");ArticleContent.text(text,"answerText",20000,false);return text.replace("\r\n","\n").replace('\r','\n');}
 public static Content normalize(String type,Content c){
  if(!isSubjective(type))throw BusinessException.invalid("readingType","请选择写作或翻译");
  if(c==null)throw BusinessException.invalid("subjective","请填写题目内容");
  if(c.minutes()!=null&&(c.minutes()<1||c.minutes()>180))throw BusinessException.invalid("subjective.minutes","建议时间为1–180分钟");
  Integer min="WRITING".equals(type)?c.minWords():null,max="WRITING".equals(type)?c.maxWords():null;
  if((min==null)!=(max==null)||min!=null&&(min<1||max<min||max>5000))throw BusinessException.invalid("subjective.minWords","请填写有效建议词数范围");
  return new Content(ArticleContent.text(c.prompt(),"subjective.prompt",20000,false),ArticleContent.text(c.requirements(),"subjective.requirements",4000,false),min,max,c.minutes(),ArticleContent.text(c.reference(),"subjective.reference",20000,false),ArticleContent.text(c.notes(),"subjective.notes",10000,false));
 }
 public static List<PracticeDtos.Check> contentChecks(Content c){var result=new ArrayList<PracticeDtos.Check>();if(c.prompt().isBlank())result.add(new PracticeDtos.Check("subjective.prompt",null,"请填写题目材料"));if(words(c.reference())<1)result.add(new PracticeDtos.Check("subjective.reference",null,"请填写英文参考范文或译文"));if(c.notes().isBlank())result.add(new PracticeDtos.Check("subjective.notes",null,"请填写对照要点"));return result;}
}
