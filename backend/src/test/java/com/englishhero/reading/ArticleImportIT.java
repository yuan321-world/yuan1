package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class ArticleImportIT extends ReadingHttpITSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ArticleMapper articles;
    @Test void validationIsReadOnlyAndConfirmationIsAtomicAndIdempotent()throws Exception {
        var admin=actor(true);var other=actor(true);var learner=actor(false);
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class);
        var payload=Map.of("schemaVersion",1,"articles",List.of(draft("Import one"),draft("Import two")));
        var validate=call(admin,"POST","/admin/articles/import/validate",payload);
        assertThat(validate.statusCode()).isEqualTo(200);assertThat(json.readTree(validate.body()).get("valid").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before);
        var invalid=Map.of("schemaVersion",1,"articles",List.of(draft("Valid"),draft("")));
        assertThat(json.readTree(call(admin,"POST","/admin/articles/import/validate",invalid).body()).get("valid").asBoolean()).isFalse();
        assertThat(call(admin,"POST","/admin/articles/import/confirm?clientImportId="+UUID.randomUUID(),invalid).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before);
        assertThat(call(learner,"POST","/admin/articles/import/validate",payload).statusCode()).isEqualTo(403);
        assertThat(call(admin,"POST","/admin/articles/import/validate",Map.of("schemaVersion",2,"articles",List.of(draft("Bad schema")))).statusCode()).isEqualTo(200);
        assertThat(json.readTree(call(admin,"POST","/admin/articles/import/validate",Map.of("schemaVersion",1,"articles",Collections.nCopies(21,draft("Too many")))).body()).get("valid").asBoolean()).isFalse();
        String clientId=UUID.randomUUID().toString();var second=anonymous();access.put(second,access.get(admin));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->call(admin,"POST","/admin/articles/import/confirm?clientImportId="+clientId,payload));
            var two=pool.submit(()->call(second,"POST","/admin/articles/import/confirm?clientImportId="+clientId,payload));
            var first=one.get();var next=two.get();assertThat(first.statusCode()).isIn(200,201);assertThat(next.statusCode()).isIn(200,201);
            assertThat(json.readTree(first.body())).isEqualTo(json.readTree(next.body()));
            for(var id:json.readTree(first.body()).get("articleIds"))assertThat(jdbc.queryForObject("SELECT status FROM reading_article WHERE id=?",String.class,id.asText())).isEqualTo("DRAFT");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before+2);
        var conflict=call(other,"POST","/admin/articles/import/confirm?clientImportId="+clientId,payload);assertThat(conflict.statusCode()).isEqualTo(409);assertThat(conflict.body()).doesNotContain("articleIds");
        assertThat(call(admin,"POST","/admin/articles/import/confirm?clientImportId="+clientId,Map.of("schemaVersion",1,"articles",List.of(draft("Different")))).statusCode()).isEqualTo(409);
        var count=new java.util.concurrent.atomic.AtomicInteger();org.mockito.Mockito.doAnswer(invocation->{if(count.incrementAndGet()==2)throw new IllegalStateException("fixture insert failure");return invocation.callRealMethod();}).when(articles).initialDraft(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong());
        String failed=UUID.randomUUID().toString();
        try{assertThat(call(admin,"POST","/admin/articles/import/confirm?clientImportId="+failed,payload).statusCode()).isEqualTo(500);}finally{org.mockito.Mockito.reset(articles);}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before+2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_import_batch WHERE client_import_id=?",Long.class,failed)).isZero();
    }
    @Test void byteLimitIncludesChunkedBodiesAndMalformedPayloads()throws Exception {
        var admin=actor(true);String valid=json.writeValueAsString(Map.of("schemaVersion",1,"articles",List.of(draft("Limit"))));
        byte[] validBytes=valid.getBytes(StandardCharsets.UTF_8);byte[] exact=new byte[1048576];Arrays.fill(exact,(byte)' ');System.arraycopy(validBytes,0,exact,0,validBytes.length);
        assertThat(raw(admin,exact,false)).isEqualTo(200);assertThat(raw(admin,Arrays.copyOf(exact,1048577),true)).isEqualTo(413);
        assertThat(raw(admin,"{".getBytes(StandardCharsets.UTF_8),false)).isEqualTo(400);
        assertThat(raw(admin,"{\"schemaVersion\":1,\"articles\":[],\"published\":true}".getBytes(StandardCharsets.UTF_8),false)).isEqualTo(400);
    }
    int raw(HttpClient actor,byte[] bytes,boolean chunked)throws Exception {
        String csrf=json.readTree(call(actor,"GET","/auth/csrf",null).body()).get("token").asText();
        var body=chunked?HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(bytes)):HttpRequest.BodyPublishers.ofByteArray(bytes);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/admin/articles/import/validate")).header("Origin","http://127.0.0.1:4174").header("Authorization","Bearer "+access.get(actor)).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/json").POST(body).build();
        return actor.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
