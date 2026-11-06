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
class PracticeListeningIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'listen!_%' ESCAPE '!'", String.class));
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
    void uploadsPublishesStreamsAndSavesWholeListeningWithFrozenPrivateTranscript() throws Exception {
        var admin=actor(true);var learner=actor(false);var other=actor(false);var otherAdmin=actor(true);
        String asset=upload(admin);String secondAsset=upload(admin);
        assertThat(call(learner,"POST","/admin/practice/audio-assets/"+asset+"/playback-ticket",null).statusCode()).isEqualTo(403);
        assertThat(call(otherAdmin,"POST","/admin/practice/audio-assets/"+asset+"/playback-ticket",null).statusCode()).isEqualTo(403);
        var input=new LinkedHashMap<String,Object>();
        input.put("requestId",requestId());input.put("expectedLockVersion",0);input.put("readingType","LISTENING");input.put("body","Synthetic listening transcript with the secret solution. This is a test fixture only.");
        input.put("metadata",Map.of("title","Synthetic listening integration","titleZh","听力测试夹具非真题","sourceKind","ORIGINAL","topic","education","sourceName","test","sourceUrl","","usageNote","Synthetic fixture only, no real exam content."));
        input.put("questions",java.util.stream.IntStream.rangeClosed(1,12).mapToObj(n->Map.of("key","q"+n,"type","LISTENING","stem",n+". Select the test option.","options",List.of("First","Second","Third","Fourth"),"correctOption","A","explanation","Synthetic explanation")).toList());
        input.put("listening",Map.of("audioAssetId",asset,"groups",List.of(Map.of("key","whole","title","整套测试","questionKeys",java.util.stream.IntStream.rangeClosed(1,12).mapToObj(n->"q"+n).toList())),"audioReviewed",true));
        var set=success(call(admin,"POST","/admin/practice/listening-sets",input));String path="/admin/practice/sets/"+set.get("id").asText();
        jdbc.update("UPDATE reading_article SET content_usage='READING' WHERE id=?",Long.parseLong(set.get("articleId").asText()));
        try{var reused=new LinkedHashMap<String,Object>(input);reused.put("articleId",set.get("articleId").asText());reused.put("articleVersionId",set.get("articleVersionId").asText());reused.put("examCode","CET4");reused.remove("body");reused.remove("requestId");for(var rejected:List.of(call(admin,"POST","/admin/practice/sets",reused),call(admin,"PUT",path,reused))){assertThat(rejected.statusCode()).isEqualTo(400);assertThat(json.readTree(rejected.body()).at("/fieldErrors/articleId").asText()).contains("专项专用");}var imported=new LinkedHashMap<String,Object>(reused);imported.remove("body");imported.remove("expectedLockVersion");imported.put("listening",Map.of("audioAssetId",asset,"groups",((Map<?,?>)input.get("listening")).get("groups")));var validation=success(call(admin,"POST","/admin/practice/import/validate",imported));assertThat(validation.get("valid").asBoolean()).isFalse();assertThat(validation.toString()).contains("专项专用");}
        finally{jdbc.update("UPDATE reading_article SET content_usage='PRACTICE' WHERE id=?",Long.parseLong(set.get("articleId").asText()));}
        assertThat(set.at("/listening/audioReviewed").asBoolean()).isFalse();
        assertThat(call(admin,"POST",path+"/publish",lock(set)).statusCode()).isEqualTo(400);
        set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","AUDIO","reviewed",true)));
        set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","QUESTIONS","reviewed",true)));
        set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","SOURCE","reviewed",true)));
        set=success(call(admin,"POST",path+"/publish",lock(set)));
        var detail=success(call(anonymous(),"GET","/practice/sets/"+set.get("id").asText(),null));assertThat(detail.toString()).doesNotContain("secret solution","correctOption","Synthetic explanation");
        var listed=success(call(anonymous(),"GET","/practice/sets?section=listening&source=ORIGINAL",null));assertThat(listed.toString()).contains("听力测试夹具非真题");
        var started=call(learner,"POST","/practice/sets/"+set.get("id").asText()+"/attempts",Map.of("setVersionId",detail.get("setVersionId").asText(),"requestId",requestId()));
        var attempt=success(started);String attemptPath="/practice/attempts/"+attempt.get("id").asText();
        assertThat(attempt.get("total").asInt()).isEqualTo(12);assertThat(attempt.toString()).doesNotContain("secret solution","correctOption","Synthetic explanation");
        assertThat(call(other,"POST",attemptPath+"/audio-ticket",null).statusCode()).isEqualTo(404);
        var ticket=success(call(learner,"POST",attemptPath+"/audio-ticket",null));String audio=ticket.get("url").asText();
        assertThat(ticket.path("sha256").asText()).isEqualTo(jdbc.queryForObject("SELECT sha256 FROM practice_audio_asset WHERE id=?",String.class,Long.parseLong(asset)));
        var range=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+audio)).header("Range","bytes=0-99").GET().build();var streamed=anonymous().send(range,HttpResponse.BodyHandlers.ofByteArray());assertThat(streamed.statusCode()).isEqualTo(206);assertThat(streamed.body()).hasSize(100);
        String invalid=audio.replace("/audio/"+asset+"?","/audio/"+secondAsset+"?");assertThat(anonymous().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+invalid)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        var badRange=anonymous().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+audio)).header("Range","bytes=99999999-").GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(badRange.statusCode()).isEqualTo(416);
        input.put("expectedLockVersion",set.get("lockVersion").asInt());input.put("body","Updated synthetic listening transcript, different from the published version.");input.put("listening",Map.of("audioAssetId",secondAsset,"groups",List.of(Map.of("key","whole","title","整套测试","questionKeys",java.util.stream.IntStream.rangeClosed(1,12).mapToObj(n->"q"+n).toList())),"audioReviewed",true));set=success(call(admin,"PUT",path+"/listening",input));assertThat(set.at("/listening/audioReviewed").asBoolean()).isFalse();assertThat(set.get("reviewedQuestionKeys").size()).isZero();
        assertThat(success(call(learner,"GET",attemptPath,null)).at("/listening/audioAssetId").asText()).isEqualTo(asset);
        if(Boolean.getBoolean("practice.listening.browser"))browser(actor(false),set,detail);
        var answers=new ArrayList<Map<String,Object>>();for(var q:attempt.get("questions"))answers.add(Map.of("questionId",q.get("id").asText(),"choice","A","flagged",true));
        int prior=attempt.get("version").asInt();String current=attempt.get("currentQuestionId").asText();
        var savedInput=Map.of("expectedVersion",prior,"answers",answers,"currentQuestionId",current,"playback",Map.of("positionSeconds",12.5,"rate",1.25));
        attempt=success(call(learner,"PUT",attemptPath+"/draft",savedInput));assertThat(attempt.at("/playback/positionSeconds").asDouble()).isEqualTo(12.5);assertThat(call(learner,"PUT",attemptPath+"/draft",savedInput).statusCode()).isEqualTo(409);
        success(call(admin,"POST",path+"/unpublish",lock(set)));assertThat(call(learner,"POST",attemptPath+"/audio-ticket",null).statusCode()).isEqualTo(200);
        var submit=Map.of("expectedVersion",attempt.get("version").asInt(),"requestId",requestId(),"confirmUnanswered",false);
        var done=success(call(learner,"POST",attemptPath+"/submit",submit));assertThat(done.get("correctCount").asInt()).isEqualTo(12);assertThat(done.toString()).contains("secret solution","correctOption","Synthetic explanation");assertThat(success(call(learner,"POST",attemptPath+"/submit",submit)).get("id").asText()).isEqualTo(done.get("id").asText());
    }
    private void browser(HttpClient learner,JsonNode set,JsonNode detail) throws Exception {
        var process=new ProcessBuilder("node","../frontend/tests/real/practice-listening.cjs");
        var env=process.environment();env.put("LISTENING_TEST_BASE","http://127.0.0.1:"+port);env.put("LISTENING_TEST_TOKEN",tokens.get(learner));env.put("LISTENING_TEST_SET",set.get("id").asText());env.put("LISTENING_TEST_VERSION",detail.get("setVersionId").asText());
        var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("LISTENING_TEST_COOKIES",json.writeValueAsString(cookies));
        var output=java.nio.file.Path.of("../.tool-cache/practice-listening-implementation/real-browser.log").toFile();var running=process.redirectErrorStream(true).redirectOutput(output).start();boolean ended=running.waitFor(150,java.util.concurrent.TimeUnit.SECONDS);if(!ended)running.destroyForcibly();assertThat(ended).as("Real browser completed within timeout").isTrue();assertThat(running.exitValue()).as("Real browser; inspect private real-browser.log").isZero();
    }
    private String upload(HttpClient admin) throws Exception {
        String csrf=json.readTree(call(admin,"GET","/auth/csrf",null).body()).get("token").asText();String boundary="test"+UUID.randomUUID();byte[] data=new byte[417*400];for(int i=0;i<400;i++){data[i*417]=(byte)0xff;data[i*417+1]=(byte)0xfb;data[i*417+2]=(byte)0x90;}
        var bytes=new java.io.ByteArrayOutputStream();bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));bytes.write(data);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/admin/practice/audio-assets")).header("Origin","http://127.0.0.1:4174").header("Authorization","Bearer "+tokens.get(admin)).header("X-CSRF-TOKEN",csrf).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());return success(response).get("id").asText();
    }
    private HttpClient actor(boolean admin) throws Exception {
        String username = "listen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        users.add(username);
        String password = UUID.randomUUID() + "aA!";
        if (admin) identities.bootstrap(username, password, "MATCH Integration Admin");
        else identities.register(username, password, "MATCH Integration Learner");
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

