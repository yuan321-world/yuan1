package com.englishhero.wall;

import com.englishhero.identity.*;
import com.englishhero.identity.token.TokenTestSupport;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=false"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
abstract class WallHttpITSupport {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired IdentityService identities;
 @Autowired tools.jackson.databind.ObjectMapper json;
 final List<Long> owners=new ArrayList<>();
 final Map<HttpClient,String> tokens=new java.util.concurrent.ConcurrentHashMap<>();
 final Map<HttpClient,Map<String,String>> accounts=new HashMap<>();
 @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry){
  TokenTestSupport.register(registry);
  var yaml=new YamlPropertiesFactoryBean();yaml.setResources(new FileSystemResource("./config/application-test.yml"));var p=Objects.requireNonNull(yaml.getObject());
  assertThat(p.getProperty("spring.datasource.url")).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
  for(String key:List.of("url","username","password"))registry.add("spring.datasource."+key,()->p.getProperty("spring.datasource."+key));
 }
 HttpClient guest(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 HttpClient actor(boolean admin)throws Exception{
  String name="wall_"+UUID.randomUUID().toString().replace("-","").substring(0,16),password=UUID.randomUUID()+"aA!";
  if(admin)identities.bootstrap(name,password,"站长");else identities.register(name,password,"学习者");
  owners.add(jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,name));
  var c=guest();accounts.put(c,Map.of("username",name,"password",password));assertThat(call(c,"POST","/auth/login",Map.of("username",name,"password",password)).statusCode()).isEqualTo(200);return c;
 }
 HttpResponse<String> call(HttpClient c,String method,String path,Object body)throws Exception{return call(c,method,path,body,true);}
 HttpResponse<String> call(HttpClient c,String method,String path,Object body,boolean csrf)throws Exception{
  String token=null;if(!method.equals("GET")&&csrf)token=json.readTree(call(c,"GET","/auth/csrf",null).body()).get("token").asText();
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Origin","http://127.0.0.1:4174");
  if(tokens.containsKey(c))b.header("Authorization","Bearer "+tokens.get(c));if(token!=null)b.header("X-CSRF-TOKEN",token);if(body!=null)b.header("Content-Type","application/json");
  var r=c.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  if(path.equals("/auth/login")&&r.statusCode()==200)tokens.put(c,json.readTree(r.body()).get("accessToken").asText());return r;
 }
 Map<String,Object> message(){return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"kind","MESSAGE","category","STUDY","color","yellow","text","今天练习阅读，有一点进步。","preferredPosition",Map.of("x",100,"y",100)));}
 String create(HttpClient c)throws Exception{var r=call(c,"POST","/wall/messages",message());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return json.readTree(r.body()).get("id").asText();}
 @AfterEach void cleanup(){
  if(owners.isEmpty())return;String ids=owners.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='wall_message'",Integer.class)>0){
   String messages="SELECT id FROM wall_message WHERE user_id IN ("+ids+")";
   jdbc.update("DELETE FROM wall_notification WHERE recipient_id IN ("+ids+") OR message_id IN ("+messages+")");
   jdbc.update("DELETE FROM wall_moderation_log WHERE actor_id IN ("+ids+") OR message_id IN ("+messages+")");
   jdbc.update("DELETE FROM wall_request WHERE user_id IN ("+ids+")");
   jdbc.update("DELETE FROM wall_report WHERE user_id IN ("+ids+") OR message_id IN ("+messages+")");
   jdbc.update("DELETE FROM wall_like WHERE user_id IN ("+ids+") OR message_id IN ("+messages+")");
   jdbc.update("DELETE FROM wall_comment WHERE user_id IN ("+ids+") OR message_id IN ("+messages+")");
   jdbc.update("DELETE FROM wall_message WHERE user_id IN ("+ids+")");jdbc.update("DELETE FROM wall_user_quota WHERE user_id IN ("+ids+")");
  }
  jdbc.update("DELETE FROM identity_user WHERE id IN ("+ids+")");
 }
}
