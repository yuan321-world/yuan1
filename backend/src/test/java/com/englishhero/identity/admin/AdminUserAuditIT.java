package com.englishhero.identity.admin;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class AdminUserAuditIT extends AdminUserHttpITSupport {
 @MockitoSpyBean AdminUserMapper mapper;
 @Test void auditIsAdminOnlyPagedAndSafeAndMatchesStatusChanges()throws Exception{
 var a=actor(true);var u=actor(false);String name=accounts.get(u).get("username");long id=jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,name);
 for(int i=0;i<3;i++){assertThat(call(a,"PUT","/admin/users/"+id+"/status",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",i,"enabled",i%2==1,"reason","<script>审计原因</script>")).statusCode()).isEqualTo(200);}
 assertThat(call(guest(),"GET","/admin/users/audit",null).statusCode()).isEqualTo(401);
 // Disabled users also cannot read protected audit data.
 assertThat(call(u,"GET","/admin/users/audit",null).statusCode()).isEqualTo(401);
 var r=call(a,"GET","/admin/users/audit?userId="+id+"&pageSize=2",null);assertThat(r.statusCode()).isEqualTo(200);var tree=json.readTree(r.body());assertThat(tree.get("items").size()).isEqualTo(2);assertThat(tree.get("hasMore").asBoolean()).isTrue();assertThat(r.body()).doesNotContain("password","request_hash","securityVersion");String cursor=tree.get("nextCursor").asText();var older=json.readTree(call(a,"GET","/admin/users/audit?userId="+id+"&pageSize=2&cursor="+cursor,null).body());assertThat(older.get("items").size()).isEqualTo(1);
 assertThat(call(a,"GET","/admin/users/audit?cursor=-1",null).statusCode()).isEqualTo(400);
 }
 @Test void auditFailureRollsBackAccountVersionAndCommand()throws Exception{
 var a=actor(true);var u=actor(false);long id=jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,accounts.get(u).get("username"));doThrow(new IllegalStateException("simulated audit failure")).when(mapper).insertAudit(anyLong(),anyLong(),eq(id),anyBoolean(),anyBoolean(),anyInt(),anyString(),any());
 String request=UUID.randomUUID().toString();assertThat(call(a,"PUT","/admin/users/"+id+"/status",Map.of("requestId",request,"expectedVersion",0,"enabled",false,"reason","回滚测试")).statusCode()).isEqualTo(500);
 assertThat(jdbc.queryForObject("SELECT enabled FROM identity_user WHERE id=?",Boolean.class,id)).isTrue();assertThat(jdbc.queryForObject("SELECT security_version FROM identity_user WHERE id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_user_command WHERE request_id=?",Integer.class,request)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_user_audit WHERE target_id=?",Integer.class,id)).isZero();
 }
}
