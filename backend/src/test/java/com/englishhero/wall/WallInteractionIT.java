package com.englishhero.wall;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallInteractionIT extends WallHttpITSupport {
 @Test void likesCommentsAndReportsKeepIdentityAndParentIsolation()throws Exception{
  var a=actor(false);var b=actor(false);String id=create(a);
  assertThat(call(b,"PUT","/wall/messages/"+id+"/like",Map.of("liked",true)).statusCode()).isEqualTo(200);
  call(b,"PUT","/wall/messages/"+id+"/like",Map.of("liked",true));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_like WHERE message_id=?",Integer.class,id)).isEqualTo(1);
  var comment=Map.of("requestId",UUID.randomUUID().toString(),"text","一起加油！");var r=call(b,"POST","/wall/messages/"+id+"/comments",comment);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);String cid=json.readTree(r.body()).get("id").asText();
  assertThat(call(b,"POST","/wall/messages/"+id+"/comments",comment).body()).isEqualTo(r.body());
  assertThat(call(a,"DELETE","/wall/comments/"+cid,Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0)).statusCode()).isEqualTo(404);
  var report=new LinkedHashMap<String,Object>(Map.of("requestId",UUID.randomUUID().toString(),"targetType","COMMENT","targetId",cid,"reason","OTHER","detail","请检查"));var reported=call(a,"POST","/wall/reports",report);assertThat(reported.statusCode()).isEqualTo(200);report.put("requestId",UUID.randomUUID().toString());assertThat(json.readTree(call(a,"POST","/wall/reports",report).body())).isEqualTo(json.readTree(reported.body()));
  var publicComments=call(guest(),"GET","/wall/messages/"+id+"/comments",null);assertThat(publicComments.body()).contains("一起加油").doesNotContain("举报","internalNote","username","email");
  assertThat(call(b,"DELETE","/wall/comments/"+cid,Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0)).statusCode()).isEqualTo(200);
  assertThat(call(a,"GET","/wall/messages/"+id,null).statusCode()).isEqualTo(200);
 }
 @Test void hiddenParentRejectsEveryNewInteraction()throws Exception{
  var a=actor(false);var b=actor(false);String id=create(a);jdbc.update("UPDATE wall_message SET status='HIDDEN' WHERE id=?",id);
  assertThat(call(b,"PUT","/wall/messages/"+id+"/like",Map.of("liked",true)).statusCode()).isEqualTo(404);
  assertThat(call(b,"POST","/wall/messages/"+id+"/comments",Map.of("requestId",UUID.randomUUID().toString(),"text","Hello")).statusCode()).isEqualTo(404);
  assertThat(call(b,"POST","/wall/reports",Map.of("requestId",UUID.randomUUID().toString(),"targetType","MESSAGE","targetId",id,"reason","OTHER","detail","")).statusCode()).isEqualTo(404);
 }
}
