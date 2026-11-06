package com.englishhero.reading.translation;

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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"english-hero.practice.ai.enabled=true","english-hero.practice.ai.api-key=synthetic-test-only","english-hero.practice.ai.worker-enabled=false","english-hero.reading.translation.baidu.app-id=synthetic-app","english-hero.reading.translation.baidu.secret-key=synthetic-secret"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
abstract class SelectionTranslationHttpITSupport {
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
  String name="users_"+UUID.randomUUID().toString().replace("-","").substring(0,16),password=UUID.randomUUID()+"aA!";
  if(admin)identities.bootstrap(name,password,"站长");else identities.register(name,password,"学习者");
  owners.add(jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?",Long.class,name));
  var c=guest();accounts.put(c,Map.of("username",name,"password",password));assertThat(call(c,"POST","/auth/login",Map.of("username",name,"password",password)).statusCode()).isEqualTo(200);return c;
 }
 HttpResponse<String> call(HttpClient c,String method,String path,Object body)throws Exception{return call(c,method,path,body,true);}
 HttpResponse<String> call(HttpClient c,String method,String path,Object body,boolean csrf)throws Exception{
  String token=null;if(!method.equals("GET")&&csrf)token=json.readTree(call(c,"GET","/auth/csrf",null).body()).get("token").asText();
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Origin","http://127.0.0.1:4174");
  if(tokens.containsKey(c)&&!path.equals("/auth/csrf")&&!path.equals("/auth/refresh"))b.header("Authorization","Bearer "+tokens.get(c));if(token!=null)b.header("X-CSRF-TOKEN",token);if(body!=null)b.header("Content-Type","application/json");
  var r=c.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  if(path.equals("/auth/login")&&r.statusCode()==200)tokens.put(c,json.readTree(r.body()).get("accessToken").asText());return r;
 }
 Map<String,Object> message(){return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"kind","MESSAGE","category","STUDY","color","yellow","text","今天练习阅读，有一点进步。","preferredPosition",Map.of("x",100,"y",100)));}
 String create(HttpClient c)throws Exception{var r=call(c,"POST","/wall/messages",message());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return json.readTree(r.body()).get("id").asText();}
 Map<String,Object> published(java.net.http.HttpClient admin)throws Exception{var body=new LinkedHashMap<String,Object>();body.put("categoryId","1");body.put("title","Selection translation test "+UUID.randomUUID());body.put("titleZh","原创翻译测试");body.put("summary","A short introduction.");body.put("difficulty","B1");body.put("body","When Omar moved to a new town, he visited the public library. Quiet spaces help people read and learn.");body.put("author","Lingua Loop");body.put("sourceType","ORIGINAL");body.put("sourceUrl",null);body.put("usageNote","原创测试");var created=call(admin,"POST","/admin/articles",body);assertThat(created.statusCode()).isIn(200,201);var tree=json.readTree(created.body());String id=tree.get("id").asText();assertThat(call(admin,"POST","/admin/articles/"+id+"/publish",Map.of("expectedLockVersion",tree.get("lockVersion").asInt())).statusCode()).isEqualTo(200);return Map.of("requestId",UUID.randomUUID().toString(),"articleId",id,"versionId",tree.get("draftVersionId").asText(),"text","When Omar moved to a new town");}
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
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='admin_user_command'",Integer.class)>0){jdbc.update("DELETE FROM admin_user_audit WHERE actor_id IN ("+ids+") OR target_id IN ("+ids+")");jdbc.update("DELETE FROM admin_user_command WHERE actor_id IN ("+ids+") OR target_id IN ("+ids+")");}
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reading_selection_translation'",Integer.class)>0){for(var q:jdbc.queryForList("SELECT quota_month,source_characters,status,error_code FROM reading_selection_translation WHERE user_id IN ("+ids+") AND model='baidu-general'")){if(!"FAILED".equals(q.get("status"))||!BaiduTranslationJobsSupport.unsent((String)q.get("error_code")))jdbc.update("UPDATE reading_baidu_monthly_quota SET used_characters=used_characters-? WHERE month_start=?",q.get("source_characters"),q.get("quota_month"));}for(var budget:jdbc.queryForList("SELECT budget_day,SUM(COALESCE(settled_microusd,reserved_microusd)) amount FROM reading_selection_translation WHERE user_id IN ("+ids+") GROUP BY budget_day"))jdbc.update("UPDATE practice_ai_daily_budget SET used_microusd=used_microusd-? WHERE budget_day=?",budget.get("amount"),budget.get("budget_day"));jdbc.update("DELETE FROM reading_selection_translation_request WHERE user_id IN ("+ids+")");jdbc.update("DELETE FROM reading_selection_translation WHERE user_id IN ("+ids+")");}
  jdbc.update("DELETE FROM reading_progress WHERE user_id IN ("+ids+")");jdbc.update("UPDATE reading_article SET published_version_id=NULL,draft_version_id=NULL WHERE created_by IN ("+ids+")");jdbc.update("DELETE v FROM reading_article_version v JOIN reading_article a ON a.id=v.article_id WHERE a.created_by IN ("+ids+")");jdbc.update("DELETE FROM reading_article WHERE created_by IN ("+ids+")");
  jdbc.update("DELETE FROM identity_user WHERE id IN ("+ids+")");
 }
}
