package com.englishhero.vocabulary.phrase;
import java.util.*;
import java.net.http.*;
import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
class PhraseCollectionIT extends PhraseHttpITSupport {
 static final String ROOT="/vocabulary/phrases";
 @Test void missingSavedRoundReturnsExplicitJsonNull()throws Exception {
  var response=call(actor(false),"GET",ROOT+"/practice",null);
  assertThat(response.statusCode()).isEqualTo(200);
  assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
  assertThat(response.body()).isEqualTo("null");
 }
 JsonNode collect(HttpClient c,String text)throws Exception {var r=call(c,"POST",ROOT,Map.of("text",text,"meaningZh","期待","note","个人笔记"));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 @Test void privateCanonicalCollectionCancelRestoreAndLearningAreSeparate()throws Exception {
  var a=actor(false);var b=actor(false);var p=collect(a,"Look forward to");String id=p.get("id").asText();assertThat(p.get("enrolled").asBoolean()).isFalse();
  var again=collect(a," look   FORWARD to ");assertThat(again.get("id")).isEqualTo(p.get("id"));assertThat(json.readTree(call(b,"GET",ROOT,null).body()).get("items").size()).isZero();assertThat(call(b,"PATCH",ROOT+"/"+id,Map.of("meaningZh","其他释义","note","")).statusCode()).isEqualTo(404);
  assertThat(call(a,"PUT",ROOT+"/"+id+"/learning",Map.of("enrolled",true)).statusCode()).isEqualTo(200);assertThat(call(a,"DELETE",ROOT+"/"+id,null).statusCode()).isEqualTo(200);assertThat(json.readTree(call(a,"GET",ROOT,null).body()).get("items").size()).isZero();var restored=json.readTree(call(a,"POST",ROOT+"/"+id+"/restore",null).body());assertThat(restored.get("enrolled").asBoolean()).isTrue();assertThat(restored.get("note").asText()).isEqualTo("个人笔记");
  var csrf=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+ROOT+"/"+id)).header("Authorization","Bearer "+access.get(a)).DELETE().build();assertThat(a.send(csrf,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
  assertThat(call(a,"POST",ROOT,Map.of("text","look forward to","meaningZh","期待","source",Map.of("articleVersionId","999999999","sentenceKey","p1-s1","startOffset",0,"endOffset",15))).statusCode()).isEqualTo(404);
 }
 @Test void concurrentCollectIsCanonicalAndRestoresOneRecord()throws Exception {
  var a=actor(false);var second=anonymous();access.put(second,access.get(a));try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var x=pool.submit(()->collect(a,"look forward to"));var y=pool.submit(()->collect(second,"LOOK FORWARD TO"));assertThat(x.get().get("id")).isEqualTo(y.get().get("id"));}assertThat(json.readTree(call(a,"GET",ROOT,null).body()).get("stats").get("collected").asInt()).isEqualTo(1);
 }

 @Test void multiplePrivateSourcesKeepSnapshotsThroughCancelRestoreAndUnpublish()throws Exception {
  var admin=actor(true);var learner=actor(false);var outsider=actor(false);String phraseId=null;String category=jdbc.queryForObject("SELECT id FROM reading_category ORDER BY id LIMIT 1",String.class);
  for(int n=0;n<2;n++){
   var draft=new com.englishhero.reading.ReadingDtos.DraftInput(category,"Phrase source "+n,"词组来源","原文词组练习","B1","We look forward to tomorrow.","English Hero","ORIGINAL",null,"原创测试");
   var response=call(admin,"POST","/admin/articles",draft);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);var article=json.readTree(response.body());String aid=article.get("id").asText(),vid=article.get("draftVersionId").asText();call(admin,"POST","/admin/articles/"+aid+"/publish",Map.of("expectedLockVersion",0));call(learner,"POST","/reading/articles/"+aid+"/start",null);
   var source=Map.of("articleVersionId",vid,"sentenceKey","p0001-s0001","startOffset",3,"endOffset",18);var input=Map.of("text","look forward to","meaningZh","期待","source",source);assertThat(call(outsider,"POST",ROOT,input).statusCode()).isEqualTo(404);var result=call(learner,"POST",ROOT,input);assertThat(result.statusCode()).as(result.body()).isEqualTo(200);var phrase=json.readTree(result.body());if(phraseId==null)phraseId=phrase.get("id").asText();else assertThat(phrase.get("id").asText()).isEqualTo(phraseId);
   call(admin,"POST","/admin/articles/"+aid+"/unpublish",Map.of("expectedLockVersion",1));
  }
  call(learner,"DELETE",ROOT+"/"+phraseId,null);var restored=json.readTree(call(learner,"POST",ROOT+"/"+phraseId+"/restore",null).body());assertThat(restored.get("sources").size()).isEqualTo(2);assertThat(restored.get("sources").get(0).get("sentence").asText()).isEqualTo("We look forward to tomorrow.");assertThat(restored.get("enrolled").asBoolean()).isFalse();
 }
}
