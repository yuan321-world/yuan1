package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WritingPersonalIT extends ReadingHttpITSupport {
 Map<String,Object> material(){return Map.of("expression","Instead of waiting …","meaningZh","与其等待","keyPhrases",List.of("instead of"),"structure","Instead of + doing","exampleEn","Instead of waiting, we can start.","exampleZh","与其等待，不如开始。","scenarios",List.of("建议"),"sortOrder",0);}
 String create(java.net.http.HttpClient admin)throws Exception{var article=json.readTree(call(admin,"POST","/admin/articles",draft("Personal writing")).body());String aid=article.get("id").asText(),vid=article.get("draftVersionId").asText();call(admin,"POST","/admin/articles/"+aid+"/publish",new ReadingDtos.LockInput(0));var m=json.readTree(call(admin,"POST","/admin/articles/"+aid+"/versions/"+vid+"/writing-materials",material()).body());String id=m.get("id").asText();call(admin,"POST","/admin/writing-materials/"+id+"/publish",new ReadingDtos.LockInput(0));return id;}
 @Test void ownedPersistenceSnapshotsAndUnpublishedBoundaries()throws Exception{
  var admin=actor(true);var reader=actor(false);var other=actor(false);String id=create(admin),path="/writing-materials/"+id;
  assertThat(call(anonymous(),"PUT",path+"/favorite",Map.of("materialRevision",1)).statusCode()).isEqualTo(401);
  assertThat(call(reader,"PUT",path+"/favorite",Map.of("materialRevision",1)).statusCode()).isEqualTo(200);
  assertThat(call(reader,"PUT",path+"/favorite",Map.of("materialRevision",1)).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_writing_favorite WHERE material_id=?",Long.class,id)).isEqualTo(1);
  var saved=call(reader,"PUT",path+"/imitation",Map.of("text","Instead of waiting, let's take a small step.","materialRevision",1,"expectedLockVersion",0));assertThat(saved.statusCode()).isEqualTo(200);
  assertThat(json.readTree(call(other,"GET",path+"/personal",null).body()).get("imitation").isNull()).isTrue();
  assertThat(call(reader,"PUT",path+"/imitation",Map.of("text","Other text.","materialRevision",1,"expectedLockVersion",0)).statusCode()).isEqualTo(409);
  var update=new HashMap<String,Object>(material());update.put("expression","New published expression");update.put("expectedLockVersion",1);call(admin,"PUT","/admin/writing-materials/"+id,update);call(admin,"POST","/admin/writing-materials/"+id+"/publish",new ReadingDtos.LockInput(2));
  var personal=json.readTree(call(reader,"GET",path+"/personal",null).body());assertThat(personal.get("material").get("revision").asInt()).isEqualTo(2);assertThat(personal.get("imitation").get("snapshot").get("expression").asText()).contains("Instead of");
  assertThat(call(reader,"PUT",path+"/imitation",Map.of("text","Instead of waiting, I will study.","materialRevision",1,"expectedLockVersion",1)).statusCode()).isEqualTo(200);
  call(admin,"POST","/admin/writing-materials/"+id+"/unpublish",new ReadingDtos.LockInput(3));
  assertThat(json.readTree(call(reader,"GET",path+"/personal",null).body()).get("available").asBoolean()).isFalse();assertThat(call(other,"GET",path+"/personal",null).statusCode()).isEqualTo(404);
  assertThat(call(reader,"PUT",path+"/favorite",Map.of("materialRevision",2)).statusCode()).isEqualTo(409);
  assertThat(call(reader,"PUT",path+"/imitation",Map.of("text","Do not save.","materialRevision",1,"expectedLockVersion",2)).statusCode()).isEqualTo(409);
  assertThat(call(reader,"DELETE",path+"/favorite",null).statusCode()).isEqualTo(204);
  assertThat(json.readTree(call(reader,"GET","/account/writing-materials?view=favorites",null).body()).get("total").asInt()).isZero();assertThat(json.readTree(call(reader,"GET","/account/writing-materials?view=imitations",null).body()).get("total").asInt()).isEqualTo(1);assertThat(json.readTree(call(other,"GET","/account/writing-materials?view=imitations",null).body()).get("total").asInt()).isZero();
 }
 @Test void simultaneousFirstSaveCannotOverwriteAndUnknownUserFieldIsRejected()throws Exception{
  var admin=actor(true);var reader=actor(false);String id=create(admin),path="/writing-materials/"+id;
  assertThat(call(reader,"PUT",path+"/favorite",Map.of("materialRevision",1,"userId","999")).statusCode()).isEqualTo(400);
  var input=Map.of("text","Instead of waiting, we learn.","materialRevision",1,"expectedLockVersion",0);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->call(reader,"PUT",path+"/imitation",input).statusCode());var b=pool.submit(()->call(reader,"PUT",path+"/imitation",input).statusCode());assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_writing_imitation WHERE material_id=?",Long.class,id)).isEqualTo(1);
 }
}
