package com.englishhero.practice.review;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import com.englishhero.identity.*;import com.englishhero.practice.*;import com.englishhero.reading.ArticleRules;import com.englishhero.web.BusinessException;
import static com.englishhero.practice.review.PracticeReviewDtos.*;
@Service @Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeReviewService {
 final PracticeReviewMapper m;final PracticeReviewSourceResolver sources;final PracticeReviewProperties config;final IdentityService identities;final PracticePublicationChecks hashes;final Clock clock;
 public PracticeReviewService(PracticeReviewMapper m,PracticeReviewSourceResolver sources,PracticeReviewProperties config,IdentityService identities,PracticePublicationChecks hashes,Clock clock){this.m=m;this.sources=sources;this.config=config;this.identities=identities;this.hashes=hashes;this.clock=clock;}
 static BusinessException error(int status,String code,String message){return new BusinessException(status,code,message);}
 public ReviewDto get(SessionPrincipal actor,String id){identities.current(actor);return m.dto(actor.userId(),ArticleRules.id(id,"id"));}
 public List<ReviewDto> history(SessionPrincipal actor,String type,String id,String slot){identities.current(actor);var source=sources.resolve(actor,type,id,slot);String scope=scope(source);return m.jdbc().queryForList("SELECT id FROM practice_subjective_review WHERE user_id=? AND scope_hash=? ORDER BY id DESC LIMIT 20",Long.class,actor.userId(),scope).stream().map(n->m.dto(actor.userId(),n)).toList();}
 String scope(Source s){return hashes.hash(List.of(s.sourceType(),s.attemptId(),s.slot(),hashes.hash(s.text())));}
 public ReviewDto create(SessionPrincipal actor,CreateReview in){identities.current(actor);if(in==null||in.requestId()==null||!in.requestId().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw BusinessException.invalid("requestId","请提供UUID请求编号");
  long uid=actor.userId();m.jdbc().queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,uid);String hash=hashes.hash(in);
  var old=m.jdbc().queryForList("SELECT request_hash,review_id FROM practice_subjective_review_request WHERE user_id=? AND request_id=?",uid,in.requestId());if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("request_hash")))throw error(409,"PRACTICE_REVIEW_CONFLICT","同一请求编号不能对应不同作答");return m.dto(uid,((Number)old.getFirst().get("review_id")).longValue());}
  var source=sources.resolve(actor,in.sourceType(),in.attemptId(),in.slot());if(!"2026-10-26".equals(in.consentVersion()))throw BusinessException.invalid("consentVersion","请先确认发送说明");String scope=scope(source);
  var reuse=m.jdbc().queryForList("SELECT id FROM practice_subjective_review WHERE user_id=? AND scope_hash=? AND status IN ('QUEUED','RUNNING','SUCCEEDED') ORDER BY id DESC LIMIT 1",Long.class,uid,scope);
  if(!reuse.isEmpty())return remember(uid,in,hash,reuse.getFirst());
  if(!config.ready())throw error(503,"PRACTICE_AI_UNAVAILABLE","AI批改暂未开放，请先查看参考内容");
  if(m.jdbc().queryForObject("SELECT COUNT(*) FROM practice_subjective_review WHERE user_id=? AND status IN ('QUEUED','RUNNING')",Long.class,uid)>0)throw error(409,"PRACTICE_REVIEW_ACTIVE","请等待当前批改完成");
  if(m.jdbc().queryForObject("SELECT COUNT(*) FROM practice_subjective_review WHERE user_id=? AND scope_hash=? AND (error_code IS NULL OR error_code<>'QUEUE_EXPIRED')",Long.class,uid,scope)>=3)throw error(429,"PRACTICE_REVIEW_LIMIT","本次作答已达到批改尝试上限");
  Instant now=clock.instant();var recent=m.jdbc().query("SELECT created_at FROM practice_subjective_review WHERE user_id=? AND scope_hash=? ORDER BY id DESC LIMIT 1",(r,n)->r.getTimestamp(1).toInstant(),uid,scope);if(!recent.isEmpty()&&recent.getFirst().plusSeconds(10).isAfter(now))throw error(429,"PRACTICE_REVIEW_COOLDOWN","请稍后再试");
  LocalDate day=now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();if(m.jdbc().queryForObject("SELECT COUNT(*) FROM practice_subjective_review WHERE user_id=? AND budget_day=? AND (error_code IS NULL OR error_code<>'QUEUE_EXPIRED')",Long.class,uid,day)>=config.getUserDailyLimit())throw error(429,"PRACTICE_REVIEW_LIMIT","今日批改次数已用完");
  // Check the actual escaped wire payload, not merely the unescaped student's text.
  if(PracticeReviewPrompt.requestJson(source,config.getModel(),m.json()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>90000)throw BusinessException.invalid("attemptId","本次材料超过批改输入上限");
  m.jdbc().update("INSERT IGNORE INTO practice_ai_daily_budget(budget_day) VALUES(?)",day);long used=m.jdbc().queryForObject("SELECT used_microusd FROM practice_ai_daily_budget WHERE budget_day=? FOR UPDATE",Long.class,day);if(used+50000>config.getDailyBudgetMicrousd())throw error(429,"PRACTICE_AI_BUDGET","今日AI可用额度不足，部分额度可能仍在预留或待确认");m.jdbc().update("UPDATE practice_ai_daily_budget SET used_microusd=used_microusd+50000 WHERE budget_day=?",day);
  long created=m.insert("INSERT INTO practice_subjective_review(user_id,subjective_attempt_id,paper_attempt_id,slot,scope_hash,source_json,response_hash,status,consent_version,model,prompt_version,rubric_version,created_at,budget_day,reserved_microusd) VALUES(?,?,?,?,?,?,?,'QUEUED',?,?,'subjective-review-v4','learning-15-v1',?,?,50000)",uid,"SUBJECTIVE".equals(source.sourceType())?Long.valueOf(source.attemptId()):null,"PAPER".equals(source.sourceType())?Long.valueOf(source.attemptId()):null,source.slot(),scope,m.json().writeValueAsString(source),hashes.hash(source.text()),in.consentVersion(),config.getModel(),java.sql.Timestamp.from(now),day);
  return remember(uid,in,hash,created);
 }
 private ReviewDto remember(long uid,CreateReview in,String hash,long id){m.jdbc().update("INSERT INTO practice_subjective_review_request(user_id,request_id,request_hash,review_id) VALUES(?,?,?,?)",uid,in.requestId(),hash,id);return m.dto(uid,id);}
}
