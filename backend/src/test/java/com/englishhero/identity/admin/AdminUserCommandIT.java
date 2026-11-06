package com.englishhero.identity.admin;
import java.util.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminUserCommandIT extends AdminUserHttpITSupport {
 long uid(HttpClient c){return jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,accounts.get(c).get("username"));}
 Map<String,Object> input(int version,boolean enabled){return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",version,"enabled",enabled,"reason","管理员测试原因😀"));}
 @Test void disablingRevokesOldAccessAndRefreshAndRestoringRequiresNewLogin()throws Exception{
 var admin=actor(true);var user=actor(false);long id=uid(user);String message=create(user);var disable=input(0,false);
 assertThat(call(admin,"PUT","/admin/users/"+id+"/status",disable,false).statusCode()).isEqualTo(403);
 var r=call(admin,"PUT","/admin/users/"+id+"/status",disable);assertThat(r.statusCode()).isEqualTo(200);assertThat(json.readTree(r.body()).get("version").asInt()).isEqualTo(1);
 assertThat(call(user,"GET","/auth/me",null).statusCode()).isEqualTo(401);assertThat(call(user,"POST","/auth/refresh",null).statusCode()).isEqualTo(401);assertThat(call(user,"POST","/auth/login",accounts.get(user)).statusCode()).isEqualTo(401);assertThat(call(guest(),"GET","/wall/messages/"+message,null).statusCode()).isEqualTo(200);
 assertThat(call(admin,"PUT","/admin/users/"+id+"/status",input(1,true)).statusCode()).isEqualTo(200);assertThat(call(user,"GET","/auth/me",null).statusCode()).isEqualTo(401);assertThat(call(user,"POST","/auth/refresh",null).statusCode()).isEqualTo(401);assertThat(call(user,"POST","/auth/login",accounts.get(user)).statusCode()).isEqualTo(200);assertThat(call(user,"GET","/auth/me",null).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wall_message WHERE id=?",Integer.class,message)).isEqualTo(1);
 }
 @Test void commandsProtectAdminsAndAreIdempotentVersionedAndValidated()throws Exception{
 var admin=actor(true);var other=actor(true);var user=actor(false);long id=uid(user);var command=input(0,false);
 assertThat(call(user,"PUT","/admin/users/"+id+"/status",command).statusCode()).isEqualTo(403);assertThat(call(admin,"PUT","/admin/users/"+uid(admin)+"/status",command).statusCode()).isEqualTo(403);assertThat(call(admin,"PUT","/admin/users/"+uid(other)+"/status",command).statusCode()).isEqualTo(403);
 var invalid=input(0,false);invalid.put("reason","  ");assertThat(call(admin,"PUT","/admin/users/"+id+"/status",invalid).statusCode()).isEqualTo(400);invalid.put("reason","😀".repeat(201));assertThat(call(admin,"PUT","/admin/users/"+id+"/status",invalid).statusCode()).isEqualTo(400);
 assertThat(call(admin,"PUT","/admin/users/"+id+"/status",command).statusCode()).isEqualTo(200);assertThat(call(admin,"PUT","/admin/users/"+id+"/status",command).statusCode()).isEqualTo(200);
 assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_user_audit WHERE target_id=?",Integer.class,id)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT security_version FROM identity_user WHERE id=?",Integer.class,id)).isEqualTo(1);
 var changed=new LinkedHashMap<>(command);changed.put("reason","different");assertThat(call(admin,"PUT","/admin/users/"+id+"/status",changed).statusCode()).isEqualTo(409);assertThat(call(admin,"PUT","/admin/users/"+id+"/status",input(0,true)).statusCode()).isEqualTo(409);
 String request=(String)command.get("requestId");assertThat(call(admin,"GET","/admin/users/requests/"+request,null).statusCode()).isEqualTo(200);assertThat(call(other,"GET","/admin/users/requests/"+request,null).statusCode()).isEqualTo(404);
 }
 @Test void concurrentAdminsOnlyOneVersionWins()throws Exception{
 var a=actor(true);var b=actor(true);var user=actor(false);long id=uid(user);try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var x=pool.submit(()->call(a,"PUT","/admin/users/"+id+"/status",input(0,false)).statusCode());var y=pool.submit(()->call(b,"PUT","/admin/users/"+id+"/status",input(0,false)).statusCode());assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(200,409);}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_user_audit WHERE target_id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void statusPayloadRequiresRealBooleanAndIntegerVersion()throws Exception{var a=actor(true);var u=actor(false);long id=uid(u);var p=input(0,false);p.put("enabled","false");assertThat(call(a,"PUT","/admin/users/"+id+"/status",p).statusCode()).isEqualTo(400);p=input(0,false);p.put("expectedVersion",0.5);assertThat(call(a,"PUT","/admin/users/"+id+"/status",p).statusCode()).isEqualTo(400);}
}
