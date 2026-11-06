package com.englishhero.reading;

import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

abstract class PracticeHttpITSupport extends ReadingHttpITSupport {
 Map<String,Object> question(String key,String stem){var q=new LinkedHashMap<String,Object>();q.put("key",key);q.put("type","DETAIL");q.put("stem",stem);q.put("options",List.of("Small steps help.","Nobody learns.","We should stop.","Waiting is enough."));q.put("correctOption","A");q.put("explanation","原创测试材料强调持续练习。");q.put("sortOrder",key.equals("q1")?0:1);return q;}
 Map<String,Object> metadata(){return new LinkedHashMap<>(Map.of("title","Practice fixture","titleZh","专项测试材料","sourceKind","ORIGINAL","topic","education","sourceName","原创测试","usageNote","仅用于自动化测试，不是真题"));}
 Map<String,Object> draftSet(JsonNode article,int lock,List<?> questions){var d=new LinkedHashMap<String,Object>();d.put("articleId",article.get("id").asText());d.put("articleVersionId",article.get("draftVersionId").asText());d.put("examCode","CET4");d.put("readingType","DETAILED");d.put("metadata",metadata());d.put("questions",questions);d.put("expectedLockVersion",lock);return d;}
 JsonNode article(HttpClient admin)throws Exception{var a=success(call(admin,"POST","/admin/articles",draft("Practice fixture "+UUID.randomUUID())));success(call(admin,"POST","/admin/articles/"+a.get("id").asText()+"/publish",Map.of("expectedLockVersion",0)));return a;}
 JsonNode success(HttpResponse<String> response){assertThat(response.statusCode()).withFailMessage("HTTP %s: %s",response.statusCode(),response.body()).isIn(200,201);return json.readTree(response.body());}
 JsonNode createSet(HttpClient admin,JsonNode a)throws Exception{return success(call(admin,"POST","/admin/practice/sets",draftSet(a,0,List.of(question("q1","What helps?"),question("q2","What should we do?")))));}
 JsonNode reviewAll(HttpClient admin,JsonNode set)throws Exception{String path="/admin/practice/sets/"+set.get("id").asText();for(var q:set.get("questions"))set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","QUESTION","questionKey",q.get("key").asText(),"reviewed",true)));return success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","SOURCE","reviewed",true)));}
 JsonNode published(HttpClient admin,JsonNode set)throws Exception{set=reviewAll(admin,set);return success(call(admin,"POST","/admin/practice/sets/"+set.get("id").asText()+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())));}
 @AfterEach void cleanupPractice(){
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='practice_set'",Boolean.class)))return;
  for(String name:users)for(Long uid:jdbc.queryForList("SELECT id FROM identity_user WHERE username=?",Long.class,name)){
   String sets="SELECT id FROM practice_set WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)";
   jdbc.update("DELETE FROM practice_request WHERE user_id=? OR set_id IN ("+sets+") OR attempt_id IN (SELECT id FROM practice_attempt WHERE user_id=? OR set_id IN ("+sets+"))",uid,uid,uid,uid,uid,uid);
   jdbc.update("DELETE FROM practice_mistake WHERE user_id=? OR set_id IN ("+sets+")",uid,uid,uid);
   jdbc.update("DELETE a FROM practice_answer a JOIN practice_attempt t ON t.id=a.attempt_id WHERE t.user_id=? OR t.set_id IN ("+sets+")",uid,uid,uid);
   jdbc.update("UPDATE practice_attempt SET source_attempt_id=NULL WHERE user_id=? OR set_id IN ("+sets+")",uid,uid,uid);
   jdbc.update("DELETE FROM practice_attempt WHERE user_id=? OR set_id IN ("+sets+")",uid,uid,uid);
   jdbc.update("UPDATE practice_set SET published_version_id=NULL,status='DRAFT' WHERE id IN (SELECT id FROM ("+sets+") owned)",uid,uid);
   jdbc.update("DELETE FROM practice_set_version WHERE set_id IN ("+sets+")",uid,uid);
   jdbc.update("DELETE FROM practice_set WHERE created_by=? OR article_id IN (SELECT id FROM reading_article WHERE created_by=?)",uid,uid);
  }
 }
}
