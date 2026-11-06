package com.englishhero.reading;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WritingMaterialIT extends ReadingHttpITSupport {
    @Test void oldVersionRequiresOwnedHistoryAndNeverAttachesMaterialToNewVersion()throws Exception{
        var admin=actor(true);var reader=actor(false);var outsider=actor(false);var guest=anonymous();
        var original=draft("Writing versions");var article=json.readTree(call(admin,"POST","/admin/articles",original).body());String aid=article.get("id").asText(),vid=article.get("draftVersionId").asText();
        call(admin,"POST","/admin/articles/"+aid+"/publish",new ReadingDtos.LockInput(0));call(reader,"POST","/reading/articles/"+aid+"/start",null);
        var created=call(admin,"POST","/admin/articles/"+aid+"/versions/"+vid+"/writing-materials",input("Original version expression"));String material=json.readTree(created.body()).get("id").asText();
        call(admin,"POST","/admin/writing-materials/"+material+"/publish",new ReadingDtos.LockInput(0));
        var update=new ReadingDtos.DraftUpdate(original.categoryId(),original.title(),original.titleZh(),original.summary(),original.difficulty(),"A different article body for the next version.",original.author(),original.sourceType(),original.sourceUrl(),original.usageNote(),1);
        var changed=json.readTree(call(admin,"PUT","/admin/articles/"+aid+"/draft",update).body());String next=changed.get("draftVersionId").asText();call(admin,"POST","/admin/articles/"+aid+"/publish",new ReadingDtos.LockInput(2));
        String base="/reading/articles/"+aid+"/versions/";
        assertThat(call(guest,"GET",base+vid+"/writing-materials",null).statusCode()).isEqualTo(404);
        assertThat(call(outsider,"GET",base+vid+"/writing-materials",null).statusCode()).isEqualTo(404);
        assertThat(call(reader,"GET",base+vid+"/writing-materials",null).body()).contains("Original version expression");
        assertThat(json.readTree(call(guest,"GET",base+next+"/writing-materials",null).body()).size()).isZero();
        var invalid=new HashMap<String,Object>(input("   "));assertThat(call(admin,"POST","/admin/articles/"+aid+"/versions/"+next+"/writing-materials",invalid).statusCode()).isEqualTo(400);
    }
    Map<String,Object> input(String expression){return Map.of("expression",expression,"meaningZh","与其问，不如问","keyPhrases",List.of("instead of"),"structure","Instead of + doing","exampleEn","Instead of waiting, we can start.","exampleZh","与其等待，不如开始。","scenarios",List.of("议论文"),"sortOrder",1);}
    @Test void administratorPublishesOnlySnapshotAndRejectsOtherRolesAndVersionMismatch()throws Exception{
        var admin=actor(true);var reader=actor(false);var guest=anonymous();
        var article=json.readTree(call(admin,"POST","/admin/articles",ArticleRulesTest.draft("Writing fixture","Instead of waiting, we can start.","ORIGINAL",null)).body());
        String aid=article.get("id").asText(),vid=article.get("draftVersionId").asText();
        String path="/admin/articles/"+aid+"/versions/"+vid+"/writing-materials",pub="/reading/articles/"+aid+"/versions/"+vid+"/writing-materials";
        assertThat(call(reader,"POST",path,input("Instead of …")).statusCode()).isEqualTo(403);
        var created=call(admin,"POST",path,input("Instead of …"));assertThat(created.statusCode()).isEqualTo(201);
        String id=json.readTree(created.body()).get("id").asText(),target="/admin/writing-materials/"+id;
        assertThat(call(guest,"GET",pub,null).statusCode()).isEqualTo(404);
        call(admin,"POST","/admin/articles/"+aid+"/publish",new ReadingDtos.LockInput(0));
        assertThat(json.readTree(call(guest,"GET",pub,null).body()).size()).isZero();
        assertThat(call(admin,"POST",target+"/publish",Map.of("expectedLockVersion",0)).statusCode()).isEqualTo(200);
        assertThat(call(guest,"GET",pub,null).body()).contains("Instead of").doesNotContain("lockVersion","draft");
        var update=new HashMap<String,Object>(input("Updated unpublished expression"));update.put("expectedLockVersion",1);
        assertThat(call(admin,"PUT",target,update).statusCode()).isEqualTo(200);
        assertThat(call(guest,"GET",pub,null).body()).doesNotContain("Updated unpublished");
        assertThat(call(admin,"PUT",target,update).statusCode()).isEqualTo(409);
        assertThat(call(admin,"POST",target+"/publish",Map.of("expectedLockVersion",2)).statusCode()).isEqualTo(200);
        assertThat(call(guest,"GET",pub,null).body()).contains("Updated unpublished");
        assertThat(call(admin,"GET","/admin/articles/"+aid+"/versions/999999999/writing-materials",null).statusCode()).isEqualTo(404);
        assertThat(call(admin,"POST",target+"/unpublish",Map.of("expectedLockVersion",3)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(call(guest,"GET",pub,null).body()).size()).isZero();
        assertThat(call(admin,"DELETE",target,Map.of("expectedLockVersion",4)).statusCode()).isEqualTo(204);
        assertThat(call(admin,"POST",target+"/publish",Map.of("expectedLockVersion",5)).statusCode()).isEqualTo(404);
        jdbc.update("DELETE FROM reading_writing_material WHERE article_id=?",aid);
    }
}
