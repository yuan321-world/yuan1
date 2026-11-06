package com.englishhero.practice;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.englishhero.identity.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticePaperMistakeService {
 private final IdentityService identities;private final PracticePaperMapper papers;private final PracticePaperContentService content;private final PracticeAttemptMapper attempts;private final PracticeAttemptService practice;private final PracticePaperMistakeMapper mistakes;private final PracticePublicationChecks hashes;
 public PracticePaperMistakeService(IdentityService identities,PracticePaperMapper papers,PracticePaperContentService content,PracticeAttemptMapper attempts,PracticeAttemptService practice,PracticePaperMistakeMapper mistakes,PracticePublicationChecks hashes){this.identities=identities;this.papers=papers;this.content=content;this.attempts=attempts;this.practice=practice;this.mistakes=mistakes;this.hashes=hashes;}
 public static PracticePaperMistakeDtos.Query query(Map<String,String> params){
  var base=PracticePaperCatalogService.query(params);String status=params.getOrDefault("status","PENDING"),type=params.get("readingType"),source=params.get("sourceAttemptId");
  if(!Set.of("PENDING","RESOLVED").contains(status))throw BusinessException.invalid("status","请选择待订正或已订正");
  PracticeQueries.allowed("readingType",type,Set.of("LISTENING","CLOZE","MATCH","DETAILED"));if(source!=null&&!source.isBlank())ArticleRules.id(source,"sourceAttemptId");
  return new PracticePaperMistakeDtos.Query(base.exam(),base.year(),base.month(),type,status,source==null||source.isBlank()?null:source,base.page(),base.pageSize());
 }
 @Transactional(readOnly=true)
 public com.englishhero.reading.ReadingDtos.PageDto<PracticePaperMistakeDtos.PaperMistake> list(SessionPrincipal actor,PracticePaperMistakeDtos.Query query){
  identities.current(actor);Set<PracticePaperMistakeDtos.SourceKey> scope=null;
  if(query.sourceAttemptId()!=null){
   var source=papers.run(actor.userId(),ArticleRules.id(query.sourceAttemptId(),"sourceAttemptId"),false);if(!"COMPLETED".equals(source.status()))throw BusinessException.invalid("sourceAttemptId","交卷后才能查看错题");scope=new HashSet<>();
   var responses=papers.responses(source.id());for(var component:papers.components(source.version())){var v=content.version(component.setVersionId());if(PracticeSubjectiveRules.isSubjective(v.readingType()))continue;var choices=responses.stream().filter(r->r.slot().equals(component.slot())).findFirst().orElseThrow().choices();for(var q:content.questions(v))if(!Objects.equals(q.answer(),choices.get(""+q.id())))scope.add(new PracticePaperMistakeDtos.SourceKey(v.id(),q.id()));}
  }
  return new com.englishhero.reading.ReadingDtos.PageDto<>(mistakes.list(actor.userId(),query,scope),query.page(),query.pageSize(),mistakes.count(actor.userId(),query,scope));
 }
 public PracticeDtos.Attempt retry(SessionPrincipal actor,String sourceId,PracticePaperMistakeDtos.RetryInput input){
  identities.current(actor);if(input==null)throw BusinessException.invalid("requestId","请提供重练请求");PracticeAttemptService.requestId(input.requestId());
  attempts.lockUser(actor.userId());var source=papers.run(actor.userId(),ArticleRules.id(sourceId,"sourceAttemptId"),true);
  if(!"COMPLETED".equals(source.status()))throw BusinessException.invalid("sourceAttemptId","交卷后才能复习错题");
  PracticePaperMistakeRules.originalNumber(input.slot(),0);
  String hash=hashes.hash(List.of("PAPER_RETRY",sourceId,input));var replay=attempts.request(actor.userId(),input.requestId());
  if(replay!=null){PracticeAttemptService.checkRequest(replay,"RETRY",hash);return practice.get(actor,""+replay.attempt());}
  var component=papers.components(source.version()).stream().filter(c->c.slot().equals(input.slot())).findFirst().orElseThrow(()->BusinessException.invalid("slot","请选择来源试卷中的部分"));
  var v=content.version(component.setVersionId());var active=attempts.active(actor.userId(),v.setId(),"MISTAKES");
  if(active!=null){var origin=attempts.paperSource(active.id());if(origin==null||active.setVersion()!=v.id())throw activeConflict(active.id());attempts.remember(actor.userId(),input.requestId(),"RETRY",hash,active.id(),null,active.version());return practice.get(actor,""+active.id());}
  var pending=mistakes.pending(actor.userId(),v.id());var full=content.questions(v);
  if(full.stream().noneMatch(q->pending.contains(q.id())))throw new BusinessException(409,"PRACTICE_NO_MISTAKES","本部分没有待订正错题");
  var selected=PracticePaperMistakeRules.fullReview(v.readingType())?full:full.stream().filter(q->pending.contains(q.id())).toList();
  long id=attempts.create(actor.userId(),v,"MISTAKES",null,selected);attempts.attachPaperSource(id,source.id(),input.slot());
  attempts.remember(actor.userId(),input.requestId(),"RETRY",hash,id,null,0);return practice.get(actor,""+id);
 }
 static BusinessException activeConflict(long id){return new BusinessException(409,"PRACTICE_REVIEW_ACTIVE","该题组已有其他来源或版本的错题练习，请先继续或完成",Map.of("activeAttemptId",""+id));}
}
