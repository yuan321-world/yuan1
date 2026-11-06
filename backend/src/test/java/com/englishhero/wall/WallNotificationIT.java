package com.englishhero.wall;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallNotificationIT extends WallHttpITSupport {
 @Test void privateReasonAndReadBoundaryAreOwnedAndIdempotent()throws Exception{
  var user=actor(false);var other=actor(false);var admin=actor(true);String id=create(user);
  var edit=new LinkedHashMap<String,Object>(Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0,"action","EDIT","text","已处理的文字","authorReason","请围绕学习交流。","internalNote","HIDDEN_NOTE","reportIds",List.of()));
  assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",edit).statusCode()).isEqualTo(200);
  call(admin,"POST","/admin/wall/messages/"+id+"/moderation",edit);
  var first=call(user,"GET","/wall/notifications",null);assertThat(first.statusCode()).isEqualTo(200);var firstTree=json.readTree(first.body());assertThat(firstTree.get("items").size()).isEqualTo(1);assertThat(first.body()).contains("请围绕学习交流").doesNotContain("HIDDEN_NOTE","internalNote");String nid=firstTree.get("items").get(0).get("id").asText(),boundary=firstTree.get("readBoundary").asText();
  assertThat(call(other,"PUT","/wall/notifications/"+nid+"/read",Map.of()).statusCode()).isEqualTo(404);
  edit.put("requestId",UUID.randomUUID().toString());edit.put("expectedVersion",1);edit.put("action","HIDE");assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",edit).statusCode()).isEqualTo(200);
  assertThat(call(user,"PUT","/wall/notifications/read-all",Map.of("throughId",boundary)).statusCode()).isEqualTo(200);
  var after=json.readTree(call(user,"GET","/wall/notifications",null).body());assertThat(after.get("unreadCount").asInt()).isEqualTo(1);assertThat(after.get("items").get(0).get("canOpen").asBoolean()).isFalse();
  assertThat(json.readTree(call(other,"GET","/wall/notifications",null).body()).get("items").size()).isZero();
 }
}
