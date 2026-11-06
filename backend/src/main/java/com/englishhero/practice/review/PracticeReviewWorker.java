package com.englishhero.practice.review;
import java.util.concurrent.*;import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.*;import jakarta.annotation.PreDestroy;
@Component @EnableScheduling
public class PracticeReviewWorker {
 private final PracticeReviewBudget jobs;private final DeepSeekReviewClient client;private final PracticeReviewProperties config;
 private final Semaphore capacity=new Semaphore(2);private final ExecutorService executor=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"practice-ai-review");t.setDaemon(true);return t;});
 public PracticeReviewWorker(PracticeReviewBudget jobs,DeepSeekReviewClient client,PracticeReviewProperties config){this.jobs=jobs;this.client=client;this.config=config;}
 @Scheduled(fixedDelay=1000) public void tick(){
  if(!config.isWorkerEnabled())return;
  try{jobs.recover();if(!config.ready())return;
   while(capacity.tryAcquire()){
    boolean handedOff=false;
    try{var job=jobs.claimNext();if(job.isEmpty())break;
     executor.execute(()->{var j=job.get();try{jobs.complete(j,client.review(j.source()));}catch(DeepSeekReviewClient.Failure e){jobs.fail(j,e);}catch(Exception e){jobs.fail(j,"OUTCOME_UNKNOWN");}finally{capacity.release();}});
     handedOff=true;
    }finally{if(!handedOff)capacity.release();}
   }
  }catch(Exception e){/* Retry DB discovery next tick; never log request contents. */}
 }
 @PreDestroy void close(){executor.shutdownNow();}
}
