package com.englishhero.practice;
import java.util.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.identity.*;
import com.englishhero.reading.*;
import com.englishhero.reading.quiz.QuizDtos.PublicQuestion;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeAttemptService {
 private final PracticeAttemptMapper attempts;private final PracticeSetMapper sets;private final PracticeQuizMapper quizzes;private final ArticleMapper articles;private final AdminArticleService articleDtos;private final IdentityService identities;private final PracticePublicationChecks hashes;private final ObjectMapper json;private final Clock clock;private final PracticePaperMistakeLedger paperMistakes;
 public PracticeAttemptService(PracticeAttemptMapper attempts,PracticeSetMapper sets,PracticeQuizMapper quizzes,ArticleMapper articles,AdminArticleService articleDtos,IdentityService identities,PracticePublicationChecks hashes,ObjectMapper json,Clock clock,PracticePaperMistakeLedger paperMistakes){this.attempts=attempts;this.sets=sets;this.quizzes=quizzes;this.articles=articles;this.articleDtos=articleDtos;this.identities=identities;this.hashes=hashes;this.json=json;this.clock=clock;this.paperMistakes=paperMistakes;}
 static BusinessException conflict(){return new BusinessException(409,"PRACTICE_CONFLICT","练习已更新，请加载最新版本并保留本地选择后重试");}
 private record ChoiceChange(String choice,boolean choiceProvided,Boolean flagged){}
 static void requestId(String id){if(id==null||!id.matches("[A-Za-z0-9-]{16,64}"))throw BusinessException.invalid("requestId","请提供16–64位有效请求标识");}
 private PracticeAttemptMapper.Row owned(long user,String id,boolean lock){var r=attempts.get(user,ArticleRules.id(id,"attemptId"),lock);if(r==null)throw PracticeAdminService.missing();return r;}
 private void version(PracticeAttemptMapper.Row r,Integer expected){if(expected==null||expected<0)throw BusinessException.invalid("expectedVersion","请提供练习版本");if(r.version()!=expected||!r.status().equals("IN_PROGRESS"))throw conflict();}
 static void checkRequest(PracticeAttemptMapper.Request r,String op,String hash){if(!r.operation().equals(op)||!r.hash().equals(hash))throw new BusinessException(409,"PRACTICE_REQUEST_CONFLICT","请求标识已用于其他操作或内容");}
 public Attempt get(SessionPrincipal actor,String id){identities.current(actor);return dto(owned(actor.userId(),id,false));}
 public Attempt start(SessionPrincipal actor,String id,StartInput input){identities.current(actor);if(input==null)throw BusinessException.invalid("requestId","请提供开始请求");requestId(input.requestId());long sid=ArticleRules.id(id,"setId");var before=sets.get(sid,false);if(before==null)throw PracticeAdminService.missing();if(PracticeSubjectiveRules.isSubjective(before.readingType()))throw BusinessException.invalid("setId","请从写作或翻译入口开始练习");var article=articles.article(before.articleId(),true);var current=sets.get(sid,false);quizzes.get(current.quizId(),true);var set=sets.get(sid,true);attempts.lockUser(actor.userId());String hash=hashes.hash(List.of("START",id,input));var request=attempts.request(actor.userId(),input.requestId());if(request!=null){checkRequest(request,"START",hash);return dto(attempts.get(actor.userId(),request.attempt(),false));}
  var active=attempts.active(actor.userId(),sid,"FULL");if(active!=null){attempts.remember(actor.userId(),input.requestId(),"START",hash,active.id(),null,active.version());return dto(active);}
  if(article==null||!set.status().equals("PUBLISHED")||set.publishedVersionId()==null)throw new BusinessException(409,"PRACTICE_SET_CHANGED","题组已下架，已有练习仍可从我的练习恢复");if(!Objects.equals(""+set.publishedVersionId(),input.setVersionId()))throw new BusinessException(409,"PRACTICE_SET_CHANGED","题组已更新，请重新加载题库后开始");
  var v=sets.version(set.publishedVersionId());long created=attempts.create(actor.userId(),v,"FULL",null,quizzes.published(v.quizVersionId()));attempts.remember(actor.userId(),input.requestId(),"START",hash,created,null,0);return dto(attempts.get(actor.userId(),created,false));
 }
 public Attempt saveDraft(SessionPrincipal actor,String id,DraftAnswersInput input){
  identities.current(actor);attempts.lockUser(actor.userId());var r=owned(actor.userId(),id,true);
  if(input==null||input.answers()==null||input.answers().size()>r.total())throw BusinessException.invalid("answers","仅保存本轮题目答案");
  var setVersion=sets.version(r.setVersion());String readingType=setVersion.readingType();boolean cloze="CLOZE".equals(readingType);boolean match="MATCH".equals(readingType);
  var saved=attempts.answers(r.id());var savedById=new HashMap<Long,PracticeAttemptMapper.Saved>();for(var answer:saved)savedById.put(answer.question(),answer);
  List<String> matchParagraphs=match?matchParagraphs(setVersion):List.of();
  var ids=new HashSet<Long>();var changes=new LinkedHashMap<Long,ChoiceChange>();
  for(var c:input.answers()){
   if(c==null)throw BusinessException.invalid("answers","请选择本轮题目");
   long qid=ArticleRules.id(c.questionId(),"questionId");if(!ids.add(qid)||!savedById.containsKey(qid))throw BusinessException.invalid("answers","只接受本轮题目且不能重复");
   if(c.choiceProvided()&&c.choice()!=null){
    boolean valid=cloze?c.choice().matches("[A-O]"):match?c.choice().matches("[A-Z]"):c.choice().matches("[A-D]");
    if(!valid)throw BusinessException.invalid("choice",cloze?"请选择A–O或清空":match?"请选择A–Z或清空":"请选择A–D或清空");
    if(match&&!matchParagraphs.contains(c.choice()))throw BusinessException.invalid("choice","请选择本轮原文中存在的段落字母或清空");
   }
   changes.put(qid,new ChoiceChange(c.choice(),c.choiceProvided(),c.flagged()));
  }
  long current=ArticleRules.id(input.currentQuestionId(),"currentQuestionId");if(!savedById.containsKey(current))throw BusinessException.invalid("currentQuestionId","题号不属于本轮练习");
  version(r,input.expectedVersion());
  var merged=new LinkedHashMap<Long,String>();for(var answer:saved)merged.put(answer.question(),answer.draft());
  for(var change:changes.entrySet())if(change.getValue().choiceProvided())merged.put(change.getKey(),change.getValue().choice());
  if(cloze)PracticeClozeRules.validateUniqueChoices(merged.values());
  boolean changed=!Objects.equals(r.current(),current);
  if(input.playback()!=null){if(!"LISTENING".equals(readingType))throw BusinessException.invalid("playback","阅读练习没有音频状态");var playback=PracticeListeningRules.playback(input.playback());String value=json.writeValueAsString(playback);if(!Objects.equals(value,attempts.playback(r.id()))){attempts.playback(r.id(),value);changed=true;}}

  for(var entry:changes.entrySet()){
   var old=savedById.get(entry.getKey());var next=entry.getValue();
   String choice=next.choiceProvided()?next.choice():old.draft();
   boolean choiceChanged=next.choiceProvided()&&!Objects.equals(old.draft(),choice);
   boolean flagChanged=next.flagged()!=null&&!Objects.equals(old.flagged(),next.flagged());
   if(choiceChanged||flagChanged){attempts.save(r.id(),entry.getKey(),choice,next.flagged());changed=true;}
  }
  if(changed)attempts.advance(r,current);
  return dto(attempts.get(actor.userId(),r.id(),false));
 }
 public Attempt submit(SessionPrincipal actor,String id,SubmitInput input){identities.current(actor);if(input==null||input.confirmUnanswered()==null)throw BusinessException.invalid("confirmUnanswered","请确认未答题处理方式");requestId(input.requestId());attempts.lockUser(actor.userId());var r=owned(actor.userId(),id,true);String hash=hashes.hash(List.of("SUBMIT",id,input));var request=attempts.request(actor.userId(),input.requestId());if(request!=null){checkRequest(request,"SUBMIT",hash);return dto(attempts.get(actor.userId(),request.attempt(),false));}version(r,input.expectedVersion());var saved=attempts.answers(r.id());if(saved.stream().anyMatch(a->a.draft()==null)&&!input.confirmUnanswered())throw new BusinessException(400,"PRACTICE_UNANSWERED_CONFIRMATION_REQUIRED","还有未答题，确认后将按错题计入");var at=clock.instant();var qs=quizzes.published(r.quizVersion());boolean paperReview=attempts.paperSource(r.id())!=null;attempts.grade(r,qs,at,!paperReview);if(paperReview)paperMistakes.recordReview(r,qs,at);attempts.remember(actor.userId(),input.requestId(),"SUBMIT",hash,r.id(),null,r.version()+1);return dto(attempts.get(actor.userId(),r.id(),false));}
 public Attempt retryMistakes(SessionPrincipal actor,String id,RetryInput input){identities.current(actor);if(input==null)throw BusinessException.invalid("requestId","请提供请求标识");requestId(input.requestId());attempts.lockUser(actor.userId());var source=owned(actor.userId(),id,true);String hash=hashes.hash(List.of("RETRY",id,input));var request=attempts.request(actor.userId(),input.requestId());if(request!=null){checkRequest(request,"RETRY",hash);return dto(attempts.get(actor.userId(),request.attempt(),false));}if(!source.status().equals("COMPLETED"))throw conflict();if(attempts.paperSource(source.id())!=null)throw new BusinessException(409,"PRACTICE_PAPER_REVIEW_SOURCE","请从整卷错题本开始复习");var active=attempts.active(actor.userId(),source.set(),"MISTAKES");if(active!=null){if(attempts.paperSource(active.id())!=null)throw PracticePaperMistakeService.activeConflict(active.id());attempts.remember(actor.userId(),input.requestId(),"RETRY",hash,active.id(),null,active.version());return dto(active);}var wrong=attempts.answers(source.id()).stream().filter(a->!Boolean.TRUE.equals(a.correct())).map(PracticeAttemptMapper.Saved::question).toList();var published=quizzes.published(source.quizVersion());String readingType=sets.version(source.setVersion()).readingType();boolean fullRetry=Set.of("CLOZE","MATCH","LISTENING").contains(readingType);var qs=fullRetry?published:published.stream().filter(q->wrong.contains(q.id())).toList();if(qs.isEmpty()||fullRetry&&wrong.isEmpty())throw new BusinessException(409,"PRACTICE_NO_MISTAKES","本轮没有需要重做的错题");long created=attempts.create(actor.userId(),sets.version(source.setVersion()),"MISTAKES",source.id(),qs);attempts.remember(actor.userId(),input.requestId(),"RETRY",hash,created,null,0);return dto(attempts.get(actor.userId(),created,false));}
 public ReadingDtos.PageDto<Attempt> history(SessionPrincipal actor,Query q,String status){identities.current(actor);PracticeQueries.allowed("status",status,Set.of("IN_PROGRESS","COMPLETED","NOT_STARTED"));return new ReadingDtos.PageDto<>(attempts.history(actor.userId(),q,status).stream().map(this::dto).toList(),q.page(),q.pageSize(),attempts.count(actor.userId(),q,status,false));}
 public ReadingDtos.PageDto<Mistake> mistakes(SessionPrincipal actor,Query q,String status){identities.current(actor);PracticeQueries.allowed("status",status,Set.of("PENDING","RESOLVED"));return new ReadingDtos.PageDto<>(attempts.mistakes(actor.userId(),q,status),q.page(),q.pageSize(),attempts.count(actor.userId(),q,status,true));}
 private Attempt dto(PracticeAttemptMapper.Row r){if(r==null)throw PracticeAdminService.missing();var v=sets.version(r.setVersion());var meta=json.readValue(v.metadata(),Metadata.class);var saved=attempts.answers(r.id());var qs=quizzes.published(r.quizVersion()).stream().filter(q->saved.stream().anyMatch(a->a.question()==q.id())).toList();boolean completed=r.status().equals("COMPLETED");var answers=qs.stream().map(q->{var a=saved.stream().filter(x->x.question()==q.id()).findFirst().orElseThrow();return new Answer(""+q.id(),a.draft(),completed?a.submitted():null,completed?a.correct():null,completed?q.answer():null,completed?q.explanation():null,a.flagged());}).toList();return new Attempt(""+r.id(),""+r.set(),""+r.setVersion(),""+r.quizVersion(),r.kind(),r.source()==null?null:r.source().toString(),r.status(),r.version(),""+r.current(),r.total(),"LISTENING".equals(v.readingType())&&!completed?null:articleDtos.version(v.articleId(),v.articleVersionId()),qs.stream().map(q->new PublicQuestion(""+q.id(),q.key(),q.type(),q.stem(),q.options())).toList(),answers,completed?r.correct():null,r.started(),r.completed(),meta.titleZh().isBlank()?meta.title():meta.titleZh(),v.readingType(),wordBank(v.wordBankJson()),listening(v.id()),"LISTENING".equals(v.readingType())?playback(r.id()):null,reviewSource(r));}
 private PaperReviewSource reviewSource(PracticeAttemptMapper.Row r){var origin=attempts.paperSource(r.id());if(origin==null)return null;var full=quizzes.published(r.quizVersion());var numbers=new LinkedHashMap<String,Integer>();for(int i=0;i<full.size();i++)numbers.put(""+full.get(i).id(),PracticePaperMistakeRules.originalNumber(origin.slot(),i));return new PaperReviewSource(""+origin.attemptId(),origin.slot(),numbers);}
 private ListeningData listening(long id){String value=sets.listening(id);return value==null?null:json.readValue(value,ListeningData.class);}
 private PlaybackState playback(long id){String value=attempts.playback(id);return value==null?new PlaybackState(0,1):json.readValue(value,PlaybackState.class);}
 private List<String> matchParagraphs(PracticeSetMapper.Version version){var article=articles.version(version.articleId(),version.articleVersionId());if(article==null)throw PracticeAdminService.missing();var doc=json.readValue(article.contentJson(),ArticleContent.Document.class);String body=doc.paragraphs().stream().map(p->p.sentences().stream().map(ArticleContent.Sentence::text).reduce((a,b)->a+"\n"+b).orElse("")).reduce((a,b)->a+"\n\n"+b).orElse("");if(PracticeMatchRules.paragraphIssue(body)!=null)throw new BusinessException(409,"PRACTICE_SET_CHANGED","练习原文版本不符合匹配段落结构");return PracticeMatchRules.paragraphLetters(body);}
 private List<String> wordBank(String value){return value==null?List.of():List.of(json.readValue(value,String[].class));}
}
