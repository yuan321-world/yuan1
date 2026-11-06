package com.englishhero.practice.review;
import static org.mockito.Mockito.*;
import java.util.Optional;import org.junit.jupiter.api.Test;import org.springframework.dao.DataAccessResourceFailureException;
class PracticeReviewWorkerTest {
 @Test void transientClaimFailuresNeverConsumeWorkerCapacity(){
  var jobs=mock(PracticeReviewBudget.class);var client=mock(DeepSeekReviewClient.class);var config=new PracticeReviewProperties();config.setEnabled(true);config.setApiKey("stub-only");
  when(jobs.claimNext()).thenThrow(new DataAccessResourceFailureException("synthetic")).thenThrow(new DataAccessResourceFailureException("synthetic")).thenReturn(Optional.empty());
  var worker=new PracticeReviewWorker(jobs,client,config);try{worker.tick();worker.tick();worker.tick();verify(jobs,times(3)).claimNext();verifyNoInteractions(client);}finally{worker.close();}
 }
}
