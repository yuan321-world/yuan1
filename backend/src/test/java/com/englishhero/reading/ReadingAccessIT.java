package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ReadingAccessIT extends ReadingHttpITSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ArticleMapper articles;
    @Test void concurrentStartReadsTheLatestOwnedProgressAfterWaitingForTheArticleLock()throws Exception {
        var admin=actor(true);var learner=actor(false);var second=anonymous();access.put(second,access.get(learner));
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("Concurrent start")).body()).get("id").asText();call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var held=new java.util.concurrent.CountDownLatch(1);var secondEntered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation->{int n=calls.incrementAndGet();if(n==2)secondEntered.countDown();var value=invocation.callRealMethod();if(n==1){held.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Start gate timed out");}return value;}).when(articles).article(Long.parseLong(id),true);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->call(learner,"POST","/reading/articles/"+id+"/start",null).statusCode());assertThat(held.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var two=pool.submit(()->call(second,"POST","/reading/articles/"+id+"/start",null).statusCode());assertThat(secondEntered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();release.countDown();
            assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(201,200);
        }finally{release.countDown();org.mockito.Mockito.reset(articles);}
    }
    @Test void startUsesANewlyPublishedSnapshotEvenWhenIdentityWasReadEarlier()throws Exception {
        var admin=actor(true);var learner=actor(false);var input=draft("Before publication");String id=json.readTree(call(admin,"POST","/admin/articles",input).body()).get("id").asText();call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation->{if(first.compareAndSet(true,false)){entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Publish gate timed out");}return invocation.callRealMethod();}).when(articles).article(Long.parseLong(id),true);
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var start=pool.submit(()->call(learner,"POST","/reading/articles/"+id+"/start",null));assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var changed=new ReadingDtos.DraftUpdate(input.categoryId(),"After publication",input.titleZh(),input.summary(),input.difficulty(),input.body(),input.author(),input.sourceType(),input.sourceUrl(),input.usageNote(),1);
            assertThat(call(admin,"PUT","/admin/articles/"+id+"/draft",changed).statusCode()).isEqualTo(200);assertThat(call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(2)).statusCode()).isEqualTo(200);release.countDown();
            var result=start.get();assertThat(result.statusCode()).isEqualTo(201);assertThat(json.readTree(result.body()).get("version").get("title").asText()).isEqualTo("After publication");
        }finally{release.countDown();org.mockito.Mockito.reset(articles);}
    }
    @Test void startAndUnpublishRespectBothTransactionLockOrders()throws Exception {
        for(boolean startFirst:new boolean[]{true,false}) {
            var admin=actor(true);var learner=actor(false);
            var a=json.readTree(call(admin,"POST","/admin/articles",draft("Lock order")).body());String id=a.get("id").asText();
            assertThat(call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0)).statusCode()).isEqualTo(200);
            call(learner,"GET","/auth/csrf",null);
            var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            org.mockito.Mockito.doAnswer(invocation->{
                var row=invocation.callRealMethod();
                if(Boolean.TRUE.equals(invocation.getArgument(1)) && first.compareAndSet(true,false)){
                    locked.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Lock-order test gate timed out");
                }
                return row;
            }).when(articles).article(Long.parseLong(id),true);
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Integer> start=()->call(learner,"POST","/reading/articles/"+id+"/start",null).statusCode();
                java.util.concurrent.Callable<Integer> unpublish=()->call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1)).statusCode();
                var one=pool.submit(startFirst?start:unpublish);
                assertThat(locked.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var two=pool.submit(startFirst?unpublish:start);release.countDown();
                int firstStatus=one.get(20,java.util.concurrent.TimeUnit.SECONDS),secondStatus=two.get(20,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(startFirst?firstStatus:secondStatus).isEqualTo(startFirst?201:409);
                assertThat(startFirst?secondStatus:firstStatus).isEqualTo(200);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress WHERE article_id=?",Integer.class,id)).isEqualTo(startFirst?1:0);
            } finally { release.countDown();org.mockito.Mockito.reset(articles); }
        }
    }
    @Test void publicationCreatesOnlyCurrentUsersHistoryAndDraftsStayPrivate()throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var a=json.readTree(call(admin,"POST","/admin/articles",draft("A fresh page")).body());String id=a.get("id").asText(),vid=a.get("draftVersionId").asText();
        assertThat(call(learner,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(404);
        assertThat(call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0)).statusCode()).isEqualTo(200);
        var list=call(learner,"GET","/reading/articles?page=1&pageSize=20",null);assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body()).get("items").size()).isGreaterThan(0);
        var start=call(learner,"POST","/reading/articles/"+id+"/start",null);assertThat(start.statusCode()).isEqualTo(201);
        var again=call(learner,"POST","/reading/articles/"+id+"/start",null);assertThat(again.statusCode()).isEqualTo(200);
        assertThat(json.readTree(start.body()).get("progress").get("id").asText()).isEqualTo(json.readTree(again.body()).get("progress").get("id").asText());
        assertThat(call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1)).statusCode()).isEqualTo(200);
        assertThat(call(learner,"POST","/reading/articles/"+id+"/start",null).statusCode()).isEqualTo(409);
        assertThat(call(learner,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(200);
        assertThat(call(other,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(404);
    }
}
