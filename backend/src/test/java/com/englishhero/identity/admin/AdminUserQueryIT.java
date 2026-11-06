package com.englishhero.identity.admin;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminUserQueryIT extends AdminUserHttpITSupport {
 @Test void queriesAreAdminOnlyAndNeverExposeCredentials()throws Exception{
 var a=actor(true);var u=actor(false);String username=accounts.get(u).get("username");long id=jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,username);jdbc.update("UPDATE identity_user SET email='sample@example.test',email_verified_at=UTC_TIMESTAMP(6) WHERE id=?",id);
 assertThat(call(guest(),"GET","/admin/users",null).statusCode()).isEqualTo(401);assertThat(call(u,"GET","/admin/users",null).statusCode()).isEqualTo(403);
 var r=call(a,"GET","/admin/users?keyword="+username,null);assertThat(r.statusCode()).isEqualTo(200);var data=json.readTree(r.body());assertThat(data.get("total").asInt()).isEqualTo(1);assertThat(data.get("items").get(0).get("id").asText()).isEqualTo(Long.toString(id));assertThat(r.body()).doesNotContain("password","passwordHash","securityVersion","email",accounts.get(u).get("password"));
 r=call(a,"GET","/admin/users/"+id,null);assertThat(r.statusCode()).isEqualTo(200);assertThat(r.body()).contains("s***@e***.test").doesNotContain("sample@example.test","password");
 assertThat(call(a,"GET","/admin/users?status=DISABLED&keyword="+username,null).body()).contains("\"total\":0");assertThat(call(a,"GET","/admin/users?pageSize=51",null).statusCode()).isEqualTo(400);assertThat(call(a,"GET","/admin/users/9223372036854775807",null).statusCode()).isEqualTo(404);
 }
 @Test void filtersUseLiteralKeywordsAndReturnProtectedAdmins()throws Exception{
 var a=actor(true);var u=actor(false);String name=accounts.get(u).get("username");jdbc.update("UPDATE identity_user SET display_name='literal%_test' WHERE username=?",name);
 var r=call(a,"GET","/admin/users?keyword=literal%25_&role=USER",null);assertThat(r.statusCode()).isEqualTo(200);assertThat(json.readTree(r.body()).get("total").asInt()).isEqualTo(1);
 r=call(a,"GET","/admin/users?keyword="+accounts.get(a).get("username")+"&role=ADMIN",null);assertThat(json.readTree(r.body()).get("items").get(0).get("protectedAccount").asBoolean()).isTrue();
 }
}
