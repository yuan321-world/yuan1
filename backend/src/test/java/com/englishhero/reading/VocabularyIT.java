package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class VocabularyIT extends ReadingHttpITSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.englishhero.vocabulary.VocabularyMapper words;
    @Test void duplicateCollectionAndCancelShareAnOrderWithoutLeakingCachedState()throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var a=json.readTree(call(admin,"POST","/admin/articles",draft("Concurrent collection")).body());String id=a.get("id").asText(),vid=a.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));call(learner,"POST","/reading/articles/"+id+"/start",null);
        String entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='step'",String.class);
        var input=Map.of("articleVersionId",vid,"sentenceKey","p0001-s0001","startOffset",6,"endOffset",11,"entryId",entry);
        var second=anonymous();access.put(second,access.get(learner));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->call(learner,"POST","/vocabulary/words",input).statusCode());var two=pool.submit(()->call(second,"POST","/vocabulary/words",input).statusCode());
            assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(201,200);
        }
        var own=json.readTree(call(learner,"GET","/dictionary/lookup?word=steps",null).body()).get("candidates").get(0).get("collection");
        String word=own.get("wordId").asText();long owner=jdbc.queryForObject("SELECT user_id FROM user_word WHERE id=?",Long.class,word);
        assertThat(own.get("active").asBoolean()).isTrue();
        assertThat(json.readTree(call(other,"GET","/dictionary/lookup?word=steps",null).body()).get("candidates").get(0).get("collection").isNull()).isTrue();
        for(boolean cancelFirst:new boolean[]{true,false}){
            var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            org.mockito.Mockito.doAnswer(invocation->{invocation.callRealMethod();if(first.compareAndSet(true,false)){held.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Collection gate timeout");}return null;}).when(words).lockOwner(owner);
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
                java.util.concurrent.Callable<Integer> cancel=()->call(learner,"DELETE","/vocabulary/words/"+word,null).statusCode();
                java.util.concurrent.Callable<Integer> collect=()->call(second,"POST","/vocabulary/words",input).statusCode();
                var one=pool.submit(cancelFirst?cancel:collect);assertThat(held.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var two=pool.submit(cancelFirst?collect:cancel);release.countDown();
                assertThat(one.get()).isEqualTo(cancelFirst?204:200);assertThat(two.get()).isEqualTo(cancelFirst?200:204);
                assertThat(jdbc.queryForObject("SELECT active FROM user_word WHERE id=?",Boolean.class,word)).isEqualTo(cancelFirst);
            }finally{release.countDown();org.mockito.Mockito.reset(words);}
        }
        call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1));
        assertThat(call(learner,"POST","/vocabulary/words",input).statusCode()).isEqualTo(200);
        assertThat(call(other,"POST","/vocabulary/words",input).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_word_source WHERE word_id=?",Long.class,word)).isEqualTo(1);
    }
    @Test void collectingValidatesServerTextAndPreservesOwnedSources()throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var a=json.readTree(call(admin,"POST","/admin/articles",draft("Remember a step")).body());String id=a.get("id").asText(),vid=a.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        String entry=jdbc.queryForObject("SELECT id FROM dictionary_entry WHERE lemma='step'",String.class);
        var input=Map.of("articleVersionId",vid,"sentenceKey","p0001-s0001","startOffset",6,"endOffset",11,"entryId",entry);
        assertThat(call(learner,"POST","/vocabulary/words",input).statusCode()).isEqualTo(404);
        call(learner,"POST","/reading/articles/"+id+"/start",null);
        var created=call(learner,"POST","/vocabulary/words",input);assertThat(created.statusCode()).isEqualTo(201);
        String word=json.readTree(created.body()).get("word").get("id").asText();
        assertThat(call(learner,"POST","/vocabulary/words",input).statusCode()).isEqualTo(200);
        var invalid=new HashMap<String,Object>(input);invalid.put("endOffset",10);
        assertThat(call(learner,"POST","/vocabulary/words",invalid).statusCode()).isEqualTo(400);
        invalid=new HashMap<>(input);invalid.put("userId","1");assertThat(call(learner,"POST","/vocabulary/words",invalid).statusCode()).isEqualTo(400);
        assertThat(call(other,"GET","/vocabulary/words/"+word,null).statusCode()).isEqualTo(404);
        assertThat(call(other,"DELETE","/vocabulary/words/"+word,null).statusCode()).isEqualTo(404);
        assertThat(json.readTree(call(learner,"GET","/vocabulary/words/"+word,null).body()).get("sources").get("total").asInt()).isEqualTo(1);
        assertThat(call(learner,"DELETE","/vocabulary/words/"+word,null).statusCode()).isEqualTo(204);
        assertThat(json.readTree(call(learner,"GET","/vocabulary/words",null).body()).get("total").asInt()).isZero();
        var restored=call(learner,"POST","/vocabulary/words",input);assertThat(restored.statusCode()).isEqualTo(200);
        assertThat(json.readTree(restored.body()).get("word").get("id").asText()).isEqualTo(word);
    }
}
