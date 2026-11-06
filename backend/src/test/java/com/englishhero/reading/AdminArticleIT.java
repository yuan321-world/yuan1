package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AdminArticleIT extends ReadingHttpITSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ArticleMapper articles;
    @Test void invalidVersionOwnershipAndFailedPointerUpdateCannotLeaveHalfAnArticle()throws Exception {
        var admin=actor(true);
        var one=json.readTree(call(admin,"POST","/admin/articles",draft("One")).body());
        var two=json.readTree(call(admin,"POST","/admin/articles",draft("Two")).body());
        assertThatThrownBy(()->jdbc.update("UPDATE reading_article SET draft_version_id=? WHERE id=?",two.get("draftVersionId").asText(),one.get("id").asText())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Test pointer failure")).when(articles).initialDraft(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong());
        try { assertThat(call(admin,"POST","/admin/articles",draft("Must roll back")).statusCode()).isEqualTo(500); }
        finally { org.mockito.Mockito.reset(articles); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article",Long.class)).isEqualTo(before);
    }
    @Test void adminCreatesImmutableDraftWhileLearnerCannotUseManagement()throws Exception {
        var admin=actor(true);var learner=actor(false);
        assertThat(call(anonymous(),"GET","/admin/articles",null).statusCode()).isEqualTo(401);
        assertThat(call(learner,"POST","/admin/articles",draft("Private draft")).statusCode()).isEqualTo(403);
        var created=call(admin,"POST","/admin/articles",draft("A small beginning"));
        assertThat(created.statusCode()).isEqualTo(201);
        var value=json.readTree(created.body());String id=value.get("id").asText();String version=value.get("draftVersionId").asText();
        assertThat(value.get("status").asText()).isEqualTo("DRAFT");assertThat(value.get("lockVersion").asInt()).isZero();
        var preview=call(admin,"GET","/admin/articles/"+id+"/preview?versionId="+version,null);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(json.readTree(preview.body()).get("content").get("paragraphs").size()).isEqualTo(1);
        assertThat(call(learner,"GET","/admin/articles/"+id,null).statusCode()).isEqualTo(403);
    }
    @Test void concurrentDraftSavesDoNotOverwriteOneAnother()throws Exception {
        var admin=actor(true);var created=call(admin,"POST","/admin/articles",draft("Original"));
        assertThat(created.statusCode()).isEqualTo(201);
        var value=json.readTree(created.body());String id=value.get("id").asText(),oldVersion=value.get("draftVersionId").asText();
        var draft=draft("Changed");var update=new ReadingDtos.DraftUpdate(draft.categoryId(),draft.title(),draft.titleZh(),draft.summary(),draft.difficulty(),draft.body(),draft.author(),draft.sourceType(),draft.sourceUrl(),draft.usageNote(),0);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var gate=new java.util.concurrent.CyclicBarrier(2);
            var a=pool.submit(()->{gate.await();return call(admin,"PUT","/admin/articles/"+id+"/draft",update).statusCode();});
            var b=pool.submit(()->{gate.await();return call(admin,"PUT","/admin/articles/"+id+"/draft",update).statusCode();});
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_article_version WHERE article_id=?",Integer.class,id)).isEqualTo(2);
        var old=call(admin,"GET","/admin/articles/"+id+"/preview?versionId="+oldVersion,null);
        assertThat(json.readTree(old.body()).get("title").asText()).isEqualTo("Original");
    }
}
