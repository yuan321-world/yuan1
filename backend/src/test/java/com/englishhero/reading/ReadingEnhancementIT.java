package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReadingEnhancementIT extends ReadingHttpITSupport {
    @Test void historyFiltersOwnedVersionsBeforePagination() throws Exception {
        var admin=actor(true);var one=actor(false);var two=actor(false);
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("History searchable sample")).body()).get("id").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var started=json.readTree(call(one,"POST","/reading/articles/"+id+"/start",null).body());
        String category=started.get("version").get("category").get("id").asText();
        assertThat(json.readTree(call(one,"GET","/reading/history?keyword=unmatched",null).body()).get("total").asInt()).isZero();
        var result=call(one,"GET","/reading/history?keyword=searchable&difficulty=B1&categoryId="+category+"&sort=short&pageSize=1",null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(json.readTree(result.body()).get("total").asInt()).isEqualTo(1);
        var longer=new ReadingDtos.DraftInput("1","History searchable longer","更长的历史文章","A different introduction.","B2","A longer example with many more useful words for a careful reader to study every single day.","Lingua Loop","ORIGINAL",null,"原创练习");
        String second=json.readTree(call(admin,"POST","/admin/articles",longer).body()).get("id").asText();
        call(admin,"POST","/admin/articles/"+second+"/publish",new ReadingDtos.LockInput(0));
        call(one,"POST","/reading/articles/"+second+"/start",null);
        var shortPage=json.readTree(call(one,"GET","/reading/history?keyword=searchable&sort=short&pageSize=1",null).body());
        assertThat(shortPage.get("total").asInt()).isEqualTo(2);
        assertThat(shortPage.get("items").get(0).get("article").get("id").asText()).isEqualTo(id);
        assertThat(json.readTree(call(one,"GET","/reading/history?sort=long&pageSize=1",null).body()).get("items").get(0).get("article").get("id").asText()).isEqualTo(second);
        assertThat(json.readTree(call(one,"GET","/reading/history?sort=short&pageSize=1&page=2",null).body()).get("items").get(0).get("article").get("id").asText()).isEqualTo(second);
        assertThat(json.readTree(call(one,"GET","/reading/history?difficulty=B2",null).body()).get("total").asInt()).isEqualTo(1);
        assertThat(json.readTree(call(one,"GET","/reading/history?keyword=%25",null).body()).get("total").asInt()).isZero();
        assertThat(json.readTree(call(two,"GET","/reading/history?keyword=searchable",null).body()).get("total").asInt()).isZero();
        assertThat(call(one,"GET","/reading/history?sort=invalid",null).statusCode()).isEqualTo(400);
        assertThat(call(one,"GET","/reading/history?difficulty=C2",null).statusCode()).isEqualTo(400);
        call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1));
        assertThat(json.readTree(call(one,"GET","/reading/history?keyword=searchable&difficulty=B1&sort=long",null).body()).get("total").asInt()).isEqualTo(1);
    }
    @Test void publicReadNeverCreatesProgressAndPrivateEndpointsRemainProtected() throws Exception {
        var admin=actor(true);var guest=anonymous();
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("Public reading sample")).body()).get("id").asText();
        assertThat(call(guest,"GET","/reading/articles/"+id,null).statusCode()).isEqualTo(404);
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        var response=call(guest,"GET","/reading/articles/"+id,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("progress").isNull()).isTrue();
        assertThat(call(guest,"GET","/reading/articles?difficulty=B1&sort=short&keyword=Public",null).statusCode()).isEqualTo(200);
        assertThat(call(guest,"GET","/reading/articles?difficulty=C2",null).statusCode()).isEqualTo(400);
        assertThat(call(guest,"GET","/reading/history",null).statusCode()).isEqualTo(401);
        assertThat(call(guest,"PUT","/reading/articles/"+id+"/favorite",null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress WHERE article_id=?",Integer.class,id)).isZero();
        assertThat(call(guest,"GET","/dictionary/lookup?word=study",null).statusCode()).isEqualTo(200);
    }
    @Test void favoritesAreIdempotentOwnedAndDoNotExposeDraftOrOldVersions() throws Exception {
        var admin=actor(true);var one=actor(false);var two=actor(false);var guest=anonymous();
        String id=json.readTree(call(admin,"POST","/admin/articles",draft("Favorite sample")).body()).get("id").asText();
        call(admin,"POST","/admin/articles/"+id+"/publish",new ReadingDtos.LockInput(0));
        assertThat(call(one,"PUT","/reading/articles/"+id+"/favorite",null).statusCode()).isEqualTo(200);
        assertThat(call(one,"PUT","/reading/articles/"+id+"/favorite",null).statusCode()).isEqualTo(200);
        var sameUser=anonymous();access.put(sameUser,access.get(one));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->call(one,"PUT","/reading/articles/"+id+"/favorite",null).statusCode());
            var second=pool.submit(()->call(sameUser,"PUT","/reading/articles/"+id+"/favorite",null).statusCode());
            assertThat(first.get()).isEqualTo(200);assertThat(second.get()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_article_favorite WHERE article_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(json.readTree(call(one,"GET","/reading/favorites/states?ids="+id,null).body()).get(0).asText()).isEqualTo(id);
        assertThat(json.readTree(call(one,"GET","/reading/favorites",null).body()).get("total").asInt()).isEqualTo(1);
        assertThat(json.readTree(call(two,"GET","/reading/favorites",null).body()).get("total").asInt()).isZero();
        assertThat(call(two,"DELETE","/reading/articles/"+id+"/favorite",null).statusCode()).isEqualTo(200);
        assertThat(json.readTree(call(one,"GET","/reading/favorites",null).body()).get("total").asInt()).isEqualTo(1);
        var initial=json.readTree(call(one,"POST","/reading/articles/"+id+"/start",null).body());String vid=initial.get("version").get("id").asText();
        call(admin,"POST","/admin/articles/"+id+"/unpublish",new ReadingDtos.LockInput(1));
        assertThat(call(guest,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(404);
        assertThat(call(two,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(404);
        assertThat(call(one,"GET","/reading/articles/"+id+"/versions/"+vid,null).statusCode()).isEqualTo(200);
        assertThat(call(one,"DELETE","/reading/articles/"+id+"/favorite",null).statusCode()).isEqualTo(200);
    }
}
