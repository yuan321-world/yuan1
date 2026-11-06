package com.englishhero.reading;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ReadingToolsIT extends ReadingHttpITSupport {
    @Test void publicToolsRespectPublicationAndVersionOwnership() throws Exception {
        var admin=actor(true);var guest=anonymous();var learner=actor(false);
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("Reading tools sample")).body()).get("id").asText();
        var row=json.readTree(call(admin,"GET","/admin/articles/"+id,null).body());
        String version=row.get("draft").get("id").asText();
        String path="/reading/articles/"+id+"/versions/"+version+"/tools";
        assertThat(call(guest,"GET",path,null).statusCode()).isEqualTo(404);
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var result=call(guest,"GET",path,null);assertThat(result.statusCode()).isEqualTo(200);
        String neighbor=json.readTree(call(admin,"POST","/admin/articles",draft("A related published article")).body()).get("id").asText();
        call(admin,"POST","/admin/articles/"+neighbor+"/publish",new ReadingDtos.LockInput(0));
        String hidden=json.readTree(call(admin,"POST","/admin/articles",draft("A related draft")).body()).get("id").asText();
        var expanded=json.readTree(call(guest,"GET",path,null).body());
        var related=new java.util.ArrayList<String>();for(var item:expanded.get("related"))related.add(item.get("id").asText());
        assertThat(related).contains(neighbor).doesNotContain(id,hidden);
        assertThat(expanded.get("next").get("id").asText()).isEqualTo(neighbor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress WHERE article_id=?",Integer.class,id)).isZero();
        assertThat(json.readTree(result.body()).get("keywords").isArray()).isTrue();
        assertThat(json.readTree(result.body()).get("translation").get("available").asBoolean()).isFalse();
        assertThat(call(guest,"GET",path.replace("/tools","/words"),null).statusCode()).isEqualTo(401);
        call(learner,"POST","/reading/articles/"+id+"/start",null);
        call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1));
        assertThat(call(guest,"GET",path,null).statusCode()).isEqualTo(404);
        assertThat(call(learner,"GET",path,null).statusCode()).isEqualTo(200);
    }
    @Test void versionWordsOnlyIncludeOwnedSourcesAndRejectUnownedVersions() throws Exception {
        var admin=actor(true);var one=actor(false);var two=actor(false);
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("Personal words sample")).body()).get("id").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var started=json.readTree(call(one,"POST","/reading/articles/"+id+"/start",null).body());
        String version=started.get("version").get("id").asText();
        String path="/reading/articles/"+id+"/versions/"+version+"/words?pageSize=1";
        assertThat(call(two,"GET",path,null).statusCode()).isEqualTo(404);
        var entry=json.readTree(call(one,"GET","/dictionary/lookup?word=small",null).body()).get("candidates").get(0).get("entryId").asText();
        var input=Map.of("articleVersionId",version,"sentenceKey","p0001-s0001","startOffset",0,"endOffset",5,"entryId",entry);
        assertThat(call(one,"POST","/vocabulary/words",input).statusCode()).isEqualTo(201);
        call(one,"POST","/vocabulary/words",input);
        var result=json.readTree(call(one,"GET",path,null).body());assertThat(result.get("total").asInt()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("source").get("surfaceForm").asText()).isEqualTo("Small");
        assertThat(result.get("items").get(0).get("word").get("lemma").asText()).isEqualTo("small");
        call(two,"POST","/reading/articles/"+id+"/start",null);
        assertThat(json.readTree(call(two,"GET",path,null).body()).get("total").asInt()).isZero();
    }
}
