package com.englishhero.reading.quiz;

import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.*;
import com.englishhero.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.reading.quiz.QuizDtos.*;
@Service
public class QuizService {
 private final QuizMapper mapper;private final ReadingService reading;private final AdminArticleService admin;private final ArticleMapper articles;private final ObjectMapper json;
 public QuizService(QuizMapper mapper,ReadingService reading,AdminArticleService admin,ArticleMapper articles,ObjectMapper json){this.mapper=mapper;this.reading=reading;this.admin=admin;this.articles=articles;this.json=json;}
 @Transactional(readOnly=true)public PublicQuiz published(SessionPrincipal actor,String a,String v){reading.version(actor,a,v);var r=mapper.article(ArticleRules.id(a,"articleId"),ArticleRules.id(v,"versionId"),false);return r==null||!r.status().equals("PUBLISHED")?null:publicDto(r);}
 @Transactional(readOnly=true)public AdminQuiz get(SessionPrincipal actor,String a,String v){admin.preview(actor,a,v);var r=mapper.article(ArticleRules.id(a,"articleId"),ArticleRules.id(v,"versionId"),false);return r==null?null:dto(r);}
 @Transactional public AdminQuiz save(SessionPrincipal actor,String a,String v,Input input){admin.requireAdmin(actor);long aid=ArticleRules.id(a,"articleId"),vid=ArticleRules.id(v,"versionId");if(articles.article(aid,true)==null||articles.version(aid,vid)==null)throw BusinessException.missing();var questions=QuizRules.questions(input);var r=mapper.article(aid,vid,true);check(input.expectedLockVersion(),r==null?0:r.lockVersion());if(r==null)mapper.create(aid,vid,json.writeValueAsString(questions),actor.userId());else mapper.save(r.id(),json.writeValueAsString(questions),actor.userId());return dto(mapper.article(aid,vid,false));}
 @Transactional public AdminQuiz status(SessionPrincipal actor,String id,Integer lock,boolean publish){admin.requireAdmin(actor);var r=mapper.get(ArticleRules.id(id,"quizId"),true);if(r==null)throw BusinessException.missing();check(lock,r.lockVersion());if(publish)mapper.publish(r,QuizRules.questions(new Input(mapper.draft(r),lock)),actor.userId());else mapper.unpublish(r.id(),actor.userId());return dto(mapper.get(r.id(),false));}
 private void check(Integer expected,int actual){if(expected==null||expected<0)throw BusinessException.invalid("expectedLockVersion","请提供题组版本");if(expected!=actual)throw new BusinessException(409,"QUIZ_CONFLICT","题组已更新，请加载最新草稿后编辑");}
 private PublicQuiz publicDto(QuizMapper.Row r){return new PublicQuiz(""+r.id(),""+r.publishedVersionId(),""+r.articleId(),""+r.articleVersionId(),mapper.questions(r.publishedVersionId()).stream().map(q->new PublicQuestion(""+q.id(),q.key(),q.type(),q.stem(),q.options())).toList());}
 private AdminQuiz dto(QuizMapper.Row r){return new AdminQuiz(""+r.id(),""+r.articleId(),""+r.articleVersionId(),mapper.draft(r),r.publishedVersionId()==null?null:publicDto(r),r.status(),r.revision(),r.lockVersion());}
}
