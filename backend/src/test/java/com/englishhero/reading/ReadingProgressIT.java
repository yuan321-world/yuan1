package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class ReadingProgressIT extends ReadingHttpITSupport {
    @Test void concurrentWritesAndHistoricalSnapshotsRemainIsolated()throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var input=draft("Original snapshot");
        var withEmoji=new ReadingDtos.DraftInput(input.categoryId(),input.title(),input.titleZh(),input.summary(),input.difficulty(),"A😀 step matters.",input.author(),input.sourceType(),input.sourceUrl(),input.usageNote());
        var a=json.readTree(call(admin,"POST","/admin/articles",withEmoji).body());String id=a.get("id").asText(),vid=a.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));call(learner,"POST","/reading/articles/"+id+"/start",null);
        assertThat(call(learner,"PUT","/reading/progress/"+vid,new ReadingDtos.ProgressUpdate("p0001-s0001",2,false,0)).statusCode()).isEqualTo(400);
        // Separate authenticated HTTP clients avoid sharing a CSRF cookie mutation across threads.
        var second=anonymous();access.put(second,access.get(learner));
        var update=new ReadingDtos.ProgressUpdate("p0001-s0001",16,false,0);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->call(learner,"PUT","/reading/progress/"+vid,update).statusCode());
            var next=pool.submit(()->call(second,"PUT","/reading/progress/"+vid,update).statusCode());
            assertThat(List.of(first.get(),next.get())).containsExactlyInAnyOrder(200,409);
        }
        var changed=new ReadingDtos.DraftUpdate(input.categoryId(),"Revised snapshot",input.titleZh(),input.summary(),input.difficulty(),input.body(),input.author(),input.sourceType(),input.sourceUrl(),input.usageNote(),1);
        assertThat(call(admin,"PUT","/admin/articles/"+id+"/draft",changed).statusCode()).isEqualTo(200);
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(2));
        assertThat(call(learner,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(200);
        assertThat(call(other,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(404);
        call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(3));
        assertThat(call(learner,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(200);
        var history=json.readTree(call(learner,"GET","/reading/history",null).body());
        assertThat(history.get("items").get(0).get("article").get("title").asText()).isEqualTo("Original snapshot");
    }
    @Test void progressIsOwnedOptimisticAndCompletionCannotBeUndoneByScrolling()throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var a=json.readTree(call(admin,"POST","/admin/articles",draft("Read and return")).body());String id=a.get("id").asText(),vid=a.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        call(learner,"POST","/reading/articles/"+id+"/start",null);
        var update=Map.of("sentenceKey","p0001-s0001","offset",0,"completed",true,"expectedLockVersion",0);
        assertThat(call(other,"PUT","/reading/progress/"+vid,update).statusCode()).isEqualTo(404);
        var saved=call(learner,"PUT","/reading/progress/"+vid,update);assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(json.readTree(saved.body()).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(call(learner,"PUT","/reading/progress/"+vid,update).statusCode()).isEqualTo(409);
        var scroll=call(learner,"PUT","/reading/progress/"+vid,Map.of("sentenceKey","p0001-s0001","offset",3,"completed",false,"expectedLockVersion",1));
        assertThat(scroll.statusCode()).isEqualTo(200);assertThat(json.readTree(scroll.body()).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(call(learner,"PUT","/reading/progress/"+vid,Map.of("sentenceKey","unknown","offset",0,"completed",false,"expectedLockVersion",2)).statusCode()).isEqualTo(400);
        assertThat(call(learner,"PUT","/reading/progress/"+vid,Map.of("sentenceKey","p0001-s0001","offset",9000,"completed",false,"expectedLockVersion",2)).statusCode()).isEqualTo(400);
        assertThat(json.readTree(call(learner,"GET","/reading/history",null).body()).get("items").size()).isEqualTo(1);
        assertThat(json.readTree(call(other,"GET","/reading/history",null).body()).get("items").size()).isZero();
    }
}
