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
class PracticeMatchIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'match!_%' ESCAPE '!'", String.class));
        cleanFixtures();
        users.clear();
    }

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) {
        TokenTestSupport.register(registry);
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        assertThat(properties.getProperty("spring.datasource.url"))
            .matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        for (String key : List.of("url", "username", "password"))
            registry.add("spring.datasource." + key, () -> properties.getProperty("spring.datasource." + key));
    }

    @Test
    void publishesMatchKeepsAnswersPrivateAndAllowsRepeatedParagraphChoicesAndFlags() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var other = actor(false);
        var catalog = success(call(anonymous(), "GET", "/practice/catalog", null));
        assertThat(catalog.at("/exams/0/types/2/available").asBoolean()).isTrue();
        assertThat(catalog.at("/exams/1/types/2/available").asBoolean()).isFalse();
        var set = createMatch(admin, "[A] First paragraph has evidence.\n\n[B] Second paragraph has context.\n\n[C] Third paragraph has a conclusion.");
        assertThat(set.get("readingType").asText()).isEqualTo("MATCH");
        String adminPath = "/admin/practice/sets/" + set.get("id").asText();
        set = reviewAndPublish(admin, adminPath, set);

        var detail = success(call(anonymous(), "GET", "/practice/sets/" + set.get("id").asText(), null));
        assertThat(detail.get("readingType").asText()).isEqualTo("MATCH");
        assertThat(detail.toString()).doesNotContain("correctOption", "explanation");
        var attempt = startMatch(learner, detail);
        assertThat(attempt.get("total").asInt()).isEqualTo(10);
        assertThat(attempt.toString()).doesNotContain("correctOption", "explanation", "correctCount");
        String attemptPath = "/practice/attempts/" + attempt.get("id").asText();
        var answers = attempt.get("answers");
        String q1 = answers.get(0).get("questionId").asText();
        String q2 = answers.get(1).get("questionId").asText();
        String q3 = answers.get(2).get("questionId").asText();

        attempt = save(learner, attemptPath, attempt, List.of(choice(q1, "A", true), choice(q2, "A", false)), q1);
        assertThat(draftChoice(attempt, q1)).isEqualTo("A");
        assertThat(draftChoice(attempt, q2)).isEqualTo("A");
        assertThat(flagged(attempt, q1)).isTrue();
        assertThat(flagged(attempt, q2)).isFalse();

        int beforeOldClientSave = attempt.get("version").asInt();
        attempt = save(learner, attemptPath, attempt, List.of(choice(q1, "B", null)), q1);
        assertThat(attempt.get("version").asInt()).isEqualTo(beforeOldClientSave + 1);
        assertThat(draftChoice(attempt, q1)).isEqualTo("B");
        assertThat(flagged(attempt, q1)).isTrue();

        beforeOldClientSave = attempt.get("version").asInt();
        attempt = save(learner, attemptPath, attempt, List.of(flagOnly(q1, false)), q2);
        assertThat(attempt.get("version").asInt()).isEqualTo(beforeOldClientSave + 1);
        assertThat(draftChoice(attempt, q1)).isEqualTo("B");
        assertThat(flagged(attempt, q1)).isFalse();

        int beforeInvalidBatch = attempt.get("version").asInt();
        var invalid = call(learner, "PUT", attemptPath + "/draft",
            draftInput(beforeInvalidBatch, List.of(choice(q1, "C", true), choice(q2, "D", true)), q3));
        assertThat(invalid.statusCode()).isEqualTo(400);
        attempt = success(call(learner, "GET", attemptPath, null));
        assertThat(attempt.get("version").asInt()).isEqualTo(beforeInvalidBatch);
        assertThat(draftChoice(attempt, q1)).isEqualTo("B");
        assertThat(flagged(attempt, q1)).isFalse();

        assertThat(call(other, "GET", attemptPath, null).statusCode()).isEqualTo(404);
        assertThat(call(other, "PUT", attemptPath + "/draft", draftInput(attempt.get("version").asInt(),
            List.of(choice(q1, "A", true)), q1)).statusCode()).isEqualTo(404);
        assertThat(call(other, "POST", attemptPath + "/submit", Map.of("expectedVersion", attempt.get("version").asInt(),
            "requestId", requestId(), "confirmUnanswered", true)).statusCode()).isEqualTo(404);
        var submit = Map.of("expectedVersion", attempt.get("version").asInt(), "requestId", requestId(), "confirmUnanswered", true);
        var completed = success(call(learner, "POST", attemptPath + "/submit", submit));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(flagged(completed, q1)).isFalse();
        var mistakes = success(call(learner, "GET", "/practice/mistakes?readingType=MATCH", null));
        assertThat(mistakes.get("total").asInt()).isGreaterThan(0);
        assertThat(mistakes.get("items").get(0).get("readingType").asText()).isEqualTo("MATCH");
        assertThat(call(learner, "PUT", attemptPath + "/draft",
            draftInput(completed.get("version").asInt(), List.of(flagOnly(q1, true)), q1)).statusCode()).isEqualTo(409);

        var retry = success(call(learner, "POST", attemptPath + "/retry-mistakes", Map.of("requestId", requestId())));
        assertThat(retry.get("kind").asText()).isEqualTo("MISTAKES");
        assertThat(retry.get("readingType").asText()).isEqualTo("MATCH");
        assertThat(retry.get("total").asInt()).isEqualTo(10);
        assertThat(retry.get("questions").size()).isEqualTo(10);
    }

    @Test
    void missingParagraphReferencesBlockWholeQuestionReviewWithoutPartialWrites() throws Exception {
        var admin = actor(true);
        var badQuestions = matchInput("[A] Paragraph A.\n\n[B] Paragraph B.");
        @SuppressWarnings("unchecked")
        var questions = new ArrayList<>((List<Map<String, Object>>) badQuestions.get("questions"));
        questions.set(0, question(36, "D"));
        badQuestions.put("questions", questions);
        var set = success(call(admin, "POST", "/admin/practice/material-sets", badQuestions));
        String path = "/admin/practice/sets/" + set.get("id").asText();
        var denied = call(admin, "POST", path + "/review", Map.of("expectedLockVersion", set.get("lockVersion").asInt(), "kind", "QUESTIONS", "reviewed", true));
        assertThat(denied.statusCode()).isEqualTo(400);
        var after = success(call(admin, "GET", path, null));
        assertThat(after.get("lockVersion").asInt()).isEqualTo(set.get("lockVersion").asInt());
        assertThat(after.get("reviewedQuestionKeys")).isEmpty();
        assertThat(after.get("checks").toString()).contains("questions[match-36].correctOption");
        assertThat(call(admin, "POST", path + "/publish", lock(after)).statusCode()).isEqualTo(400);
    }

    @Test
    void matchConcurrentSavesUseTheAttemptVersionAndConcurrentSubmitReplayGradesOnce() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var set = createMatch(admin, "[A] One.\n\n[B] Two.");
        String setPath = "/admin/practice/sets/" + set.get("id").asText();
        set = reviewAndPublish(admin, setPath, set);
        var attempt = startMatch(learner, success(call(anonymous(), "GET", "/practice/sets/" + set.get("id").asText(), null)));
        String path = "/practice/attempts/" + attempt.get("id").asText();
        String question = attempt.get("answers").get(0).get("questionId").asText();
        var first = draftInput(0, List.of(choice(question, "A", true)), question);
        var second = draftInput(0, List.of(choice(question, "B", false)), question);
        var responses = concurrentCalls(learner, "PUT", path + "/draft", first, second);
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        attempt = success(call(learner, "GET", path, null));
        assertThat(attempt.get("version").asInt()).isEqualTo(1);
        var winner = responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow();
        assertThat(flagged(attempt, question)).isEqualTo(flagged(success(winner), question));

        var submit = Map.of("expectedVersion", attempt.get("version").asInt(), "requestId", requestId(), "confirmUnanswered", true);
        var submitted = concurrentCalls(learner, "POST", path + "/submit", submit, submit);
        assertThat(submitted).extracting(HttpResponse::statusCode).containsExactly(200, 200);
        var firstResult = success(submitted.get(0));
        var replay = success(submitted.get(1));
        assertThat(replay.get("id")).isEqualTo(firstResult.get("id"));
        assertThat(replay.get("completedAt")).isEqualTo(firstResult.get("completedAt"));
    }

    @Test
    void editingPassageInvalidatesReviewAndLeavesStartedAttemptFrozen() throws Exception {
        var admin = actor(true);
        var learner = actor(false);
        var set = createMatch(admin, "[A] Frozen first.\n\n[B] Frozen second.");
        String path = "/admin/practice/sets/" + set.get("id").asText();
        set = reviewAndPublish(admin, path, set);
        var attempt = startMatch(learner, success(call(anonymous(), "GET", "/practice/sets/" + set.get("id").asText(), null)));
        String frozenArticle = attempt.get("article").toString();

        var changed = matchInput("[A] Revised first.\n\n[B] Revised second.\n\n[C] A new third.");
        changed.put("requestId", requestId());
        changed.put("expectedLockVersion", set.get("lockVersion").asInt());
        var edited = success(call(admin, "PUT", path + "/material", changed));
        assertThat(edited.get("reviewedQuestionKeys")).isEmpty();
        assertThat(edited.get("sourceReviewed").asBoolean()).isFalse();
        assertThat(call(admin, "POST", path + "/publish", lock(edited)).statusCode()).isEqualTo(400);

        var resumed = success(call(learner, "GET", "/practice/attempts/" + attempt.get("id").asText(), null));
        assertThat(resumed.get("article").toString()).isEqualTo(frozenArticle);
        var oldVersion = success(call(admin, "GET", path + "/versions/1", null));
        assertThat(oldVersion.get("article").toString()).isEqualTo(frozenArticle);
    }

    @Test
    void matchJsonImportPreservesStoredParagraphBoundariesAndRemainsUnreviewed() throws Exception {
        var admin = actor(true);
        String passage = "[A] First imported paragraph.\n\n[B] Second imported paragraph.";
        var articleInput = new LinkedHashMap<String, Object>();
        articleInput.put("categoryId", "1");
        articleInput.put("title", "Imported matching source");
        articleInput.put("titleZh", "导入匹配原文");
        articleInput.put("summary", "Original paragraph labelled material.");
        articleInput.put("difficulty", "B1");
        articleInput.put("body", passage);
        articleInput.put("author", "Local integration fixture");
        articleInput.put("sourceType", "ORIGINAL");
        articleInput.put("sourceUrl", "");
        articleInput.put("usageNote", "Synthetic ORIGINAL fixture only.");
        var article = success(call(admin, "POST", "/admin/articles", articleInput));
        success(call(admin, "POST", "/admin/articles/" + article.get("id").asText() + "/publish", Map.of("expectedLockVersion", 0)));

        var input = new LinkedHashMap<String, Object>();
        input.put("requestId", requestId());
        input.put("articleId", article.get("id").asText());
        input.put("articleVersionId", article.get("draftVersionId").asText());
        input.put("examCode", "CET4");
        input.put("readingType", "MATCH");
        input.put("metadata", matchInput(passage).get("metadata"));
        input.put("questions", matchInput(passage).get("questions"));
        input.put("wordBank", List.of());

        var validation = success(call(admin, "POST", "/admin/practice/import/validate", input));
        assertThat(validation.get("valid").asBoolean()).isTrue();
        var imported = success(call(admin, "POST", "/admin/practice/import", input));
        assertThat(imported.get("readingType").asText()).isEqualTo("MATCH");
        assertThat(imported.get("status").asText()).isEqualTo("DRAFT");
        assertThat(imported.get("reviewedQuestionKeys")).isEmpty();
        assertThat(imported.get("sourceReviewed").asBoolean()).isFalse();
    }

    private JsonNode createMatch(HttpClient admin, String body) throws Exception {
        return success(call(admin, "POST", "/admin/practice/material-sets", matchInput(body)));
    }

    private JsonNode reviewAndPublish(HttpClient admin, String path, JsonNode set) throws Exception {
        set = success(call(admin, "POST", path + "/review", Map.of("expectedLockVersion", set.get("lockVersion").asInt(),
            "kind", "QUESTIONS", "reviewed", true)));
        assertThat(set.get("reviewedQuestionKeys")).hasSize(10);
        set = success(call(admin, "POST", path + "/review", Map.of("expectedLockVersion", set.get("lockVersion").asInt(),
            "kind", "SOURCE", "reviewed", true)));
        return success(call(admin, "POST", path + "/publish", lock(set)));
    }

    private JsonNode startMatch(HttpClient learner, JsonNode detail) throws Exception {
        return success(call(learner, "POST", "/practice/sets/" + detail.get("id").asText() + "/attempts",
            Map.of("setVersionId", detail.get("setVersionId").asText(), "requestId", requestId())));
    }

    private Map<String, Object> matchInput(String body) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("title", "Synthetic ORIGINAL matching passage");
        metadata.put("titleZh", "原创匹配集成材料");
        metadata.put("sourceKind", "ORIGINAL");
        metadata.put("topic", "education");
        metadata.put("year", null);
        metadata.put("month", null);
        metadata.put("paperNo", null);
        metadata.put("sourceName", "");
        metadata.put("sourceUrl", "");
        metadata.put("usageNote", "Synthetic ORIGINAL fixture for local practice only; not a past exam.");
        var input = new LinkedHashMap<String, Object>();
        input.put("requestId", requestId());
        input.put("body", body);
        input.put("metadata", metadata);
        input.put("readingType", "MATCH");
        input.put("questions", java.util.stream.IntStream.range(0, 10).mapToObj(i -> question(36 + i, "A")).toList());
        input.put("expectedLockVersion", 0);
        return input;
    }

    private Map<String, Object> question(int number, String answer) {
        var question = new LinkedHashMap<String, Object>();
        question.put("key", "match-" + number);
        question.put("type", "MATCH");
        question.put("stem", "Statement " + number + " makes a complete matching claim.");
        question.put("options", List.of());
        question.put("correctOption", answer);
        question.put("explanation", "The matching evidence appears in paragraph " + answer + ".");
        return question;
    }

    private Map<String, Object> choice(String questionId, String value, Boolean flagged) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("questionId", questionId);
        if (value != null) choice.put("choice", value);
        if (flagged != null) choice.put("flagged", flagged);
        return choice;
    }

    private Map<String, Object> flagOnly(String questionId, Boolean flagged) {
        var choice = new LinkedHashMap<String, Object>();
        choice.put("questionId", questionId);
        choice.put("flagged", flagged);
        return choice;
    }

    private Map<String, Object> draftInput(int version, List<Map<String, Object>> answers, String current) {
        return Map.of("expectedVersion", version, "answers", answers, "currentQuestionId", current);
    }

    private JsonNode save(HttpClient learner, String path, JsonNode attempt, List<Map<String, Object>> answers, String current) throws Exception {
        return success(call(learner, "PUT", path + "/draft",
            draftInput(attempt.get("version").asInt(), answers, current)));
    }

    private List<HttpResponse<String>> concurrentCalls(HttpClient client, String method, String path,
            Object firstBody, Object secondBody) throws Exception {
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

    private HttpClient actor(boolean admin) throws Exception {
        String username = "match_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
                jdbc.update("DELETE FROM identity_user WHERE id=?", uid);
            }
        }
    }
}
