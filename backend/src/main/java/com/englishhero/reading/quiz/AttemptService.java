package com.englishhero.reading.quiz;

import java.util.*;
import com.englishhero.identity.*;
import com.englishhero.reading.*;
import com.englishhero.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.englishhero.reading.quiz.AttemptDtos.*;
import static com.englishhero.reading.quiz.QuizDtos.*;
@Service
public class AttemptService {
 private final com.englishhero.reading.checkin.CheckinService checkins;private final AttemptMapper mapper;private final QuizMapper quizzes;private final QuizService quizService;private final ArticleMapper articles;private final ReadingMapper progress;private final ReadingService reading;private final IdentityService identities;
 public AttemptService(AttemptMapper mapper,QuizMapper quizzes,QuizService quizService,ArticleMapper articles,ReadingMapper progress,ReadingService reading,IdentityService identities,com.englishhero.reading.checkin.CheckinService checkins){this.checkins=checkins;this.mapper=mapper;this.quizzes=quizzes;this.quizService=quizService;this.articles=articles;this.progress=progress;this.reading=reading;this.identities=identities;}
 @Transactional(readOnly=true)public State state(SessionPrincipal actor,String a,String v){var published=quizService.published(actor,a,v);long version=ArticleRules.id(v,"versionId");var quiz=quizzes.article(ArticleRules.id(a,"articleId"),version,false);var active=quiz==null?null:mapper.active(actor.userId(),quiz.id());var latest=mapper.latest(actor.userId(),version);return new State(published,active!=null?dto(active):latest==null?null:dto(latest),summary(mapper.first(actor.userId(),version)),summary(mapper.latestCompleted(actor.userId(),version)));}
 @Transactional(readOnly=true)public ReadingDtos.PageDto<Summary> history(SessionPrincipal actor,String a,String v,int page,int size){reading.version(actor,a,v);ArticleRules.page(page);ArticleRules.pageSize(size);long version=ArticleRules.id(v,"versionId");return new ReadingDtos.PageDto<>(mapper.history(actor.userId(),version,page,size).stream().map(this::summary).toList(),page,size,mapper.count(actor.userId(),version));}
 @Transactional(readOnly=true)public Attempt get(SessionPrincipal actor,String id){identities.current(actor);return dto(owned(actor,id,false));}
 @Transactional public Attempt start(SessionPrincipal actor,String id,StartInput input){
  identities.current(actor);long qid=ArticleRules.id(id,"quizId");if(input==null||input.clientRequestId()==null||!input.clientRequestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw BusinessException.invalid("clientRequestId","请提供有效的请求标识");
  var target=quizzes.get(qid,false);if(target==null)throw missing();var article=articles.article(target.articleId(),true);var quiz=quizzes.get(qid,true);mapper.lockUser(actor.userId());var requested=mapper.request(actor.userId(),input.clientRequestId());if(requested!=null){if(requested.quizId()!=qid)throw conflict("请求标识已用于另一题组");return dto(requested,true);}
  var active=mapper.active(actor.userId(),qid,true);if(active!=null){mapper.remember(actor.userId(),input.clientRequestId(),active.id());return dto(active,true);}
  // Match management's article -> quiz lock order; current reads prevent stale publication checks.
  boolean available=article!=null&&!article.status().equals("DRAFT")&&((article.status().equals("PUBLISHED")&&Objects.equals(article.publishedVersionId(),quiz.articleVersionId()))||progress.progressCurrent(actor.userId(),quiz.articleVersionId())!=null);
  if(!available||!quiz.status().equals("PUBLISHED"))throw new BusinessException(409,"QUIZ_UNAVAILABLE","题组已下架或暂不可开始新练习，已有记录保留");
  if(!Objects.equals(""+quiz.publishedVersionId(),input.quizVersionId()))throw new BusinessException(409,"QUIZ_CHANGED","题组已更新，请加载最新题目后开始");
  long created=mapper.create(actor.userId(),quiz,input.clientRequestId(),quizzes.questions(quiz.publishedVersionId(),true));mapper.remember(actor.userId(),input.clientRequestId(),created);return dto(mapper.get(actor.userId(),created,false),true);
 }
 @Transactional public Attempt choice(SessionPrincipal actor,String id,String question,ChoiceInput input,boolean submit){
  identities.current(actor);checkins.lockUser(actor.userId());var row=owned(actor,id,true);long qid=ArticleRules.id(question,"questionId");if(input==null||input.expectedLockVersion()==null||input.expectedLockVersion()<0)throw BusinessException.invalid("expectedLockVersion","请提供练习版本");String choice=input.choice();if(choice!=null&&!choice.matches("[A-D]")||submit&&choice==null)throw BusinessException.invalid("choice","请选择A—D中的一个选项");
  var questions=quizzes.questions(row.quizVersionId(),true);var current=questions.stream().filter(q->q.id()==qid).findFirst().orElseThrow(this::missing);var answers=mapper.answers(row.id(),true);var answer=answers.stream().filter(a->a.questionId()==qid).findFirst().orElseThrow(this::missing);
  if(answer.submittedChoice()!=null){if(submit&&answer.submittedChoice().equals(choice))return dto(row,true);throw conflict("本题已提交，不能修改答案");}
  if(!row.status().equals("IN_PROGRESS")||input.expectedLockVersion()!=row.lockVersion())throw conflict("练习已在其他页面更新，请加载最新记录");
  if(submit){var first=questions.stream().filter(q->answers.stream().anyMatch(a->a.questionId()==q.id()&&a.submittedChoice()==null)).findFirst().orElseThrow(this::missing);if(first.id()!=qid)throw conflict("请先提交当前题目");long done=answers.stream().filter(a->a.submittedChoice()!=null).count();mapper.submit(row.id(),qid,choice,current.answer().equals(choice),done+1==row.total());if(done+1==row.total())checkins.completed(actor.userId(),row.articleVersionId());}
  else if(!Objects.equals(answer.draftChoice(),choice))mapper.draft(row.id(),qid,choice);
  return dto(mapper.get(actor.userId(),row.id(),false),true);
 }
 private AttemptMapper.Row owned(SessionPrincipal actor,String id,boolean lock){var row=mapper.get(actor.userId(),ArticleRules.id(id,"attemptId"),lock);if(row==null)throw missing();return row;}
 private Summary summary(AttemptMapper.Row r){return r==null?null:new Summary(""+r.id(),""+r.quizId(),""+r.quizVersionId(),""+r.articleId(),""+r.articleVersionId(),r.attemptNo(),r.status(),r.status().equals("COMPLETED")?r.correctCount():null,r.total(),r.startedAt(),r.completedAt());}
 private Attempt dto(AttemptMapper.Row r){return dto(r,false);}
 private Attempt dto(AttemptMapper.Row r,boolean lock){var questions=quizzes.questions(r.quizVersionId(),lock);var answers=mapper.answers(r.id(),lock);var publicQuiz=new PublicQuiz(""+r.quizId(),""+r.quizVersionId(),""+r.articleId(),""+r.articleVersionId(),questions.stream().map(q->new PublicQuestion(""+q.id(),q.key(),q.type(),q.stem(),q.options())).toList());var saved=questions.stream().map(q->{var a=answers.stream().filter(x->x.questionId()==q.id()).findFirst().orElseThrow(this::missing);boolean submitted=a.submittedChoice()!=null;return new SavedAnswer(""+q.id(),a.draftChoice(),a.submittedChoice(),a.correct(),submitted?q.answer():null,submitted?q.explanation():null,a.submittedAt());}).toList();return new Attempt(""+r.id(),""+r.quizId(),""+r.quizVersionId(),""+r.articleId(),""+r.articleVersionId(),r.attemptNo(),r.status(),r.status().equals("COMPLETED")?r.correctCount():null,r.total(),r.lockVersion(),r.startedAt(),r.completedAt(),publicQuiz,saved);}
 private BusinessException missing(){return new BusinessException(404,"ATTEMPT_NOT_FOUND","练习或题目不存在");}
 private BusinessException conflict(String message){return new BusinessException(409,"ATTEMPT_CONFLICT",message);}
}
