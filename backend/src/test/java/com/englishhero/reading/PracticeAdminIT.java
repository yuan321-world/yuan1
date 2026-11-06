package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PracticeAdminIT extends PracticeHttpITSupport {
 @Test void wholeGroupReviewIsAtomicVersionedAndDoesNotPublish()throws Exception{
  var admin=actor(true);var a=article(admin);var set=createSet(admin,a);String path="/admin/practice/sets/"+set.get("id").asText();int version=set.get("lockVersion").asInt();
  var input=Map.of("expectedLockVersion",version,"kind","QUESTIONS","reviewed",true);
  assertThat(call(actor(false),"POST",path+"/review",input).statusCode()).isEqualTo(403);
  assertThat(call(anonymous(),"POST",path+"/review",input).statusCode()).isEqualTo(401);
  set=success(call(admin,"POST",path+"/review",input));assertThat(set.get("reviewedQuestionKeys").size()).isEqualTo(2);assertThat(set.get("lockVersion").asInt()).isEqualTo(version+1);assertThat(set.get("sourceReviewed").asBoolean()).isFalse();assertThat(set.get("status").asText()).isEqualTo("DRAFT");assertThat(set.get("revision").asInt()).isZero();
  assertThat(call(admin,"POST",path+"/review",input).statusCode()).isEqualTo(409);
  var bad=question("q2","Missing explanation");bad.put("explanation","");set=success(call(admin,"PUT",path,draftSet(a,set.get("lockVersion").asInt(),List.of(question("q1","What helps?"),bad))));
  var before=set;assertThat(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","QUESTIONS","reviewed",true)).statusCode()).isEqualTo(400);
  set=success(call(admin,"GET",path,null));assertThat(set.get("lockVersion")).isEqualTo(before.get("lockVersion"));assertThat(set.get("reviewedQuestionKeys")).isEqualTo(before.get("reviewedQuestionKeys"));
 }
 @Test void incompleteDraftPersistsButCannotPublish()throws Exception{
  var admin=actor(true);var a=article(admin);var blank=question("q1","");blank.put("correctOption",null);blank.put("explanation","");var set=success(call(admin,"POST","/admin/practice/sets",draftSet(a,0,List.of(blank))));String path="/admin/practice/sets/"+set.get("id").asText();
  assertThat(set.get("checks").size()).isGreaterThan(3);assertThat(call(admin,"POST",path+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())).statusCode()).isEqualTo(400);
  assertThat(call(actor(false),"GET",path,null).statusCode()).isEqualTo(403);assertThat(call(anonymous(),"GET",path,null).statusCode()).isEqualTo(401);
 }
 @Test void reviewHashesInvalidateOnlyChangedQuestionsAndFreezeHistory()throws Exception{
  var admin=actor(true);var a=article(admin);var set=createSet(admin,a);String path="/admin/practice/sets/"+set.get("id").asText();
  set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","SOURCE","reviewed",true)));
  assertThat(call(admin,"POST",path+"/publish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())).statusCode()).isEqualTo(400);
  set=published(admin,set);assertThat(set.get("revision").asInt()).isEqualTo(1);
  var changed=draftSet(a,set.get("lockVersion").asInt(),List.of(question("q1","Changed draft"),question("q2","What should we do?")));set=success(call(admin,"PUT",path,changed));
  assertThat(set.get("reviewedQuestionKeys").toString()).contains("q2").doesNotContain("q1");assertThat(set.get("sourceReviewed").asBoolean()).isFalse();
  var old=success(call(admin,"GET",path+"/versions/1",null));assertThat(old.toString()).contains("What helps?").doesNotContain("Changed draft");
  assertThat(call(admin,"PUT",path,changed).statusCode()).isEqualTo(409);
  set=published(admin,set);assertThat(set.get("revision").asInt()).isEqualTo(2);assertThat(call(admin,"GET",path+"/versions",null).body()).contains("revision");
  assertThat(call(admin,"POST",path+"/unpublish",Map.of("expectedLockVersion",set.get("lockVersion").asInt())).statusCode()).isEqualTo(200);
 }
 @Test void independentQuizzesShareArticleVersionAndLegacyEndpointsRejectPracticeQuiz()throws Exception{
  var admin=actor(true);var reader=actor(false);var a=article(admin);var set=published(admin,createSet(admin,a));String aid=a.get("id").asText(),vid=a.get("draftVersionId").asText();
  var legacy=success(call(admin,"PUT","/admin/articles/"+aid+"/versions/"+vid+"/quiz",Map.of("expectedLockVersion",0,"questions",List.of(question("q1","Article only")))));
  assertThat(legacy.get("draft").toString()).contains("Article only");
  long qid=jdbc.queryForObject("SELECT id FROM reading_quiz WHERE article_id=? AND `usage`='PRACTICE'",Long.class,aid);long qv=jdbc.queryForObject("SELECT published_version_id FROM reading_quiz WHERE id=?",Long.class,qid);
  assertThat(call(reader,"POST","/reading/quizzes/"+qid+"/attempts",Map.of("quizVersionId",""+qv,"clientRequestId",UUID.randomUUID().toString())).statusCode()).isEqualTo(404);
  assertThat(call(admin,"POST","/admin/reading/quizzes/"+qid+"/publish",Map.of("expectedLockVersion",1)).statusCode()).isEqualTo(404);
  assertThat(call(anonymous(),"GET","/reading/articles/"+aid+"/versions/"+vid+"/quiz",null).body()).isEqualTo("null");
  var detail=call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null);assertThat(detail.body()).doesNotContain("correctOption","explanation","draft","reviewedQuestionKeys");
 }
 @Test void catalogFiltersAndSourceDatesAreHonest()throws Exception{
  var admin=actor(true);var a=article(admin);var set=published(admin,createSet(admin,a));
  var catalog=success(call(anonymous(),"GET","/practice/sets?exam=CET4&source=ORIGINAL&page=1&pageSize=5",null));assertThat(catalog.get("total").asInt()).isGreaterThan(0);
  assertThat(call(anonymous(),"GET","/practice/sets?state=COMPLETED",null).statusCode()).isEqualTo(401);
  assertThat(success(call(anonymous(),"GET","/practice/sets?exam=CET6",null)).get("total").asInt()).isZero();
  assertThat(call(admin,"POST","/admin/practice/sets",draftSet(a,0,List.of(question("q1","Duplicate")))).statusCode()).isEqualTo(409);
  var invalid=draftSet(a,set.get("lockVersion").asInt(),List.of(question("q1","Past")));var meta=metadata();meta.put("sourceKind","PAST");invalid.put("metadata",meta);set=success(call(admin,"PUT","/admin/practice/sets/"+set.get("id").asText(),invalid));assertThat(set.get("checks").toString()).contains("year","month","paperNo");
 }
}
