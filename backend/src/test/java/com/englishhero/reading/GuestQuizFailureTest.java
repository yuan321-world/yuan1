package com.englishhero.reading;

import com.englishhero.reading.quiz.*;
import com.englishhero.identity.token.AuthProperties;
import com.englishhero.web.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class GuestQuizFailureTest {
 @Test void redisFailureNeverFallsBackToUncheckedStartOrGrading(){
  var redis=mock(StringRedisTemplate.class);var properties=new AuthProperties();properties.setNamespace("eh:test:auth:v1");
  when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("internal redis detail"));
  var quizzes=mock(QuizService.class);var mapper=mock(QuizMapper.class);var service=new GuestQuizService(quizzes,mapper,mock(ArticleMapper.class),redis,properties);
  assertThatThrownBy(()->service.start(null,"1","2",new QuizDtos.GuestStart("3"),"127.0.0.1")).isInstanceOfSatisfying(BusinessException.class,e->{assertThat(e.status).isEqualTo(503);assertThat(e.code).isEqualTo("QUIZ_SERVICE_UNAVAILABLE");});
  assertThatThrownBy(()->service.submit(new QuizDtos.GuestSubmit("a".repeat(43),"1","A"),"127.0.0.1")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status).isEqualTo(503));verifyNoInteractions(quizzes,mapper);
 }
}
