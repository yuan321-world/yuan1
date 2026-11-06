package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PracticeMaterialIT extends PracticeHttpITSupport {
 @Test void publishingASetDoesNotRequirePublishingItsReadingSource()throws Exception{
  var admin=actor(true);var reader=actor(false);var a=success(call(admin,"POST","/admin/articles",draft("Draft material "+UUID.randomUUID())));
  var set=published(admin,createSet(admin,a));String sid=set.get("id").asText(),aid=a.get("id").asText();
  assertThat(success(call(admin,"GET","/admin/articles/"+aid,null)).get("status").asText()).isEqualTo("DRAFT");
  assertThat(call(anonymous(),"GET","/reading/articles/"+aid,null).statusCode()).isEqualTo(404);
  var detail=success(call(anonymous(),"GET","/practice/sets/"+sid,null));
  success(call(reader,"POST","/practice/sets/"+sid+"/attempts",Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));
  assertThat(success(call(anonymous(),"GET","/practice/sets?q=Practice%20fixture",null)).get("items").toString()).contains(sid);
 }
 Map<String,Object> ownInput(){var m=metadata();m.put("title","Private material "+UUID.randomUUID());return new LinkedHashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"metadata",m,"body","Small steps can change a day. We learn by trying again.","questions",List.of(question("q1","What helps?")),"expectedLockVersion",0));}
 @Test void ownMaterialIsPrivateIdempotentAndVersionedWithSetReviews()throws Exception{
  var admin=actor(true);var reader=actor(false);var input=ownInput();
  assertThat(call(reader,"POST","/admin/practice/material-sets",input).statusCode()).isEqualTo(403);
  var set=success(call(admin,"POST","/admin/practice/material-sets",input));String path="/admin/practice/sets/"+set.get("id").asText(),aid=set.get("articleId").asText();
  assertThat(set.get("materialOnly").asBoolean()).isTrue();
  var bypass=json.readTree(json.writeValueAsString(draft("Bypass material editor")));var bypassFields=new LinkedHashMap<String,Object>();bypass.properties().forEach(e->bypassFields.put(e.getKey(),e.getValue()));bypassFields.put("expectedLockVersion",0);
  assertThat(call(admin,"PUT","/admin/articles/"+aid+"/draft",bypassFields).statusCode()).isEqualTo(400);
  assertThat(success(call(admin,"POST","/admin/practice/material-sets",input)).get("id")).isEqualTo(set.get("id"));
  var changedRequest=new LinkedHashMap<>(input);changedRequest.put("body","Another body.");assertThat(call(admin,"POST","/admin/practice/material-sets",changedRequest).statusCode()).isEqualTo(409);
  assertThat(call(admin,"POST","/admin/articles/"+aid+"/publish",Map.of("expectedLockVersion",0)).statusCode()).isEqualTo(400);
  assertThat(success(call(admin,"GET","/admin/articles?keyword=Private%20material",null)).get("items").toString()).doesNotContain(aid);
  jdbc.update("UPDATE reading_article_version SET source_url=NULL WHERE id=?",set.get("articleVersionId").asText());
  set=reviewAll(admin,set);input.put("expectedLockVersion",set.get("lockVersion").asInt());
  var unchanged=success(call(admin,"PUT",path+"/material",input));assertThat(unchanged.get("reviewedQuestionKeys").size()).isEqualTo(1);assertThat(unchanged.get("sourceReviewed").asBoolean()).isTrue();
  set=published(admin,unchanged);var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));
  var attempt=success(call(reader,"POST","/practice/sets/"+set.get("id").asText()+"/attempts",Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));
  input.put("expectedLockVersion",set.get("lockVersion").asInt());input.put("body","New material changes the evidence. Read carefully.");
  set=success(call(admin,"PUT",path+"/material",input));assertThat(set.get("sourceReviewed").asBoolean()).isFalse();assertThat(set.get("reviewedQuestionKeys").size()).isZero();
  assertThat(call(admin,"PUT",path+"/material",input).statusCode()).isEqualTo(409);
  var frozen=success(call(reader,"GET","/practice/attempts/"+attempt.get("id").asText(),null));assertThat(frozen.get("article").toString()).contains("Small steps").doesNotContain("New material");
  assertThat(success(call(admin,"GET",path+"/versions/1",null)).get("article").toString()).contains("Small steps");
  assertThat(call(anonymous(),"GET","/reading/articles/"+aid,null).statusCode()).isEqualTo(404);
 }
 @Test void readingUnpublicationDoesNotTakeDownTheIndependentSet()throws Exception{
  var admin=actor(true);var reader=actor(false);var a=article(admin);var set=published(admin,createSet(admin,a));String aid=a.get("id").asText(),sid=set.get("id").asText();
  var latest=success(call(admin,"GET","/admin/articles/"+aid,null));success(call(admin,"POST","/admin/articles/"+aid+"/unpublish",Map.of("expectedLockVersion",latest.get("lockVersion").asInt())));
  var detail=success(call(anonymous(),"GET","/practice/sets/"+sid,null));success(call(reader,"POST","/practice/sets/"+sid+"/attempts",Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",UUID.randomUUID().toString())));
 }
 @Test void materialInputErrorsAreValidationResponsesAndMissingTitlesRemainDraftable()throws Exception{
  var admin=actor(true);var input=ownInput();input.put("body",null);assertThat(call(admin,"POST","/admin/practice/material-sets",input).statusCode()).isEqualTo(400);
  input=ownInput();var m=metadata();m.put("title","");m.put("titleZh","");input.put("metadata",m);var set=success(call(admin,"POST","/admin/practice/material-sets",input));assertThat(set.get("checks").toString()).contains("metadata.title");
  assertThat(call(admin,"POST","/admin/practice/sets/"+set.get("id").asText()+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())).statusCode()).isEqualTo(400);
 }
 @Test void invalidOwnSetRollsBackMaterialAndCannotEditAReadingArticle()throws Exception{
  var admin=actor(true);long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class);var input=ownInput();input.put("questions",List.of());
  assertThat(call(admin,"POST","/admin/practice/material-sets",input).statusCode()).isEqualTo(400);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before);
  var a=article(admin);var set=createSet(admin,a);var update=ownInput();update.put("expectedLockVersion",set.get("lockVersion").asInt());assertThat(call(admin,"PUT","/admin/practice/sets/"+set.get("id").asText()+"/material",update).statusCode()).isEqualTo(400);
 }
}
