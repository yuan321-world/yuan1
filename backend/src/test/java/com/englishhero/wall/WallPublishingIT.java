package com.englishhero.wall;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WallPublishingIT extends WallHttpITSupport {
 @org.springframework.beans.factory.annotation.Autowired WallService diagnosticService;
 @Test void serviceProxyUsesRepositoryAccessors()throws Exception{
  actor(false);long uid=owners.getLast();var p=new com.englishhero.identity.SessionPrincipal(uid,"fixture",0,List.of("USER"));
  diagnosticService.create(p,new WallDtos.MessageInput(UUID.randomUUID().toString(),"MESSAGE","STUDY","yellow","诊断内容","",null));
 }
 @Test void publicReadAuthenticatedWriteAndIdempotentOwnerDelete()throws Exception{
  var a=actor(false);var b=actor(false);var g=guest();
  assertThat(call(g,"GET","/wall/messages",null).statusCode()).isEqualTo(200);
  assertThat(call(g,"POST","/wall/messages",message()).statusCode()).isEqualTo(401);
  assertThat(call(a,"POST","/wall/messages",message(),false).statusCode()).isEqualTo(403);
  var input=message();var r=call(a,"POST","/wall/messages",input);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
  String id=json.readTree(r.body()).get("id").asText();
  assertThat(call(a,"POST","/wall/messages",input).body()).isEqualTo(r.body());
  input.put("text","different");assertThat(call(a,"POST","/wall/messages",input).statusCode()).isEqualTo(409);
  var detail=call(g,"GET","/wall/messages/"+id,null);assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).doesNotContain("password","username","email","internalNote");
  var command=Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0);
  assertThat(call(b,"DELETE","/wall/messages/"+id,command).statusCode()).isEqualTo(404);
  assertThat(call(a,"DELETE","/wall/messages/"+id,command).statusCode()).isEqualTo(200);
  assertThat(call(g,"GET","/wall/messages/"+id,null).statusCode()).isEqualTo(404);
 }
 @Test void limitsUnicodeAndAnnouncementAuthority()throws Exception{
  var a=actor(false);var input=message();input.put("text","😀".repeat(1001));assertThat(call(a,"POST","/wall/messages",input).statusCode()).isEqualTo(400);
  input.put("text","😀".repeat(1000));var good=call(a,"POST","/wall/messages",input);assertThat(good.statusCode()).withFailMessage(good.body()).isEqualTo(200);
  input=message();input.put("kind","ANNOUNCEMENT");input.put("title","Fake admin");assertThat(call(a,"POST","/wall/messages",input).statusCode()).isEqualTo(403);
  assertThat(call(a,"GET","/admin/wall/messages",null).statusCode()).isEqualTo(403);
 }
 @Test void cooldownReturnsRetryHeaderWithoutAnotherRecord()throws Exception{
  var a=actor(false);create(a);var r=call(a,"POST","/wall/messages",message());assertThat(r.statusCode()).isEqualTo(429);assertThat(r.headers().firstValue("Retry-After")).isPresent();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_message WHERE user_id=?",Integer.class,owners.getLast())).isEqualTo(1);
 }
 @Test void dailyQuotaResetsOnlyItsOwnDayAndRetryDoesNotConsumeAnotherSlot()throws Exception{
  var a=actor(false);long uid=owners.getLast();create(a);jdbc.update("UPDATE wall_user_quota SET message_count=9,last_message=NULL WHERE user_id=?",uid);var input=message();assertThat(call(a,"POST","/wall/messages",input).statusCode()).isEqualTo(200);assertThat(call(a,"POST","/wall/messages",input).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT message_count FROM wall_user_quota WHERE user_id=?",Integer.class,uid)).isEqualTo(10);assertThat(call(a,"POST","/wall/messages",message()).statusCode()).isEqualTo(429);jdbc.update("UPDATE wall_user_quota SET quota_day=DATE_SUB(quota_day,INTERVAL 1 DAY),last_message=NULL WHERE user_id=?",uid);assertThat(call(a,"POST","/wall/messages",message()).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT message_count FROM wall_user_quota WHERE user_id=?",Integer.class,uid)).isEqualTo(1);
 }
}
