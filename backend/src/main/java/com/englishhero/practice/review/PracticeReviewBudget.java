package com.englishhero.practice.review;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import static com.englishhero.practice.review.PracticeReviewDtos.*;
@Service
public class PracticeReviewBudget {
 private final PracticeReviewMapper m;private final Clock clock;
 public PracticeReviewBudget(PracticeReviewMapper m,Clock clock){this.m=m;this.clock=clock;}
 public static boolean knownUsage(Long input,Long output){return input!=null&&output!=null&&input>=0&&output>=0&&input<=90000&&output<=8000;}
 public static long cost(Long input,Long output){if(input==null||output==null||input<0||output<0||input>90000||output>8000)return 50000;return (input*3+output*12+9)/10;}
 @Transactional(isolation=Isolation.READ_COMMITTED) public void recover(){
  Instant now=clock.instant();var expired=m.jdbc().query("SELECT * FROM practice_subjective_review WHERE (status='RUNNING' AND lease_until<?) OR (status='QUEUED' AND created_at<?) FOR UPDATE SKIP LOCKED",m.jobs(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.minusSeconds(600)));
  for(var j:expired){boolean unsent="QUEUED".equals(j.status());m.jdbc().update("UPDATE practice_subjective_review SET status='FAILED',error_code=?,completed_at=?,settled_microusd=? WHERE id=?",unsent?"QUEUE_EXPIRED":"OUTCOME_UNKNOWN",java.sql.Timestamp.from(now),unsent?0:j.reserve(),j.id());if(unsent)m.jdbc().update("UPDATE practice_ai_daily_budget SET used_microusd=used_microusd-? WHERE budget_day=?",j.reserve(),j.day());}
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public Optional<PracticeReviewMapper.Job> claimNext(){
  // A fixed sentinel budget row serializes only the short global claim section across processes.
  m.jdbc().update("INSERT IGNORE INTO practice_ai_daily_budget(budget_day) VALUES('1970-01-01')");m.jdbc().queryForObject("SELECT used_microusd FROM practice_ai_daily_budget WHERE budget_day='1970-01-01' FOR UPDATE",Long.class);
  if(m.jdbc().queryForObject("SELECT COUNT(*) FROM practice_subjective_review WHERE status='RUNNING'",Long.class)>=2)return Optional.empty();
  var jobs=m.jdbc().query("SELECT * FROM practice_subjective_review WHERE status='QUEUED' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",m.jobs());if(jobs.isEmpty())return Optional.empty();var j=jobs.getFirst();String lease=UUID.randomUUID().toString();
  m.jdbc().update("UPDATE practice_subjective_review SET status='RUNNING',lease_token=?,lease_until=? WHERE id=? AND status='QUEUED'",lease,java.sql.Timestamp.from(clock.instant().plusSeconds(180)),j.id());return m.jdbc().query("SELECT * FROM practice_subjective_review WHERE id=?",m.jobs(),j.id()).stream().findFirst();
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public void complete(PracticeReviewMapper.Job j,ProviderResponse response){
  var rows=m.jdbc().query("SELECT * FROM practice_subjective_review WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>? FOR UPDATE",m.jobs(),j.id(),j.lease(),java.sql.Timestamp.from(clock.instant()));if(rows.isEmpty())return;
  long settled=cost(response.inputTokens(),response.outputTokens());
  m.jdbc().update("UPDATE practice_subjective_review SET status='SUCCEEDED',result_json=?,model=?,input_tokens=?,output_tokens=?,settled_microusd=?,completed_at=? WHERE id=?",m.json().writeValueAsString(response.result()),response.model(),response.inputTokens(),response.outputTokens(),settled,java.sql.Timestamp.from(clock.instant()),j.id());m.jdbc().update("UPDATE practice_ai_daily_budget SET used_microusd=used_microusd-?+? WHERE budget_day=?",j.reserve(),settled,j.day());
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public void fail(PracticeReviewMapper.Job j,String code){settleFailure(j,new DeepSeekReviewClient.Failure(code));}
 @Transactional(isolation=Isolation.READ_COMMITTED) public void fail(PracticeReviewMapper.Job j,DeepSeekReviewClient.Failure failure){settleFailure(j,failure);}
 private void settleFailure(PracticeReviewMapper.Job j,DeepSeekReviewClient.Failure f){
  var rows=m.jdbc().query("SELECT * FROM practice_subjective_review WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>? FOR UPDATE",m.jobs(),j.id(),j.lease(),java.sql.Timestamp.from(clock.instant()));if(rows.isEmpty())return;
  long settled=f.definitelyUnsent?0:knownUsage(f.inputTokens,f.outputTokens)?cost(f.inputTokens,f.outputTokens):j.reserve();
  m.jdbc().update("UPDATE practice_subjective_review SET status='FAILED',error_code=?,input_tokens=?,output_tokens=?,settled_microusd=?,completed_at=? WHERE id=?",f.code,f.inputTokens,f.outputTokens,settled,java.sql.Timestamp.from(clock.instant()),j.id());
  m.jdbc().update("UPDATE practice_ai_daily_budget SET used_microusd=used_microusd-?+? WHERE budget_day=?",j.reserve(),settled,j.day());
 }
}
