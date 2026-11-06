package com.englishhero.practice;
import java.util.*;
import com.englishhero.reading.ArticleContent;
import com.englishhero.reading.quiz.QuizDtos.QuestionInput;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;
public final class PracticeDraftRules {
 private PracticeDraftRules(){}
 public static String text(String s,String field,int max){return ArticleContent.text(s,field,max,false);}
 public static DraftInput normalize(DraftInput d){
  if(d==null||d.metadata()==null)throw BusinessException.invalid("metadata","请填写题组信息");
  if(!"CET4".equals(d.examCode())||!Set.of("DETAILED","CLOZE","MATCH","LISTENING").contains(d.readingType()==null?"":d.readingType()))throw BusinessException.invalid("examCode","当前仅支持四级仔细阅读、选词填空和长篇匹配，其他分类为规划");
  var metadata=normalizeMetadata(d.metadata());
  if("LISTENING".equals(d.readingType())){var qs=PracticeListeningRules.questions(d.questions());return new DraftInput(d.articleId(),d.articleVersionId(),d.examCode(),d.readingType(),metadata,qs,d.expectedLockVersion(),List.of(),PracticeListeningRules.normalize(d.listening(),qs));}
  if(d.listening()!=null)throw BusinessException.invalid("listening","阅读题组不能附加听力数据");
  if("CLOZE".equals(d.readingType()))return new DraftInput(d.articleId(),d.articleVersionId(),d.examCode(),d.readingType(),metadata,PracticeClozeRules.normalizeQuestions(d.questions()),d.expectedLockVersion(),PracticeClozeRules.normalizeWordBank(d.wordBank()));
  if("MATCH".equals(d.readingType())){
   if(d.wordBank()!=null&&!d.wordBank().isEmpty())throw BusinessException.invalid("wordBank","长篇匹配不使用共享词库");
   return new DraftInput(d.articleId(),d.articleVersionId(),d.examCode(),d.readingType(),metadata,PracticeMatchRules.normalizeQuestions(d.questions()),d.expectedLockVersion(),List.of());
  }
  if(d.wordBank()!=null&&!d.wordBank().isEmpty())throw BusinessException.invalid("wordBank","仔细阅读题组不使用共享词库");
  if(d.questions()==null||d.questions().isEmpty()||d.questions().size()>10)throw BusinessException.invalid("questions","每组需要1–10道题目");
  var keys=new HashSet<String>();var qs=new ArrayList<QuestionInput>();int i=0;
  for(var q:d.questions()){String f="questions["+i+"]";if(q==null||q.key()==null||!q.key().matches("[A-Za-z0-9_-]{1,64}")||!keys.add(q.key()))throw BusinessException.invalid("questions","题目标识须唯一且有效");if(!Set.of("MAIN_IDEA","DETAIL","INFERENCE","VOCABULARY","ATTITUDE").contains(q.type()==null?"":q.type()))throw BusinessException.invalid(f+".type","请选择题型");if(q.options()==null||q.options().size()!=4)throw BusinessException.invalid(f+".options","需要四个选项位置");if(q.correctOption()!=null&&!q.correctOption().isBlank()&&!q.correctOption().matches("[A-D]"))throw BusinessException.invalid(f+".correctOption","答案须为A–D或未选择");
   qs.add(new QuestionInput(q.key(),q.type(),text(q.stem(),f+".stem",2000),q.options().stream().map(o->text(o,f+".options",1000)).toList(),q.correctOption()==null||q.correctOption().isBlank()?null:q.correctOption(),text(q.explanation(),f+".explanation",4000),i++));
  }
  return new DraftInput(d.articleId(),d.articleVersionId(),d.examCode(),d.readingType(),metadata,List.copyOf(qs),d.expectedLockVersion(),List.of());
 }
 public static Metadata normalizeMetadata(Metadata input){if(input==null)throw BusinessException.invalid("metadata","请填写题目信息");
  var m=input;String source=m.sourceKind();if(!Set.of("ORIGINAL","PAST").contains(source==null?"":source))throw BusinessException.invalid("metadata.sourceKind","请选择来源");
  String topic=m.topic();if(!Set.of("culture","education","life","technology").contains(topic==null?"":topic))throw BusinessException.invalid("metadata.topic","请选择主题");
  boolean past=source.equals("PAST");return new Metadata(text(m.title(),"metadata.title",200),text(m.titleZh(),"metadata.titleZh",500),source,topic,past?m.year():null,past?m.month():null,past?m.paperNo():null,text(m.sourceName(),"metadata.sourceName",500),text(m.sourceUrl(),"metadata.sourceUrl",2000),text(m.usageNote(),"metadata.usageNote",2000));
 }

}
