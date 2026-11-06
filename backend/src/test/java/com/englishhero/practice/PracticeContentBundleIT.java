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
class PracticeContentBundleIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'bundle!_%' ESCAPE '!'", String.class));
        cleanFixtures();
        users.clear();
    }

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        TokenTestSupport.register(registry);registry.add("english-hero.practice.audio-directory",()->java.nio.file.Path.of("target/bundle-it-audio").toAbsolutePath().toString());
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        assertThat(properties.getProperty("spring.datasource.url"))
            .matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        for (String key : List.of("url", "username", "password"))
            registry.add("spring.datasource." + key, () -> properties.getProperty("spring.datasource." + key));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="practice.content.bundle",matches="true")
    void verifiedSourceBundleRunsWithIsolatedLearnersAndFrozenReferences() throws Exception {
        var admin=actor(true);var learner=actor(false);var browserLearner=actor(false);var other=actor(false);
        var bundle=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("practice.content.bundle.file","../.tool-cache/practice-subjective-source/test-bundle.json"))));
        String audio=uploadSourceAudio(admin);var created=new ArrayList<Map<String,Object>>();
        for(var item:bundle){
            boolean subjective=item.get("kind").asText().equals("subjective");String type=item.get("readingType").asText();
            var input=json.convertValue(item,new tools.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});input.remove("kind");input.put("expectedLockVersion",0);input.put("requestId",requestId());if(subjective)input.remove("requestId");
            if(type.equals("LISTENING")){var data=(Map<String,Object>)input.get("listening");data.put("audioAssetId",audio);data.put("audioReviewed",false);}
            var set=success(call(admin,"POST",subjective?"/admin/practice/subjective-sets":"/admin/practice/material-sets",input));String sid=set.get("id").asText(),sp="/admin/practice/sets/"+sid;
            if(type.equals("LISTENING"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","AUDIO","reviewed",true)));
            for(String kind:List.of(subjective?"CONTENT":"QUESTIONS","SOURCE"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
            set=success(call(admin,"POST",sp+"/publish",lock(set)));assertThat(set.get("checks").size()).isZero();
            var detail=success(call(anonymous(),"GET","/practice/sets/"+sid,null));
            assertNoPrivateFields(detail);
            String section=switch(type){case "LISTENING"->"listening";case "WRITING"->"writing";case "TRANSLATION"->"translation";default->"reading";};
            created.add(Map.of("id",sid,"readingType",type,"section",section,"titleZh",item.at("/metadata/titleZh").asText(),"input",item));
            var attempt=success(call(learner,"POST","/practice/sets/"+sid+(subjective?"/subjective-attempts":"/attempts"),Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",requestId())));
            String ap="/practice/"+(subjective?"subjective-attempts/":"attempts/")+attempt.get("id").asText();assertThat(call(other,"GET",ap,null).statusCode()).isEqualTo(404);
            if(subjective){
                assertNoPrivateFields(attempt);String answer=item.at("/subjective/reference").asText();
                attempt=success(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answerText",answer)));assertThat(success(call(learner,"GET",ap,null)).get("answerText").asText()).isEqualTo(answer);
                var done=success(call(learner,"POST",ap+"/submit",Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",requestId(),"confirmWordRange",false)));
                assertThat(done.get("reference").asText()).isEqualTo(item.at("/subjective/reference").asText());assertThat(done.get("notes").asText()).isEqualTo(item.at("/subjective/notes").asText());assertThat(done.toString()).doesNotContain("correctCount");
            }else{
                var choices=new ArrayList<Map<String,Object>>();for(int i=0;i<attempt.get("questions").size();i++)choices.add(Map.of("questionId",attempt.get("questions").get(i).get("id").asText(),"choice",item.get("questions").get(i).get("correctOption").asText()));
                attempt=success(call(learner,"PUT",ap+"/draft",Map.of("expectedVersion",0,"answers",choices,"currentQuestionId",attempt.get("currentQuestionId").asText())));
                var done=success(call(learner,"POST",ap+"/submit",Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",requestId(),"confirmUnanswered",false)));
                assertThat(done.get("correctCount").asInt()).isEqualTo(item.get("questions").size());
                for(int i=0;i<item.get("questions").size();i++)assertThat(done.get("answers").get(i).get("explanation").asText()).isEqualTo(item.get("questions").get(i).get("explanation").asText());
            }
        }
        assertThat(success(call(learner,"GET","/practice/history?section=reading&source=PAST",null)).get("total").asInt()).isEqualTo(4);
        assertThat(success(call(learner,"GET","/practice/history?section=listening&source=PAST",null)).get("total").asInt()).isEqualTo(1);
        assertThat(success(call(learner,"GET","/practice/subjective-history?section=all&source=PAST",null)).get("total").asInt()).isEqualTo(2);
        browser(browserLearner,created);
    }
    static void assertNoPrivateFields(JsonNode response) {
        if (response.isObject()) {
            for (var property : response.properties()) {
                assertThat(property.getKey()).isNotIn("reference", "notes", "correctOption", "explanation");
                assertNoPrivateFields(property.getValue());
            }
        } else if (response.isArray()) {
            for (var child : response) assertNoPrivateFields(child);
        }
    }

    private String uploadSourceAudio(HttpClient admin) throws Exception {
        String csrf=json.readTree(call(admin,"GET","/auth/csrf",null).body()).get("token").asText();String boundary="bundle"+UUID.randomUUID();
        var bytes=new java.io.ByteArrayOutputStream();bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cet4-source.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));bytes.write(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("practice.content.audio.file","../.tool-cache/practice-listening-source/cet4-202406-1.mp3"))));bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/admin/practice/audio-assets")).header("Origin","http://127.0.0.1:4174").header("Authorization","Bearer "+tokens.get(admin)).header("X-CSRF-TOKEN",csrf).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());return success(response).get("id").asText();
    }
    private void browser(HttpClient learner,List<Map<String,Object>> created) throws Exception {
        var process=new ProcessBuilder("node",System.getProperty("practice.content.browser.script","../.tool-cache/practice-subjective-source/browser.cjs"));var env=process.environment();env.put("BUNDLE_BASE","http://127.0.0.1:"+port);env.put("BUNDLE_TOKEN",tokens.get(learner));var sourceFile=java.nio.file.Path.of(System.getProperty("practice.content.output.directory","../.tool-cache/practice-subjective-source"),"isolated-created-sets.json").toAbsolutePath();java.nio.file.Files.writeString(sourceFile,json.writeValueAsString(created));env.put("BUNDLE_SETS_FILE",sourceFile.toString());
        var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("BUNDLE_COOKIES",json.writeValueAsString(cookies));
        var running=process.redirectErrorStream(true).redirectOutput(java.nio.file.Path.of(System.getProperty("practice.content.output.directory","../.tool-cache/practice-subjective-source"),"real-browser.log").toFile()).start();boolean ended=running.waitFor(150,java.util.concurrent.TimeUnit.SECONDS);if(!ended)running.destroyForcibly();assertThat(ended).as("Bounded actual browser").isTrue();assertThat(running.exitValue()).as("Inspect private real-browser.log").isZero();
    }

    private HttpClient actor(boolean admin) throws Exception {
        String username = "bundle_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        users.add(username);
        String password = UUID.randomUUID() + "aA!";
        if (admin) identities.bootstrap(username, password, "Bundle Integration Admin");
        else identities.register(username, password, "Bundle Integration Learner");
        HttpClient client = anonymous();
        assertThat(call(client, "POST", "/auth/login", Map.of("username", username, "password", password)).statusCode()).isEqualTo(200);
        usernames.put(client, username);
        return client;
    }

    private HttpClient anonymous() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private HttpResponse<String> call(HttpClient client, String method, String path, Object body) throws Exception {
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

    private JsonNode success(HttpResponse<String> response) {
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

    private void cleanFixtures() {
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
                for(String key:jdbc.queryForList("SELECT storage_key FROM practice_audio_asset WHERE created_by=?",String.class,uid))java.nio.file.Path.of("target/bundle-it-audio",key).toFile().delete();
                jdbc.update("DELETE FROM practice_audio_asset WHERE created_by=?",uid);
                jdbc.update("DELETE FROM identity_user WHERE id=?", uid);
            }
        }
    }
}

