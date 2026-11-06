package com.englishhero.wall;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallModerationIT extends WallHttpITSupport {
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean WallModerationMapper moderationSpy;
 Map<String,Object> change(long version,String action){return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",version,"action",action,"authorReason","请注意保护个人隐私。","internalNote","INTERNAL_ONLY","reportIds",List.of()));}
 @Test void announcementAndVersionedModerationRemainAdminOnly()throws Exception{
  var admin=actor(true);var user=actor(false);var input=message();input.put("kind","ANNOUNCEMENT");input.put("title","学习公告");input.put("text","请**友好交流**。<img src=x>");
  var ann=call(admin,"POST","/admin/wall/messages",input);assertThat(ann.statusCode()).withFailMessage(ann.body()).isEqualTo(200);assertThat(ann.body()).contains("ANNOUNCEMENT");
  String id=create(user);var edit=change(0,"EDIT");edit.put("text","已调整的内容");
  assertThat(call(user,"POST","/admin/wall/messages/"+id+"/moderation",edit).statusCode()).isEqualTo(403);
  var result=call(admin,"POST","/admin/wall/messages/"+id+"/moderation",edit);assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
  assertThat(json.readTree(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",edit).body())).isEqualTo(json.readTree(result.body()));
  assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",change(0,"HIDE")).statusCode()).isEqualTo(409);
  assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",change(1,"HIDE")).statusCode()).isEqualTo(200);
  assertThat(call(guest(),"GET","/wall/messages/"+id,null).statusCode()).isEqualTo(404);
  assertThat(call(user,"GET","/wall/mine",null).body()).contains("HIDDEN","请注意保护个人隐私").doesNotContain("INTERNAL_ONLY");
  assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",change(2,"RESTORE")).statusCode()).isEqualTo(200);
  assertThat(call(user,"DELETE","/wall/messages/"+id,Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",3)).statusCode()).isEqualTo(200);
  assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",change(4,"RESTORE")).statusCode()).isEqualTo(409);
 }
 @Test void keepingAReportDoesNotExposeAnInternalReasonAsHiddenContentReason()throws Exception{
  var admin=actor(true);var user=actor(false);String id=create(user);assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",change(0,"HIDE")).statusCode()).isEqualTo(200);var keep=change(1,"KEEP");keep.put("authorReason","KEEP_INTERNAL_CONTEXT");assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",keep).statusCode()).isEqualTo(200);assertThat(call(user,"GET","/wall/mine",null).body()).doesNotContain("KEEP_INTERNAL_CONTEXT");assertThat(json.readTree(call(user,"GET","/wall/notifications",null).body()).get("items").size()).isEqualTo(1);
 }
 @Test void notificationFailureRollsBackContentReportAuditAndCommand()throws Exception{
  var admin=actor(true);var user=actor(false);String id=create(user);long owner=owners.getLast();
  var r=call(user,"POST","/wall/reports",Map.of("requestId",UUID.randomUUID().toString(),"targetType","MESSAGE","targetId",id,"reason","OTHER","detail","测试报告"));assertThat(r.statusCode()).isEqualTo(200);String rid=json.readTree(r.body()).get("id").asText();
  org.mockito.Mockito.doThrow(new IllegalStateException("fixture notification failure")).when(moderationSpy).notify(org.mockito.ArgumentMatchers.eq(owner),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(Long.parseLong(id)),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq("HIDE"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(java.time.Instant.class));
  var input=change(0,"HIDE");input.put("reportIds",List.of(rid));assertThat(call(admin,"POST","/admin/wall/messages/"+id+"/moderation",input).statusCode()).isEqualTo(500);
  assertThat(jdbc.queryForObject("SELECT status FROM wall_message WHERE id=?",String.class,id)).isEqualTo("VISIBLE");assertThat(jdbc.queryForObject("SELECT status FROM wall_report WHERE id=?",String.class,rid)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_moderation_log WHERE message_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_notification WHERE message_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_request WHERE request_id=?",Integer.class,input.get("requestId"))).isZero();
 }
 @Test void concurrentOwnDeleteAndAdminEditCannotResurrectContent()throws Exception{
  var admin=actor(true);var user=actor(false);String id=create(user);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var first=pool.submit(()->call(user,"DELETE","/wall/messages/"+id,Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0)));var input=change(0,"EDIT");input.put("text","编辑后的内容");var second=pool.submit(()->call(admin,"POST","/admin/wall/messages/"+id+"/moderation",input));var codes=List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode(),second.get(20,java.util.concurrent.TimeUnit.SECONDS).statusCode());assertThat(codes).containsExactlyInAnyOrder(200,409);assertThat(jdbc.queryForObject("SELECT version FROM wall_message WHERE id=?",Long.class,id)).isEqualTo(1);}finally{pool.shutdownNow();}
 }
}
