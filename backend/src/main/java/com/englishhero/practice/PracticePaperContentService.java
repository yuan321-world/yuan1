package com.englishhero.practice;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.reading.*;
import com.englishhero.reading.quiz.QuizDtos.PublicQuestion;
import com.englishhero.reading.quiz.QuizMapper;
import static com.englishhero.practice.PracticePaperDtos.*;

@Service
public class PracticePaperContentService {
 private final PracticeSetMapper sets;private final PracticeQuizMapper quizzes;private final AdminArticleService articles;private final ObjectMapper json;
 public PracticePaperContentService(PracticeSetMapper sets,PracticeQuizMapper quizzes,AdminArticleService articles,ObjectMapper json){this.sets=sets;this.quizzes=quizzes;this.articles=articles;this.json=json;}
 public PracticeSetMapper.Version version(String id){var v=sets.version(ArticleRules.id(id,"setVersionId"));if(v==null)throw PracticeAdminService.missing();return v;}
 public PracticeDtos.Metadata metadata(PracticeSetMapper.Version v){return json.readValue(v.metadata(),PracticeDtos.Metadata.class);}
 public List<QuizMapper.Question> questions(PracticeSetMapper.Version v){return PracticeSubjectiveRules.isSubjective(v.readingType())?List.of():quizzes.published(v.quizVersionId());}
 public PracticePaperRules.Component descriptor(ComponentInput c,boolean requireAvailable){var v=version(c.setVersionId());var s=sets.get(v.setId(),requireAvailable);if(requireAvailable&&(!"PUBLISHED".equals(s.status())||s.publishedVersionId()==null))throw new com.englishhero.web.BusinessException(409,"PRACTICE_PAPER_UNAVAILABLE","组成题组已下架，不能开始新整卷");var m=metadata(v);return new PracticePaperRules.Component(c.slot(),c.setVersionId(),s.exam(),m.sourceKind(),m.year(),m.month(),v.readingType(),PracticeSubjectiveRules.isSubjective(v.readingType())?1:questions(v).size());}
 public String unavailable(List<ComponentInput> components){for(var c:components){var v=version(c.setVersionId());var s=sets.get(v.setId(),false);if(!"PUBLISHED".equals(s.status()))return "组成题组已下架，已有练习仍可继续";}return null;}
 public List<ComponentView> details(List<ComponentInput> components){return components.stream().map(c->{var v=version(c.setVersionId());var m=metadata(v);return new ComponentView(c.slot(),c.setVersionId(),m.titleZh().isBlank()?m.title():m.titleZh(),v.readingType(),PracticeSubjectiveRules.isSubjective(v.readingType())?1:questions(v).size());}).toList();}
 public List<ResponseInput> validate(List<ComponentInput> components,List<ResponseInput> responses,String slot,String question){
  if(responses==null||responses.size()!=7||!PracticePaperRules.SLOTS.contains(slot))throw com.englishhero.web.BusinessException.invalid("responses","须保存完整七部分作答和有效当前位置");var map=new HashMap<String,ResponseInput>();for(var r:responses)if(r==null||r.slot()==null||map.putIfAbsent(r.slot(),r)!=null)throw com.englishhero.web.BusinessException.invalid("responses","作答槽位不能重复");
  var result=new ArrayList<ResponseInput>();
  for(var c:components){var r=map.get(c.slot());if(r==null)throw com.englishhero.web.BusinessException.invalid("responses","缺少组成作答");var v=version(c.setVersionId());
   if(PracticeSubjectiveRules.isSubjective(v.readingType())){if(r.choices()!=null&&!r.choices().isEmpty())throw com.englishhero.web.BusinessException.invalid("choices","主观题不接受选择项");result.add(new ResponseInput(c.slot(),PracticeSubjectiveRules.answer(r.text()),Map.of()));if(slot.equals(c.slot())&&question!=null&&!question.isBlank())throw com.englishhero.web.BusinessException.invalid("currentQuestionId","主观题没有客观题号");}
   else{if(r.text()!=null&&!r.text().isEmpty())throw com.englishhero.web.BusinessException.invalid("text","客观题不接受作文文本");var qs=questions(v);var ids=qs.stream().map(q->""+q.id()).toList();var paragraphs="MATCH".equals(v.readingType())?PracticeMatchRules.paragraphLetters(body(v)):List.<String>of();result.add(new ResponseInput(c.slot(),"",PracticePaperRules.choices(v.readingType(),r.choices(),ids,paragraphs)));if(slot.equals(c.slot())&&question!=null&&!question.isBlank()&&!ids.contains(question))throw com.englishhero.web.BusinessException.invalid("currentQuestionId","题号不属于当前组成");}
  }return result;
 }
 private String body(PracticeSetMapper.Version v){var a=articles.version(v.articleId(),v.articleVersionId());return a.content().paragraphs().stream().map(p->p.sentences().stream().map(ArticleContent.Sentence::text).reduce((x,y)->x+"\n"+y).orElse("")).reduce((x,y)->x+"\n\n"+y).orElse("");}
 public ComponentAttempt component(ComponentInput c,ResponseInput response,boolean done,Instant started,Instant completed){var v=version(c.setVersionId());var m=metadata(v);String title=m.titleZh().isBlank()?m.title():m.titleZh();
  if(PracticeSubjectiveRules.isSubjective(v.readingType())){var content=json.readValue(sets.subjective(v.id(),true),PracticeSubjectiveDtos.Content.class);return new ComponentAttempt(c.slot(),v.readingType(),title,null,content.publicPrompt(),response.text(),PracticeSubjectiveRules.words(response.text()),done?content.reference():null,done?content.notes():null);}
  var qs=questions(v);var answers=qs.stream().map(q->{String chosen=response.choices().get(""+q.id());return new PracticeDtos.Answer(""+q.id(),chosen,done?chosen:null,done?Objects.equals(chosen,q.answer()):null,done?q.answer():null,done?q.explanation():null,false);}).toList();
  var listening=sets.listening(v.id());var objective=new PracticeDtos.Attempt("paper-component",""+v.setId(),""+v.id(),""+v.quizVersionId(),"FULL",null,done?"COMPLETED":"IN_PROGRESS",0,""+qs.getFirst().id(),qs.size(),"LISTENING".equals(v.readingType())&&!done?null:articles.version(v.articleId(),v.articleVersionId()),qs.stream().map(q->new PublicQuestion(""+q.id(),q.key(),q.type(),q.stem(),q.options())).toList(),answers,done?(int)answers.stream().filter(a->Boolean.TRUE.equals(a.correct())).count():null,started,completed,title,v.readingType(),v.wordBankJson()==null?List.of():List.of(json.readValue(v.wordBankJson(),String[].class)),listening==null?null:json.readValue(listening,PracticeDtos.ListeningData.class),new PracticeDtos.PlaybackState(0,1));return new ComponentAttempt(c.slot(),v.readingType(),title,objective,null,"",0,null,null);
 }
}
