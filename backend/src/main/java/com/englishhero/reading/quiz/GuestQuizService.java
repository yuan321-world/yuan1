package com.englishhero.reading.quiz;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.identity.token.AuthProperties;
import com.englishhero.reading.ArticleRules;
import com.englishhero.reading.ArticleMapper;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.web.BusinessException;
import static com.englishhero.reading.quiz.QuizDtos.*;

@Service
public class GuestQuizService {
 private final QuizService quizzes;private final QuizMapper mapper;private final ArticleMapper articles;private final StringRedisTemplate redis;private final String prefix;
 private final SecureRandom random=new SecureRandom();
 private static final DefaultRedisScript<Long> LIMIT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end;return n",Long.class);
 private static final DefaultRedisScript<Long> START=new DefaultRedisScript<>("redis.call('HSET',KEYS[1],'version',ARGV[1],'position','0');redis.call('EXPIRE',KEYS[1],600);return 1",Long.class);
 private static final DefaultRedisScript<Long> SUBMIT=new DefaultRedisScript<>("if redis.call('EXISTS',KEYS[1])==0 then return -1 end;local old=redis.call('HGET',KEYS[1],ARGV[1]);if old then if old==ARGV[2] then return 1 else return -2 end end;if redis.call('HGET',KEYS[1],'position')~=ARGV[3] then return -3 end;redis.call('HSET',KEYS[1],ARGV[1],ARGV[2]);redis.call('HINCRBY',KEYS[1],'position',1);return 0",Long.class);
 public GuestQuizService(QuizService quizzes,QuizMapper mapper,ArticleMapper articles,StringRedisTemplate redis,AuthProperties p){this.quizzes=quizzes;this.mapper=mapper;this.articles=articles;this.redis=redis;prefix=p.getNamespace()+":reading-guest:";}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void limit(String ip,String scope,int seconds,int maximum){Long count=redis.execute(LIMIT,List.of(prefix+"rate:"+scope+":"+hash(ip)),""+seconds);if(count==null||count>maximum)throw new BusinessException(429,"QUIZ_RATE_LIMIT","请求较多，请稍后再试");}
 @Transactional public GuestSession start(SessionPrincipal actor,String a,String v,GuestStart input,String ip){
  try{
   limit(ip,"start",3600,10);long aid=ArticleRules.id(a,"articleId"),vid=ArticleRules.id(v,"versionId");articles.article(aid,true);mapper.article(aid,vid,true);var quiz=quizzes.published(actor,a,v);
   if(quiz==null)throw new BusinessException(404,"QUIZ_UNAVAILABLE","本篇暂无已发布练习");
   if(input==null||!quiz.versionId().equals(input.quizVersionId()))throw new BusinessException(409,"QUIZ_CHANGED","题组已更新，请重新加载练习");
   byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);redis.execute(START,List.of(prefix+"session:"+hash(token)),quiz.versionId());return new GuestSession(token,600);
  }catch(DataAccessException e){throw unavailable();}
 }
 public Feedback submit(GuestSubmit input,String ip){
  if(input==null||input.token()==null||!input.token().matches("[A-Za-z0-9_-]{43}"))throw expired();
  long id=ArticleRules.id(input.questionId(),"questionId");if(input.choice()==null||!input.choice().matches("[A-D]"))throw BusinessException.invalid("choice","请选择一个选项");
  try{
   limit(ip,"submit",60,120);String key=prefix+"session:"+hash(input.token());Object stored=redis.opsForHash().get(key,"version");if(stored==null)throw expired();
   var questions=mapper.questions(Long.parseLong(stored.toString()));int index=-1;for(int i=0;i<questions.size();i++)if(questions.get(i).id()==id)index=i;
   if(index<0)throw new BusinessException(404,"QUIZ_QUESTION_NOT_FOUND","题目不属于本次体验");
   Long result=redis.execute(SUBMIT,List.of(key),"a:"+id,input.choice(),""+index);
   if(result==null||result==-1)throw expired();if(result<0)throw new BusinessException(409,"QUIZ_SUBMISSION_CONFLICT",result==-2?"本题已提交，不能修改答案":"请先完成当前题目");
   var q=questions.get(index);var answers=redis.opsForHash().entries(key);if(answers.isEmpty())throw expired();
   int correct=0;for(var question:questions)if(question.answer().equals(answers.get("a:"+question.id())))correct++;
   boolean completed=Integer.parseInt(answers.get("position").toString())==questions.size();
   // Count is revealed only at completion; concurrent retries cannot expose other questions' results.
   return new Feedback(""+id,input.choice(),q.answer().equals(input.choice()),q.answer(),q.explanation(),completed,completed?correct:0,questions.size());
  }catch(DataAccessException e){throw unavailable();}
 }
 private BusinessException expired(){return new BusinessException(410,"QUIZ_SESSION_EXPIRED","体验已过期，请重新开始；当前选择仍保留");}
 private BusinessException unavailable(){return new BusinessException(503,"QUIZ_SERVICE_UNAVAILABLE","练习服务暂不可用，请稍后重试");}
}
