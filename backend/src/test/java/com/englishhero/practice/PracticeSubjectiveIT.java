package com.englishhero.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishhero.identity.IdentityService;
import com.englishhero.identity.TestEmailConfiguration;
import com.englishhero.identity.token.TokenTestSupport;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
class PracticeSubjectiveIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'subj!_%' ESCAPE '!'", String.class));
        cleanFixtures();
        users.clear();
    }

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        TokenTestSupport.register(registry);registry.add("english-hero.practice.audio-directory",()->java.nio.file.Path.of("target/listening-it-audio").toAbsolutePath().toString());
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        assertThat(properties.getProperty("spring.datasource.url"))
            .matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        for (String key : List.of("url", "username", "password"))
            registry.add("spring.datasource." + key, () -> properties.getProperty("spring.datasource." + key));
    }

    @Test
    void subjectiveLifecycleFreezesReferencesAndIsolatesUsers() throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var input=new LinkedHashMap<String,Object>();
        input.put("readingType","WRITING");input.put("metadata",Map.of("title","Synthetic writing fixture","titleZh","写作测试夹具非真题","sourceKind","ORIGINAL","topic","education","sourceName","test","sourceUrl","","usageNote","Synthetic integration fixture only."));
        input.put("subjective",Map.of("prompt","Write a short proposal for a shared study space.","requirements","Explain your suggestion.","minWords",3,"maxWords",10,"minutes",30,"reference","SECRET REFERENCE frozen original","notes","SECRET NOTES frozen original"));
        var created=call(admin,"POST","/admin/practice/subjective-sets",input);
        var set=success(created);String sp="/admin/practice/sets/"+set.get("id").asText();
        assertThat(call(learner,"POST","/admin/practice/subjective-sets",input).statusCode()).isEqualTo(403);
        assertThat(call(admin,"POST",sp+"/publish",lock(set)).statusCode()).isEqualTo(400);
        for(String kind:List.of("CONTENT","SOURCE"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
        set=success(call(admin,"POST",sp+"/publish",lock(set)));
        var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));
        assertThat(detail.toString()).doesNotContain("SECRET","reference","notes");
        if(Boolean.getBoolean("practice.subjective.browser"))browser(actor(false),set,detail);
        assertThat(success(call(anonymous(),"GET","/practice/sets?section=writing",null)).toString()).contains("Synthetic writing fixture");
        var start=Map.of("requestId",requestId(),"setVersionId",detail.get("setVersionId").asText());
        String startPath="/practice/sets/"+set.get("id").asText()+"/subjective-attempts";
        assertThat(call(anonymous(),"POST",startPath,start).statusCode()).isEqualTo(401);
        var attempt=success(call(learner,"POST",startPath,start));String ap="/practice/subjective-attempts/"+attempt.get("id").asText();
        assertThat(success(call(learner,"POST",startPath,start)).get("id").asText()).isEqualTo(attempt.get("id").asText());
        assertThat(attempt.toString()).doesNotContain("SECRET","reference","notes","correctCount");
        assertThat(call(other,"GET",ap,null).statusCode()).isEqualTo(404);
        assertThat(call(other,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText","Other text")).statusCode()).isEqualTo(404);
        attempt=success(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText","Short")));
        assertThat(call(learner,"POST",ap+"/submit",Map.of("expectedVersion",1,"requestId",requestId(),"confirmWordRange",false)).statusCode()).isEqualTo(400);
        var draft=Map.of("expectedVersion",1,"answerText","A quiet study-space helps students.");
        attempt=success(call(learner,"PUT",ap+"/draft",draft));assertThat(attempt.get("wordCount").asInt()).isEqualTo(5);
        assertThat(call(learner,"PUT",ap+"/draft",draft).statusCode()).isEqualTo(409);
        assertThat(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",2,"answerText","x".repeat(20001))).statusCode()).isEqualTo(400);
        input.put("expectedLockVersion",set.get("lockVersion").asInt());
        input.put("subjective",Map.of("prompt","Changed prompt draft","requirements","Explain.","minWords",3,"maxWords",10,"minutes",30,"reference","NEW SECRET","notes","NEW NOTES"));
        set=success(call(admin,"PUT",sp+"/subjective",input));
        assertThat(set.get("checks").toString()).contains("contentReview","sourceReview");
        success(call(admin,"POST",sp+"/unpublish",lock(set)));
        assertThat(call(other,"POST",startPath,Map.of("requestId",requestId(),"setVersionId",detail.get("setVersionId").asText())).statusCode()).isEqualTo(404);
        var submit=Map.of("requestId",requestId(),"expectedVersion",attempt.get("version").asInt(),"confirmWordRange",false);
        var done=success(call(learner,"POST",ap+"/submit",submit));
        assertThat(done.toString()).contains("SECRET REFERENCE frozen original","SECRET NOTES frozen original","shared study space").doesNotContain("NEW SECRET","correctCount");
        assertThat(success(call(learner,"POST",ap+"/submit",submit)).get("id").asText()).isEqualTo(done.get("id").asText());
        assertThat(call(learner,"POST",ap+"/submit",Map.of("requestId",submit.get("requestId"),"expectedVersion",2,"confirmWordRange",true)).statusCode()).isEqualTo(409);
        assertThat(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",2,"answerText","overwrite")).statusCode()).isEqualTo(409);
        var revised=success(call(learner,"POST",ap+"/revise",Map.of("requestId",requestId())));
        assertThat(revised.get("id").asText()).isNotEqualTo(done.get("id").asText());
        assertThat(revised.get("answerText").asText()).isEqualTo(done.get("answerText").asText());
        assertThat(revised.toString()).doesNotContain("SECRET","reference","notes");
        assertThat(success(call(learner,"GET",ap,null)).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(success(call(learner,"GET","/practice/subjective-history?section=writing",null)).get("total").asInt()).isEqualTo(2);
        assertThat(success(call(other,"GET","/practice/subjective-history?section=writing",null)).get("total").asInt()).isZero();
    }

    @Test
    void translationUsesChineseMaterialAndConcurrentSubmitIsIdempotent() throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);
        var input=new LinkedHashMap<String,Object>();input.put("readingType","TRANSLATION");
        input.put("metadata",Map.of("title","Synthetic translation fixture","titleZh","翻译测试夹具非真题","sourceKind","ORIGINAL","topic","life","sourceName","test","sourceUrl","","usageNote","Synthetic test only."));
        input.put("subjective",Map.of("prompt","社区组织周末步行活动。","requirements","汉译英","minWords",120,"maxWords",180,"minutes",15,"reference","SECRET TRANSLATION reference","notes","SECRET translation notes"));
        var set=success(call(admin,"POST","/admin/practice/subjective-sets",input));String sp="/admin/practice/sets/"+set.get("id").asText();
        assertThat(set.at("/subjective/minWords").isNull()).isTrue();
        for(String kind:List.of("CONTENT","SOURCE"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
        set=success(call(admin,"POST",sp+"/publish",lock(set)));
        assertThat(success(call(anonymous(),"GET","/practice/sets?section=translation",null)).get("total").asInt()).isPositive();
        var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));
        if(Boolean.getBoolean("practice.subjective.browser"))browser(actor(false),set,detail);
        var start=Map.of("requestId",requestId(),"setVersionId",detail.get("setVersionId").asText());String startPath="/practice/sets/"+set.get("id").asText()+"/subjective-attempts";
        assertThat(call(learner,"POST","/practice/sets/"+set.get("id").asText()+"/attempts",start).statusCode()).isEqualTo(400);
        var attempt=success(call(learner,"POST",startPath,start));String ap="/practice/subjective-attempts/"+attempt.get("id").asText();
        var forbidden=learner.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+ap+"/draft")).header("Authorization","Bearer "+tokens.get(learner)).header("Origin","http://127.0.0.1:4174").header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{}" )).build(),HttpResponse.BodyHandlers.ofString());assertThat(forbidden.statusCode()).isEqualTo(403);
        attempt=success(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText","只有中文")));
        assertThat(call(learner,"POST",ap+"/submit",Map.of("expectedVersion",1,"requestId",requestId(),"confirmWordRange",true)).statusCode()).isEqualTo(400);
        attempt=success(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",1,"answerText","The community organizes weekend walks.")));
        var submit=Map.of("expectedVersion",2,"requestId",requestId(),"confirmWordRange",false);
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call(learner,"POST",ap+"/submit",submit);}catch(Exception e){throw new RuntimeException(e);}});
        var second=call(learner,"POST",ap+"/submit",submit);var done=success(first.get());assertThat(success(second).get("id").asText()).isEqualTo(done.get("id").asText());
        assertThat(done.get("reference").asText()).isEqualTo("SECRET TRANSLATION reference");
        assertThat(call(other,"POST",ap+"/submit",Map.of("expectedVersion",2,"requestId",requestId(),"confirmWordRange",true)).statusCode()).isEqualTo(404);
        assertThat(call(other,"POST",ap+"/revise",Map.of("requestId",requestId())).statusCode()).isEqualTo(404);
        assertThat(success(call(learner,"GET","/practice/subjective-history?section=translation&source=ORIGINAL&state=COMPLETED",null)).get("total").asInt()).isEqualTo(1);
        assertThat(success(call(learner,"GET","/practice/subjective-history?section=writing",null)).get("total").asInt()).isZero();
        assertThat(success(call(admin,"GET",sp+"/versions",null)).toString()).contains("SECRET TRANSLATION reference");
    }

    private void browser(HttpClient learner,JsonNode set,JsonNode detail) throws Exception {
        var process=new ProcessBuilder("node","../frontend/tests/real/practice-subjective.cjs");var env=process.environment();
        env.put("SUBJECTIVE_TEST_BASE","http://127.0.0.1:"+port);env.put("SUBJECTIVE_TEST_TOKEN",tokens.get(learner));env.put("SUBJECTIVE_TEST_SET",set.get("id").asText());env.put("SUBJECTIVE_TEST_VERSION",detail.get("setVersionId").asText());env.put("SUBJECTIVE_TEST_TYPE",set.get("readingType").asText());
        var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("SUBJECTIVE_TEST_COOKIES",json.writeValueAsString(cookies));
        var output=java.nio.file.Path.of("../.tool-cache/practice-subjective-implementation/real-"+set.get("readingType").asText()+".log").toFile();var running=process.redirectErrorStream(true).redirectOutput(output).start();boolean ended=running.waitFor(150,java.util.concurrent.TimeUnit.SECONDS);if(!ended)running.destroyForcibly();assertThat(ended).isTrue();assertThat(running.exitValue()).as("Real browser: inspect private log").isZero();
    }

    HttpClient actor(boolean admin) throws Exception {
        String username = "subj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        users.add(username);
        String password = UUID.randomUUID() + "aA!";
        if (admin) identities.bootstrap(username, password, "MATCH Integration Admin");
        else identities.register(username, password, "MATCH Integration Learner");
        HttpClient client = anonymous();
        assertThat(call(client, "POST", "/auth/login", Map.of("username", username, "password", password)).statusCode()).isEqualTo(200);
        usernames.put(client, username);
        return client;
    }

    HttpClient anonymous() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    HttpResponse<String> call(HttpClient client, String method, String path, Object body) throws Exception {
        String csrf = null;
        if (!method.equals("GET")) csrf = json.readTree(call(client, "GET", "/auth/csrf", null).body()).get("token").asText();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .header("Origin", "http://127.0.0.1:4174");
        if (tokens.containsKey(client)) request.header("Authorization", "Bearer " + tokens.get(client));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        var response = client.send(request.method(method,
            body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(), HttpResponse.BodyHandlers.ofString());
        if (path.equals("/auth/login") && response.statusCode() == 200)
            tokens.put(client, json.readTree(response.body()).get("accessToken").asText());
        return response;
    }

    JsonNode success(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage("HTTP %s: %s", response.statusCode(), response.body()).isIn(200, 201);
        return json.readTree(response.body());
    }

    private Map<String, Object> lock(JsonNode set) { return Map.of("expectedLockVersion", set.get("lockVersion").asInt()); }

    private String draftChoice(JsonNode attempt, String id) {
        var answer = java.util.stream.StreamSupport.stream(attempt.get("answers").spliterator(), false)
            .filter(a -> a.get("questionId").asText().equals(id)).findFirst().orElseThrow();
        var choice = answer.get("draftChoice");
        return choice == null || choice.isNull() ? null : choice.asText();
    }

    private boolean flagged(JsonNode attempt, String id) {
        return java.util.stream.StreamSupport.stream(attempt.get("answers").spliterator(), false)
            .filter(a -> a.get("questionId").asText().equals(id)).findFirst().orElseThrow().get("flagged").asBoolean();
    }

    private String requestId() { return UUID.randomUUID().toString(); }

    @AfterEach
    void cleanFixtureUsers() {
        cleanFixtures();
    }

    void cleanFixtures() {
        for (String name : users) {
            var ids = jdbc.queryForList("SELECT id FROM identity_user WHERE username=?", Long.class, name);
            for (Long uid : ids) {
                String articles = "SELECT id FROM reading_article WHERE created_by=?";
                String sets = "SELECT id FROM practice_set WHERE article_id IN (" + articles + ")";
                jdbc.update("DELETE FROM practice_request WHERE user_id=? OR set_id IN (" + sets + ") OR attempt_id IN (SELECT id FROM practice_attempt WHERE user_id=? OR set_id IN (" + sets + "))", uid, uid, uid, uid);
                jdbc.update("DELETE FROM practice_mistake WHERE user_id=? OR set_id IN (" + sets + ")", uid, uid);
                jdbc.update("DELETE a FROM practice_answer a JOIN practice_attempt t ON t.id=a.attempt_id WHERE t.user_id=? OR t.set_id IN (" + sets + ")", uid, uid);
                jdbc.update("UPDATE practice_attempt SET source_attempt_id=NULL WHERE user_id=? OR set_id IN (" + sets + ")", uid, uid);
                jdbc.update("DELETE FROM practice_attempt WHERE user_id=? OR set_id IN (" + sets + ")", uid, uid);
                if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='practice_subjective_attempt'",Integer.class)>0){jdbc.update("DELETE FROM practice_subjective_request WHERE user_id=? OR attempt_id IN (SELECT id FROM practice_subjective_attempt WHERE set_id IN ("+sets+"))",uid,uid);
                jdbc.update("UPDATE practice_subjective_attempt SET parent_attempt_id=NULL WHERE user_id=? OR set_id IN ("+sets+")",uid,uid);
                jdbc.update("DELETE FROM practice_subjective_attempt WHERE user_id=? OR set_id IN ("+sets+")",uid,uid);}
                jdbc.update("UPDATE practice_set SET published_version_id=NULL,status='DRAFT' WHERE id IN (SELECT id FROM (" + sets + ") owned)", uid);
                jdbc.update("DELETE FROM practice_set_version WHERE set_id IN (" + sets + ")", uid);
                jdbc.update("DELETE FROM practice_set WHERE article_id IN (" + articles + ")", uid);
                jdbc.update("DELETE q FROM reading_question q JOIN reading_quiz_version v ON v.id=q.quiz_version_id JOIN reading_quiz z ON z.id=v.quiz_id WHERE z.article_id IN (" + articles + ")", uid);
                jdbc.update("UPDATE reading_quiz SET published_version_id=NULL,status='DRAFT' WHERE article_id IN (" + articles + ")", uid);
                jdbc.update("DELETE v FROM reading_quiz_version v JOIN reading_quiz z ON z.id=v.quiz_id WHERE z.article_id IN (" + articles + ")", uid);
                jdbc.update("DELETE FROM reading_quiz WHERE article_id IN (" + articles + ")", uid);
                jdbc.update("UPDATE reading_article SET draft_version_id=NULL,published_version_id=NULL WHERE created_by=?", uid);
                jdbc.update("DELETE v FROM reading_article_version v JOIN reading_article a ON a.id=v.article_id WHERE a.created_by=?", uid);
                jdbc.update("DELETE FROM reading_article WHERE created_by=?", uid);
                for(String key:jdbc.queryForList("SELECT storage_key FROM practice_audio_asset WHERE created_by=?",String.class,uid))java.nio.file.Path.of("target/listening-it-audio",key).toFile().delete();
                jdbc.update("DELETE FROM practice_audio_asset WHERE created_by=?",uid);
                jdbc.update("DELETE FROM identity_user WHERE id=?", uid);
            }
        }
    }
}

