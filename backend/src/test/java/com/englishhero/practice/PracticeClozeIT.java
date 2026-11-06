package com.englishhero.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishhero.identity.IdentityService;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.identity.TestEmailConfiguration;
import com.englishhero.identity.token.TokenTestSupport;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
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
class PracticeClozeIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'cloze!_%' ESCAPE '!'", String.class));
        cleanOnlyTheseFixtures();
        users.clear();
    }

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        TokenTestSupport.register(registry);
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var p = Objects.requireNonNull(yaml.getObject());
        assertThat(p.getProperty("spring.datasource.url"))
            .matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        for (String key : List.of("url", "username", "password"))
            registry.add("spring.datasource." + key, () -> p.getProperty("spring.datasource." + key));
    }

    @Test
    void wholeClozeReviewChecksSharedBankAndAllAnswersBeforeConfirming()throws Exception{
        var admin=actor(true);var data=materialInput("Synthetic passage "+markers()+" for group review.");
        var set=success(call(admin,"POST","/admin/practice/material-sets",data));String path="/admin/practice/sets/"+set.get("id").asText();
        set=success(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind","QUESTIONS","reviewed",true)));
        assertThat(set.get("reviewedQuestionKeys").size()).isEqualTo(10);assertThat(set.get("sourceReviewed").asBoolean()).isFalse();assertThat(set.get("status").asText()).isEqualTo("DRAFT");
        var bad=materialInput("Synthetic passage "+markers()+" for group review.");var words=new ArrayList<String>((List<String>)bad.get("wordBank"));words.set(14,"");bad.put("wordBank",words);bad.put("expectedLockVersion",set.get("lockVersion").asInt());
        set=success(call(admin,"PUT",path+"/material",bad));int version=set.get("lockVersion").asInt();
        assertThat(call(admin,"POST",path+"/review",Map.of("expectedLockVersion",version,"kind","QUESTIONS","reviewed",true)).statusCode()).isEqualTo(400);
        var after=success(call(admin,"GET",path,null));assertThat(after.get("lockVersion").asInt()).isEqualTo(version);assertThat(after.get("reviewedQuestionKeys")).isEmpty();
    }

    @Test
    void clozePublishesFrozenBankKeepsAnswersPrivateAndRetriesTheWholePassage() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var set = success(call(admin, "POST", "/admin/practice/material-sets", materialInput("The evidence is " + markers() + " in this passage.")));
        assertThat(set.get("readingType").asText()).isEqualTo("CLOZE");
        assertThat(set.get("wordBank").size()).isEqualTo(15);

        String adminPath = "/admin/practice/sets/" + set.get("id").asText();
        assertThat(call(admin, "POST", adminPath + "/publish", lock(set)).statusCode()).isEqualTo(400);
        set = reviewAll(admin, adminPath, set);
        set = success(call(admin, "POST", adminPath + "/publish", lock(set)));
        var detail = success(call(anonymous(), "GET", "/practice/sets/" + set.get("id").asText(), null));
        assertThat(detail.get("readingType").asText()).isEqualTo("CLOZE");
        assertThat(detail.get("wordBank").size()).isEqualTo(15);
        assertThat(detail.toString()).doesNotContain("correctOption", "explanation");

        var attempt = success(call(learner, "POST", "/practice/sets/" + set.get("id").asText() + "/attempts",
            Map.of("setVersionId", detail.get("setVersionId").asText(), "requestId", requestId())));
        assertThat(attempt.get("total").asInt()).isEqualTo(10);
        assertThat(attempt.get("wordBank").size()).isEqualTo(15);
        assertThat(attempt.toString()).doesNotContain("correctOption", "explanation", "correctCount");
        var answers = attempt.get("answers");
        String q1 = answers.get(0).get("questionId").asText();
        String q2 = answers.get(1).get("questionId").asText();
        attempt = success(call(learner, "PUT", "/practice/attempts/" + attempt.get("id").asText() + "/draft",
            Map.of("expectedVersion", attempt.get("version").asInt(), "answers", List.of(choice(q1, "A"), choice(q2, "B")), "currentQuestionId", q1)));
        int savedVersion = attempt.get("version").asInt();

        var duplicate = call(learner, "PUT", "/practice/attempts/" + attempt.get("id").asText() + "/draft",
            Map.of("expectedVersion", savedVersion, "answers", List.of(choice(q1, "B")), "currentQuestionId", q1));
        assertThat(duplicate.statusCode()).isEqualTo(400);
        attempt = success(call(learner, "GET", "/practice/attempts/" + attempt.get("id").asText(), null));
        assertThat(attempt.get("version").asInt()).isEqualTo(savedVersion);
        assertThat(draftChoice(attempt, q1)).isEqualTo("A");
        assertThat(draftChoice(attempt, q2)).isEqualTo("B");

        attempt = success(call(learner, "PUT", "/practice/attempts/" + attempt.get("id").asText() + "/draft",
            Map.of("expectedVersion", savedVersion, "answers", Arrays.asList(choice(q1, null), choice(q2, "A")), "currentQuestionId", q2)));
        assertThat(draftChoice(attempt, q1)).isNull();
        assertThat(draftChoice(attempt, q2)).isEqualTo("A");
        var unanswered = Map.of("expectedVersion", attempt.get("version").asInt(), "requestId", requestId(), "confirmUnanswered", false);
        assertThat(call(learner, "POST", "/practice/attempts/" + attempt.get("id").asText() + "/submit", unanswered).statusCode()).isEqualTo(400);
        attempt = success(call(learner, "POST", "/practice/attempts/" + attempt.get("id").asText() + "/submit",
            Map.of("expectedVersion", attempt.get("version").asInt(), "requestId", requestId(), "confirmUnanswered", true)));
        assertThat(attempt.get("correctCount").asInt()).isZero();

        var retry = success(call(learner, "POST", "/practice/attempts/" + attempt.get("id").asText() + "/retry-mistakes", Map.of("requestId", requestId())));
        assertThat(retry.get("kind").asText()).isEqualTo("MISTAKES");
        assertThat(retry.get("total").asInt()).isEqualTo(10);
        assertThat(retry.get("questions").size()).isEqualTo(10);
        assertThat(retry.get("wordBank")).isEqualTo(detail.get("wordBank"));

        String revisedBody = "The evidence now reads " + markers() + " in this synthetic passage.";
        var bodyOnly = materialInput(revisedBody);
        bodyOnly.put("expectedLockVersion", set.get("lockVersion").asInt());
        var bodyEdited = success(call(admin, "PUT", adminPath + "/material", bodyOnly));
        assertThat(bodyEdited.get("wordBank")).isEqualTo(set.get("wordBank"));
        assertThat(bodyEdited.get("reviewedQuestionKeys")).isEmpty();
        assertThat(bodyEdited.get("sourceReviewed").asBoolean()).isFalse();
        bodyEdited = reviewAll(admin, adminPath, bodyEdited);
        assertThat(bodyEdited.get("reviewedQuestionKeys")).hasSize(10);
        assertThat(bodyEdited.get("sourceReviewed").asBoolean()).isTrue();

        var updated = materialInput(revisedBody);
        updated.put("requestId", requestId());
        var bank = new ArrayList<>((List<String>) updated.get("wordBank"));
        bank.set(0, "replacement");
        updated.put("wordBank", bank);
        updated.put("expectedLockVersion", bodyEdited.get("lockVersion").asInt());
        var edited = success(call(admin, "PUT", adminPath + "/material", updated));
        assertThat(edited.get("reviewedQuestionKeys")).isEmpty();
        assertThat(edited.get("sourceReviewed").asBoolean()).isFalse();
        var oldVersion = success(call(admin, "GET", adminPath + "/versions/1", null));
        assertThat(oldVersion.get("wordBank")).isEqualTo(detail.get("wordBank"));
        var oldAttempt = success(call(learner, "GET", "/practice/attempts/" + attempt.get("id").asText(), null));
        assertThat(oldAttempt.get("wordBank")).isEqualTo(detail.get("wordBank"));
    }

    @Test
    void clozeAttemptsEnforceOwnershipAndSerializeCompetingSameVersionSaves() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var other = actor(false);
        var set = publishedCloze(admin);
        var attempt = startCloze(learner, set);
        String attemptPath = "/practice/attempts/" + attempt.get("id").asText();
        String questionId = attempt.get("questions").get(0).get("id").asText();

        assertThat(call(other, "GET", attemptPath, null).statusCode()).isEqualTo(404);
        assertThat(call(other, "PUT", attemptPath + "/draft",
            Map.of("expectedVersion", 0, "answers", List.of(choice(questionId, "K")), "currentQuestionId", questionId)).statusCode()).isEqualTo(404);
        assertThat(call(other, "POST", attemptPath + "/submit",
            Map.of("expectedVersion", 0, "requestId", requestId(), "confirmUnanswered", true)).statusCode()).isEqualTo(404);

        var firstSave = Map.of("expectedVersion", 0, "answers", List.of(choice(questionId, "K")), "currentQuestionId", questionId);
        var secondSave = Map.of("expectedVersion", 0, "answers", List.of(choice(questionId, "L")), "currentQuestionId", questionId);
        var responses = concurrentCalls(learner, "PUT", attemptPath + "/draft", firstSave, secondSave);
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        var winnerResponse = responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow();
        var winner = success(winnerResponse);
        assertThat(winner.get("version").asInt()).isEqualTo(1);
        attempt = success(call(learner, "GET", attemptPath, null));
        assertThat(attempt.get("version").asInt()).isEqualTo(1);
        assertThat(draftChoice(attempt, questionId)).isIn("K", "L");
        assertThat(draftChoice(attempt, questionId)).isEqualTo(draftChoice(winner, questionId));
    }

    @Test
    void clozeConcurrentSubmitReplayGradesOnlyOnce() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var set = publishedCloze(admin);
        var attempt = startCloze(learner, set);
        String attemptPath = "/practice/attempts/" + attempt.get("id").asText();
        String questionId = attempt.get("questions").get(0).get("id").asText();
        attempt = success(call(learner, "PUT", attemptPath + "/draft",
            Map.of("expectedVersion", attempt.get("version").asInt(), "answers", List.of(choice(questionId, "A")), "currentQuestionId", questionId)));

        var submit = Map.of("expectedVersion", attempt.get("version").asInt(), "requestId", requestId(), "confirmUnanswered", true);
        var responses = concurrentCalls(learner, "POST", attemptPath + "/submit", submit, submit);
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(200, 200);
        var first = success(responses.get(0));
        var concurrentReplay = success(responses.get(1));
        assertThat(concurrentReplay.get("id")).isEqualTo(first.get("id"));
        assertThat(concurrentReplay.get("completedAt")).isEqualTo(first.get("completedAt"));
        assertThat(concurrentReplay.get("correctCount")).isEqualTo(first.get("correctCount"));

        var sequentialReplay = success(call(learner, "POST", attemptPath + "/submit", submit));
        assertThat(sequentialReplay.get("id")).isEqualTo(first.get("id"));
        assertThat(sequentialReplay.get("completedAt")).isEqualTo(first.get("completedAt"));
        Long userId = jdbc.queryForObject("SELECT id FROM identity_user WHERE username=?", Long.class, usernameOf(learner));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_request WHERE user_id=? AND attempt_id=? AND operation='SUBMIT'", Long.class,
            userId, Long.parseLong(attempt.get("id").asText()))).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT MAX(wrong_count) FROM practice_mistake WHERE user_id=? AND set_id=?", Integer.class,
            userId, Long.parseLong(set.get("id").asText()))).isEqualTo(1);
    }

    private JsonNode publishedCloze(HttpClient admin) throws Exception {
        var set = success(call(admin, "POST", "/admin/practice/material-sets", materialInput("Synthetic evidence includes " + markers() + " in context.")));
        String path = "/admin/practice/sets/" + set.get("id").asText();
        set = reviewAll(admin, path, set);
        return success(call(admin, "POST", path + "/publish", lock(set)));
    }

    private JsonNode startCloze(HttpClient learner, JsonNode set) throws Exception {
        var detail = success(call(anonymous(), "GET", "/practice/sets/" + set.get("id").asText(), null));
        return success(call(learner, "POST", "/practice/sets/" + set.get("id").asText() + "/attempts",
            Map.of("setVersionId", detail.get("setVersionId").asText(), "requestId", requestId())));
    }

    private List<HttpResponse<String>> concurrentCalls(HttpClient client, String method, String path, Object firstBody, Object secondBody) throws Exception {
        success(call(client, "GET", "/auth/csrf", null));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("First concurrent request timed out");
                return call(client, method, path, firstBody);
            });
            var second = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Second concurrent request timed out");
                return call(client, method, path, secondBody);
            });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private String usernameOf(HttpClient client) {
        return Objects.requireNonNull(usernames.get(client));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> materialInput(String body) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("title", "Synthetic CLOZE integration passage");
        metadata.put("titleZh", "选词填空集成材料");
        metadata.put("sourceKind", "ORIGINAL");
        metadata.put("topic", "education");
        metadata.put("year", null);
        metadata.put("month", null);
        metadata.put("paperNo", null);
        metadata.put("sourceName", "");
        metadata.put("sourceUrl", "");
        metadata.put("usageNote", "Synthetic ORIGINAL fixture for local integration only; not a past exam.");
        var input = new LinkedHashMap<String, Object>();
        input.put("requestId", requestId());
        input.put("body", body);
        input.put("metadata", metadata);
        input.put("readingType", "CLOZE");
        input.put("wordBank", java.util.stream.IntStream.range(0, 15).mapToObj(i -> "choice" + i).toList());
        input.put("questions", java.util.stream.IntStream.range(0, 10).mapToObj(i -> {
            var q = new LinkedHashMap<String, Object>();
            q.put("key", "hole-" + (26 + i));
            q.put("type", "CLOZE");
            q.put("stem", "" + (26 + i));
            q.put("options", List.of());
            q.put("correctOption", "ABCDEFGHIJ".substring(i, i + 1));
            q.put("explanation", "Answer context " + (26 + i));
            return q;
        }).toList());
        input.put("expectedLockVersion", 0);
        return input;
    }

    private JsonNode reviewAll(HttpClient admin, String path, JsonNode set) throws Exception {
        for (var question : set.get("questions")) {
            set = success(call(admin, "POST", path + "/review", Map.of("expectedLockVersion", set.get("lockVersion").asInt(),
                "kind", "QUESTION", "questionKey", question.get("key").asText(), "reviewed", true)));
        }
        return success(call(admin, "POST", path + "/review", Map.of("expectedLockVersion", set.get("lockVersion").asInt(), "kind", "SOURCE", "reviewed", true)));
    }

    private HttpClient actor(boolean admin) throws Exception {
        String username = "cloze_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        users.add(username);
        String password = UUID.randomUUID() + "aA!";
        if (admin) identities.bootstrap(username, password, "CLOZE Integration Admin");
        else identities.register(username, password, "CLOZE Integration Learner");
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
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).header("Origin", "http://127.0.0.1:4174");
        if (tokens.containsKey(client)) request.header("Authorization", "Bearer " + tokens.get(client));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        var response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        if (path.equals("/auth/login") && response.statusCode() == 200)
            tokens.put(client, json.readTree(response.body()).get("accessToken").asText());
        return response;
    }

    private JsonNode success(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage("HTTP %s: %s", response.statusCode(), response.body()).isIn(200, 201);
        return json.readTree(response.body());
    }

    private Map<String, Object> lock(JsonNode set) { return Map.of("expectedLockVersion", set.get("lockVersion").asInt()); }
    private Map<String, Object> choice(String question, String value) { var answer = new LinkedHashMap<String, Object>(); answer.put("questionId", question); answer.put("choice", value); return answer; }
    private String draftChoice(JsonNode attempt, String id) { var answer = java.util.stream.StreamSupport.stream(attempt.get("answers").spliterator(), false).filter(a -> a.get("questionId").asText().equals(id)).findFirst().orElseThrow();var choice=answer.get("draftChoice");return choice==null||choice.isNull()?null:choice.asText(); }
    private String requestId() { return UUID.randomUUID().toString(); }
    private String markers() { return java.util.stream.IntStream.rangeClosed(26, 35).mapToObj(i -> "{{" + i + "}}").reduce((a, b) -> a + " context " + b).orElseThrow(); }

    @AfterEach
    void cleanOnlyTheseFixtures() {
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
                jdbc.update("DELETE FROM identity_user WHERE id=?", uid);
            }
        }
    }
}
