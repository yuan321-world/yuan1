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
class PracticePaperIT {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PracticePaperMapper observedPapers;
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final List<String> users = new ArrayList<>();
    final Map<HttpClient, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<HttpClient, String> usernames = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void recoverOnlyThisTestsFixtures() {
        users.addAll(jdbc.queryForList("SELECT username FROM identity_user WHERE username LIKE 'paper!_%' ESCAPE '!'", String.class));
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


    @Test void paperLifecycleIsAtomicPrivateAndFrozen() throws Exception {
      var admin=actor(true);var learner=actor(false);var other=actor(false);
      var input=new LinkedHashMap<String,Object>();input.put("title","Synthetic complete paper");input.put("examCode","CET4");input.put("year",2024);input.put("month",6);input.put("sourceNote","Synthetic integration fixture only, not a real examination.");
      var components=new ArrayList<Map<String,String>>();
      for(var slot:PracticePaperRules.SLOTS){var s=fixture(admin,slot);String vid=jdbc.queryForObject("SELECT published_version_id FROM practice_set WHERE id=?",String.class,s.get("id").asText());components.add(Map.of("slot",slot,"setVersionId",vid));}
      input.put("components",components);
      assertThat(call(learner,"POST","/admin/practice/papers",input).statusCode()).isEqualTo(403);
      var paper=success(call(admin,"POST","/admin/practice/papers",input));String pp="/admin/practice/papers/"+paper.get("id").asText();
      assertThat(call(admin,"POST",pp+"/publish",lock(paper)).statusCode()).isEqualTo(400);
      paper=success(call(admin,"POST",pp+"/review",lock(paper)));paper=success(call(admin,"POST",pp+"/publish",lock(paper)));
      org.mockito.Mockito.clearInvocations(observedPapers);
      var list=success(call(anonymous(),"GET","/practice/papers?year=2024&month=6",null));assertThat(list.get("total").asInt()).isPositive();assertThat(list.toString()).doesNotContain("SECRET","correctOption");
      org.mockito.Mockito.verify(observedPapers,org.mockito.Mockito.never()).version(org.mockito.ArgumentMatchers.anyLong());
      var catalogPaper=list.get("items").get(0);
      var publicDetail=success(call(anonymous(),"GET","/practice/papers/"+catalogPaper.get("id").asText(),null));
      assertThat(catalogPaper.get("components")).isEqualTo(publicDetail.get("components"));
      assertThat(catalogPaper.get("componentDetails")).isEqualTo(publicDetail.get("componentDetails"));
      assertThat(catalogPaper.get("available")).isEqualTo(publicDetail.get("available"));

      String startPath="/practice/papers/"+paper.get("id").asText()+"/attempts";var start=Map.of("paperVersionId",paper.get("paperVersionId").asText(),"requestId",requestId());
      assertThat(call(anonymous(),"POST",startPath,start).statusCode()).isEqualTo(401);
      var a=success(call(learner,"POST",startPath,start));String ap="/practice/paper-attempts/"+a.get("id").asText();
      JsonNode translationArticle=null;for(var component:a.get("components"))if(component.path("slot").asText().equals("DETAILED_1"))translationArticle=component.at("/objective/article");
      var translationInput=new LinkedHashMap<String,Object>();translationInput.put("articleId",translationArticle.path("articleId").asText());translationInput.put("versionId",translationArticle.path("id").asText());translationInput.put("text",translationArticle.at("/content/paragraphs/0/sentences/0/text").asText());translationInput.put("mode","TRANSLATION");translationInput.put("paperAttemptId",a.path("id").asText());translationInput.put("slot","DETAILED_1");
      assertThat(call(learner,"POST","/reading/selection-translations/reference",translationInput).statusCode()).isEqualTo(409);
      assertThat(call(other,"POST","/reading/selection-translations/reference",translationInput).statusCode()).isEqualTo(404);
      var personalCatalog=success(call(learner,"GET","/practice/papers?year=2024&month=6",null));
      assertThat(personalCatalog.toString()).contains("\"currentAttemptId\":\""+a.get("id").asText()+"\"");
      assertThat(success(call(other,"GET","/practice/papers?year=2024&month=6",null)).toString()).doesNotContain("currentAttemptId");

      assertThat(a.get("components").size()).isEqualTo(7);assertThat(a.toString()).doesNotContain("SECRET","correctOption","reference","explanation");
      assertThat(success(call(learner,"POST",startPath,start)).get("id").asText()).isEqualTo(a.get("id").asText());
      assertThat(call(other,"GET",ap,null).statusCode()).isEqualTo(404);assertThat(call(other,"POST",ap+"/audio-ticket",null).statusCode()).isEqualTo(404);
      assertThat(call(learner,"POST",ap+"/audio-ticket",null).statusCode()).isEqualTo(200);
      var responses=new ArrayList<Map<String,Object>>();
      for(var c:a.get("components")){String slot=c.get("slot").asText();var choices=new LinkedHashMap<String,String>();if(c.hasNonNull("objective")){int n=0;for(var q:c.get("objective").get("questions")){choices.put(q.get("id").asText(),slot.equals("CLOZE")?""+(char)('A'+n++):"A");}}responses.add(Map.of("slot",slot,"text",slot.equals("WRITING")?"A quiet room supports learning.":slot.equals("TRANSLATION")?"The community organizes walks.":"","choices",choices));}
      var draft=new LinkedHashMap<String,Object>();draft.put("expectedLockVersion",a.get("lockVersion").asInt());draft.put("currentSlot","WRITING");draft.put("responses",responses);
      a=success(call(learner,"PUT",ap+"/draft",draft));assertThat(call(learner,"PUT",ap+"/draft",draft).statusCode()).isEqualTo(409);
      assertThat(success(call(learner,"GET",ap,null)).toString()).contains("quiet room");
      var invalid=new ArrayList<>(responses);invalid.set(2,Map.of("slot","CLOZE","text","","choices",Map.of("99999999","B")));draft.put("expectedLockVersion",a.get("lockVersion").asInt());draft.put("responses",invalid);assertThat(call(learner,"PUT",ap+"/draft",draft).statusCode()).isEqualTo(400);
      assertThat(success(call(learner,"GET",ap,null)).get("lockVersion").asInt()).isEqualTo(a.get("lockVersion").asInt());
      // A new published paper version cannot alter this already started version.
      input.put("expectedLockVersion",paper.get("lockVersion").asInt());input.put("title","New paper title");paper=success(call(admin,"PUT",pp,input));paper=success(call(admin,"POST",pp+"/review",lock(paper)));paper=success(call(admin,"POST",pp+"/publish",lock(paper)));
      assertThat(success(call(learner,"GET",ap,null)).get("title").asText()).isEqualTo("Synthetic complete paper");
      String componentSet=jdbc.queryForObject("SELECT set_id FROM practice_set_version WHERE id=?",String.class,components.get(0).get("setVersionId"));var s=success(call(admin,"GET","/admin/practice/sets/"+componentSet,null));success(call(admin,"POST","/admin/practice/sets/"+componentSet+"/unpublish",lock(s)));
      var unavailableCatalog=success(call(anonymous(),"GET","/practice/papers?year=2024&month=6",null));
      assertThat(unavailableCatalog.get("items").get(0).get("available").asBoolean()).isFalse();
      assertThat(unavailableCatalog.get("items").get(0).get("unavailableReason").asText()).isEqualTo("组成题组已下架，已有练习仍可继续");

      assertThat(call(other,"POST",startPath,Map.of("paperVersionId",paper.get("paperVersionId").asText(),"requestId",requestId())).statusCode()).isEqualTo(409);
      var submit=new LinkedHashMap<String,Object>();submit.put("expectedLockVersion",a.get("lockVersion").asInt());submit.put("currentSlot","WRITING");submit.put("responses",responses);submit.put("requestId",requestId());submit.put("confirmUnanswered",true);
      org.mockito.Mockito.doThrow(new IllegalStateException("Synthetic pointer failure")).when(observedPapers).advance(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.nullable(String.class),org.mockito.ArgumentMatchers.eq(true));
      try{assertThat(call(learner,"POST",ap+"/submit",submit).statusCode()).isEqualTo(500);}finally{org.mockito.Mockito.reset(observedPapers);}
      var rolledBack=success(call(learner,"GET",ap,null));assertThat(rolledBack.get("status").asText()).isEqualTo("IN_PROGRESS");assertThat(rolledBack.get("lockVersion").asInt()).isEqualTo(a.get("lockVersion").asInt());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_paper_response WHERE attempt_id=? AND result_json IS NOT NULL",Integer.class,a.get("id").asText())).isZero();
      var one=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call(learner,"POST",ap+"/submit",submit);}catch(Exception e){throw new RuntimeException(e);}});
      var two=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call(learner,"POST",ap+"/submit",submit);}catch(Exception e){throw new RuntimeException(e);}});
      var done=success(one.join());success(two.join());      assertThat(call(learner,"POST","/reading/selection-translations/reference",translationInput).statusCode()).isEqualTo(200);
assertThat(done.get("status").asText()).isEqualTo("COMPLETED");assertThat(done.toString()).contains("SECRET");
      int total=0,correct=0;for(var c:done.get("components"))if(c.hasNonNull("objective")){total+=c.get("objective").get("total").asInt();correct+=c.get("objective").get("correctCount").asInt();}assertThat(total).isEqualTo(55);assertThat(correct).isEqualTo(55);
      assertThat(call(learner,"PUT",ap+"/draft",draft).statusCode()).isEqualTo(409);submit.put("currentSlot","TRANSLATION");assertThat(call(learner,"POST",ap+"/submit",submit).statusCode()).isEqualTo(409);
      assertThat(success(call(learner,"GET","/practice/paper-history",null)).get("total").asInt()).isEqualTo(1);assertThat(success(call(other,"GET","/practice/paper-history",null)).get("total").asInt()).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_attempt WHERE user_id=(SELECT id FROM identity_user WHERE username=?)",Integer.class,usernames.get(learner))).isZero();
      jdbc.update("UPDATE practice_set SET status='PUBLISHED' WHERE id=?",componentSet);
      var empty=success(call(other,"POST",startPath,Map.of("paperVersionId",paper.get("paperVersionId").asText(),"requestId",requestId())));String emptyPath="/practice/paper-attempts/"+empty.get("id").asText();var emptySubmit=new LinkedHashMap<String,Object>();emptySubmit.put("requestId",requestId());emptySubmit.put("expectedLockVersion",0);emptySubmit.put("currentSlot","WRITING");emptySubmit.put("responses",json.readValue(empty.get("responses").toString(),List.class));emptySubmit.put("confirmUnanswered",false);assertThat(call(other,"POST",emptyPath+"/submit",emptySubmit).statusCode()).isEqualTo(400);emptySubmit.put("confirmUnanswered",true);var emptyDone=success(call(other,"POST",emptyPath+"/submit",emptySubmit));int emptyTotal=0;for(var c:emptyDone.get("components"))if(c.hasNonNull("objective")){emptyTotal+=c.at("/objective/total").asInt();assertThat(c.at("/objective/correctCount").asInt()).isZero();}assertThat(emptyTotal).isEqualTo(55);
      if(Boolean.getBoolean("practice.paper.browser"))browser(actor(false),paper);
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="practice.paper.source",matches="true")
    void publishedSourcePaperCompletesInRealBrowser() throws Exception {
      var admin=actor(true);var learner=actor(false);
      var bundle=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("practice.paper.source.bundle"))));
      var slots=List.of("LISTENING","CLOZE","MATCH","DETAILED_1","DETAILED_2","WRITING","TRANSLATION");
      assertThat(bundle.size()).isEqualTo(7);var components=new ArrayList<Map<String,String>>();
      var sourceSets=new LinkedHashMap<String,String>();String asset=upload(admin);
      for(int i=0;i<bundle.size();i++){
        var item=bundle.get(i);String type=item.get("readingType").asText();boolean subjective=PracticeSubjectiveRules.isSubjective(type);
        var input=json.convertValue(item,new tools.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});
        input.remove("kind");input.put("expectedLockVersion",0);if(!subjective)input.put("requestId",requestId());
        if(type.equals("LISTENING"))((Map<String,Object>)input.get("listening")).put("audioAssetId",asset);
        var set=success(call(admin,"POST",subjective?"/admin/practice/subjective-sets":"/admin/practice/material-sets",input));String sid=set.get("id").asText(),sp="/admin/practice/sets/"+sid;
        for(String kind:subjective?List.of("CONTENT","SOURCE"):type.equals("LISTENING")?List.of("AUDIO","QUESTIONS","SOURCE"):List.of("QUESTIONS","SOURCE"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
        set=success(call(admin,"POST",sp+"/publish",lock(set)));String version=success(call(anonymous(),"GET","/practice/sets/"+sid,null)).get("setVersionId").asText();
        components.add(Map.of("slot",slots.get(i),"setVersionId",version));sourceSets.put(slots.get(i),sid);
      }
      var metadata=bundle.get(5).get("metadata");var input=new LinkedHashMap<String,Object>();
      input.put("title","Source paper acceptance fixture");input.put("examCode","CET4");input.put("year",metadata.get("year").asInt());input.put("month",metadata.get("month").asInt());input.put("sourceNote","Dedicated test database copy of the published source bundle.");input.put("components",components);
      var paper=success(call(admin,"POST","/admin/practice/papers",input));String pp="/admin/practice/papers/"+paper.get("id").asText();
      paper=success(call(admin,"POST",pp+"/review",lock(paper)));paper=success(call(admin,"POST",pp+"/publish",lock(paper)));
      sourceBrowser(learner,paper,sourceSets);
    }
    private void sourceBrowser(HttpClient learner,JsonNode paper,Map<String,String> sourceSets) throws Exception {
      var builder=new ProcessBuilder("node",System.getProperty("practice.paper.source.browser","../frontend/tests/real/practice-paper-source.cjs"));var env=builder.environment();
      env.put("PAPER_TEST_BASE","http://127.0.0.1:"+port);env.put("PAPER_TEST_TOKEN",tokens.get(learner));env.put("PAPER_TEST_ID",paper.get("id").asText());env.put("PAPER_SOURCE_SETS",json.writeValueAsString(sourceSets));
      env.put("PAPER_SOURCE_BUNDLE",java.nio.file.Path.of(System.getProperty("practice.paper.source.bundle")).toAbsolutePath().toString());
      var folder=java.nio.file.Path.of(System.getProperty("practice.paper.source.output")).toAbsolutePath();java.nio.file.Files.createDirectories(folder);env.put("PAPER_SOURCE_OUTPUT",folder.toString());
      var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("PAPER_TEST_COOKIES",json.writeValueAsString(cookies));
      var process=builder.redirectErrorStream(true).redirectOutput(folder.resolve("real-browser.log").toFile()).start();boolean ended=process.waitFor(240,java.util.concurrent.TimeUnit.SECONDS);if(!ended)process.destroyForcibly();assertThat(ended).as("Bounded source browser").isTrue();assertThat(process.exitValue()).as("Source browser; inspect private real-browser.log").isZero();
    }
    JsonNode fixture(HttpClient admin,String slot) throws Exception {
      String type=PracticePaperRules.type(slot);var input=new LinkedHashMap<String,Object>();input.put("readingType",type);input.put("metadata",Map.of("title","Synthetic "+slot,"titleZh","测试夹具非真题 "+slot,"sourceKind","PAST","topic","education","year",2024,"month",6,"paperNo",1,"sourceName","Synthetic source fixture","sourceUrl","https://example.org/test","usageNote","Synthetic integration only."));
      boolean subjective=PracticeSubjectiveRules.isSubjective(type);
      if(subjective)input.put("subjective",Map.of("prompt",type.equals("WRITING")?"Write a proposal for a study room.":"社区组织步行活动。","requirements","Complete the task.","minWords",3,"maxWords",180,"minutes",30,"reference","SECRET reference text","notes","SECRET comparison notes"));
      else{var qs=new ArrayList<Map<String,Object>>();for(int n=0;n<PracticePaperRules.count(slot);n++){String qt=type.equals("DETAILED")?"DETAIL":type;qs.add(Map.of("key","q"+(n+1),"type",qt,"stem",type.equals("CLOZE")?""+(26+n):"Synthetic question "+n,"options",type.equals("CLOZE")||type.equals("MATCH")?List.of():List.of("First option","Second option","Third option","Fourth option"),"correctOption",type.equals("CLOZE")?""+(char)('A'+n):"A","explanation","SECRET explanation","sortOrder",n));}input.put("questions",qs);input.put("requestId",requestId());input.put("body",type.equals("CLOZE")?java.util.stream.IntStream.rangeClosed(26,35).mapToObj(n->"Item {{"+n+"}} text.").collect(java.util.stream.Collectors.joining(" ")):type.equals("MATCH")?"[A] People learn together in community groups.":type.equals("LISTENING")?"SECRET transcript for listening only.":"Original source material for this synthetic fixture.");if(type.equals("CLOZE"))input.put("wordBank",java.util.stream.IntStream.range(0,15).mapToObj(n->"word"+(char)('a'+n)).toList());if(type.equals("LISTENING"))input.put("listening",Map.of("audioAssetId",upload(admin),"groups",List.of(Map.of("key","whole","title","整套测试","questionKeys",java.util.stream.IntStream.rangeClosed(1,25).mapToObj(n->"q"+n).toList()))));}
      var set=success(call(admin,"POST",subjective?"/admin/practice/subjective-sets":"/admin/practice/material-sets",input));String sp="/admin/practice/sets/"+set.get("id").asText();
      for(String kind:subjective?List.of("CONTENT","SOURCE"):type.equals("LISTENING")?List.of("AUDIO","QUESTIONS","SOURCE"):List.of("QUESTIONS","SOURCE"))set=success(call(admin,"POST",sp+"/review",Map.of("expectedLockVersion",set.get("lockVersion").asInt(),"kind",kind,"reviewed",true)));
      return success(call(admin,"POST",sp+"/publish",lock(set)));
    }
    private void browser(HttpClient learner,JsonNode paper) throws Exception {
      var builder=new ProcessBuilder("node","../frontend/tests/real/practice-paper.cjs");var env=builder.environment();env.put("PAPER_TEST_BASE","http://127.0.0.1:"+port);env.put("PAPER_TEST_TOKEN",tokens.get(learner));env.put("PAPER_TEST_ID",paper.get("id").asText());
      var cookies=((CookieManager)learner.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().map(c->Map.of("name",c.getName(),"value",c.getValue(),"domain","127.0.0.1","path",c.getPath()==null?"/":c.getPath(),"httpOnly",c.isHttpOnly(),"secure",c.getSecure(),"sameSite","Lax")).toList();env.put("PAPER_TEST_COOKIES",json.writeValueAsString(cookies));var out=java.nio.file.Path.of("../.tool-cache/practice-paper-implementation/real-browser.log").toFile();var process=builder.redirectErrorStream(true).redirectOutput(out).start();boolean ended=process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS);if(!ended)process.destroyForcibly();assertThat(ended).isTrue();assertThat(process.exitValue()).as("Real browser; private real-browser.log").isZero();
    }
    private String upload(HttpClient admin) throws Exception {
        String csrf=json.readTree(call(admin,"GET","/auth/csrf",null).body()).get("token").asText();String boundary="test"+UUID.randomUUID();byte[] data=new byte[417*400];for(int i=0;i<400;i++){data[i*417]=(byte)0xff;data[i*417+1]=(byte)0xfb;data[i*417+2]=(byte)0x90;}
        if(Boolean.getBoolean("practice.paper.source"))data=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("practice.paper.source.audio")));
        var bytes=new java.io.ByteArrayOutputStream();bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));bytes.write(data);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=admin.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/admin/practice/audio-assets")).header("Origin","http://127.0.0.1:4174").header("Authorization","Bearer "+tokens.get(admin)).header("X-CSRF-TOKEN",csrf).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());return success(response).get("id").asText();
    }
    HttpClient actor(boolean admin) throws Exception {
        String username = "paper_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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

    Map<String, Object> lock(JsonNode set) { return Map.of("expectedLockVersion", set.get("lockVersion").asInt()); }

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

    String requestId() { return UUID.randomUUID().toString(); }

    @AfterEach
    void cleanFixtureUsers() {
        cleanFixtures();
    }

    private void cleanFixtures() {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='practice_paper'",Integer.class)>0){
          String owned="SELECT p.id FROM practice_paper p JOIN identity_user u ON u.id=p.created_by WHERE u.username LIKE 'paper!_%' ESCAPE '!'";
          if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='practice_paper_mistake'",Integer.class)>0){
            jdbc.update("DELETE m FROM practice_paper_mistake m JOIN identity_user u ON u.id=m.user_id WHERE u.username LIKE 'paper!_%' ESCAPE '!'");
            jdbc.update("UPDATE practice_attempt t JOIN identity_user u ON u.id=t.user_id SET t.source_paper_attempt_id=NULL,t.source_paper_slot=NULL WHERE u.username LIKE 'paper!_%' ESCAPE '!'");
          }
          jdbc.update("DELETE r FROM practice_paper_request r JOIN practice_paper_attempt a ON a.id=r.attempt_id WHERE a.paper_id IN ("+owned+")");
          jdbc.update("DELETE r FROM practice_paper_response r JOIN practice_paper_attempt a ON a.id=r.attempt_id WHERE a.paper_id IN ("+owned+")");
          jdbc.update("DELETE FROM practice_paper_attempt WHERE paper_id IN ("+owned+")");
          jdbc.update("UPDATE practice_paper SET published_version_id=NULL,status='DRAFT' WHERE id IN (SELECT id FROM ("+owned+") owned)");
          jdbc.update("DELETE c FROM practice_paper_component c JOIN practice_paper_version v ON v.id=c.paper_version_id WHERE v.paper_id IN ("+owned+")");
          jdbc.update("DELETE FROM practice_paper_version WHERE paper_id IN ("+owned+")");
          jdbc.update("DELETE FROM practice_paper WHERE id IN (SELECT id FROM ("+owned+") owned)");
        }

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




