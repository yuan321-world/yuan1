package com.englishhero.vocabulary;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.identity.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;

@Service
public class WordDifficultyService {
 public record State(boolean difficult,int attempts,int assistedAttempts,Boolean manualOverride){}
 public record Input(Boolean difficult){}
 private final JdbcTemplate jdbc;private final IdentityService identities;
 public WordDifficultyService(JdbcTemplate jdbc,IdentityService identities){this.jdbc=jdbc;this.identities=identities;}
 public static final String ATTEMPTS="WITH attempts AS (SELECT i.learning_word_id wid,e.item_id,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.snapshot,'$.attemptId')),'0') attempt_key,MAX(CASE WHEN e.correct=FALSE THEN 1 ELSE 0 END) failed,MAX(e.id) last_id FROM vocabulary_learning_event e JOIN vocabulary_learning_item i ON i.id=e.item_id AND i.user_id=e.user_id JOIN vocabulary_learning_session s ON s.id=e.session_id AND s.user_id=e.user_id WHERE e.user_id=? AND s.flow_version='v2' AND e.action IN ('HINT','ERROR','UNKNOWN','SPELLING','KNOWN','CHOICE') GROUP BY i.learning_word_id,e.item_id,attempt_key),ranked AS (SELECT *,ROW_NUMBER() OVER(PARTITION BY wid ORDER BY last_id DESC) rn FROM attempts) ";
 public Map<Long,State> states(long user){
  var attempts=new HashMap<Long,int[]>();jdbc.query(ATTEMPTS+"SELECT wid,COUNT(*),SUM(failed) FROM ranked WHERE rn<=5 GROUP BY wid",r->{attempts.put(r.getLong(1),new int[]{r.getInt(2),r.getInt(3)});},user);
  var result=new HashMap<Long,State>();jdbc.query("SELECT id,difficulty_override FROM vocabulary_learning_word WHERE user_id=?",r->{int[] a=attempts.getOrDefault(r.getLong(1),new int[]{0,0});Boolean override=r.getObject(2)==null?null:r.getBoolean(2);result.put(r.getLong(1),new State(override==null?a[1]>=3:override,a[0],a[1],override));},user);return result;
 }
 @Transactional(readOnly=true) public State state(SessionPrincipal actor,String raw){identities.current(actor);long id=ArticleRules.id(raw,"id");var state=states(actor.userId()).get(id);if(state==null)throw new BusinessException(404,"LEARNING_NOT_FOUND","学习记录不存在或不属于当前账号");return state;}
 @Transactional public State update(SessionPrincipal actor,String raw,Input input){identities.current(actor);long id=ArticleRules.id(raw,"id");if(input==null)throw BusinessException.invalid("difficult","请选择难词状态");jdbc.queryForObject("SELECT id FROM identity_user WHERE id=? FOR UPDATE",Long.class,actor.userId());if(jdbc.update("UPDATE vocabulary_learning_word SET difficulty_override=? WHERE user_id=? AND id=?",input.difficult(),actor.userId(),id)==0)throw new BusinessException(404,"LEARNING_NOT_FOUND","学习记录不存在或不属于当前账号");return states(actor.userId()).get(id);}
}
