package com.englishhero.reading.translation;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import com.englishhero.practice.review.*;
import static org.mockito.Mockito.*;
class BaiduTranslationWorkerTest {
 @Test void translationRunsWithoutDeepSeekCredentialsAndNeverFallsBack(){
  var jobs=mock(SelectionTranslationJobs.class);var deepseek=mock(DeepSeekReviewClient.class);var baidu=mock(BaiduTranslationClient.class);var config=new PracticeReviewProperties();
  var now=Instant.now();var job=new SelectionTranslationDtos.Job(1,2,"two words","lease",LocalDate.now(),0,now,now.plusSeconds(60),"RUNNING","TRANSLATION",BaiduTranslationProperties.MODEL,9,LocalDate.now().withDayOfMonth(1));
  when(jobs.claim()).thenReturn(Optional.of(job));var failure=new BaiduTranslationClient.Failure("BAIDU_54003",true);when(baidu.translate("two words")).thenThrow(failure);
  var worker=new SelectionTranslationWorker(jobs,deepseek,config,baidu);try{worker.tick();verify(jobs,timeout(2000)).failBaidu(job,failure);verifyNoInteractions(deepseek);}finally{worker.close();}
 }
}
