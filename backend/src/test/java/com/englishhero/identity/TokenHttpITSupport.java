package com.englishhero.identity;

import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "english-hero.email.enabled=true", "english-hero.email.hmac-secret=test-only-secret-for-email-integration-2026",
    "english-hero.email.from=test@example.com"})
@org.springframework.context.annotation.Import(TestEmailConfiguration.class)
abstract class TokenHttpITSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired TestEmailConfiguration.Mailbox mailbox;
    final List<String> fixtures = new ArrayList<>();
    String fixtureName() { String name = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); fixtures.add(name); return name; }
    @AfterEach void removeOnlyThisTestFixtures() {
        for (String name : fixtures) {
            jdbc.update("DELETE FROM identity_email_challenge WHERE email_hash=UNHEX(SHA2(?,256))", name + "@example.com");
            jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", name);
            jdbc.update("DELETE FROM identity_user WHERE username=?", name);
        }
    }
    @DynamicPropertySource static void testDatabase(DynamicPropertyRegistry registry) {
        com.englishhero.identity.token.TokenTestSupport.register(registry);
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var p = Objects.requireNonNull(yaml.getObject());
        var url = p.getProperty("spring.datasource.url", "");
        assertThat(url).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> p.getProperty("spring.datasource.username"));
        registry.add("spring.datasource.password", () -> p.getProperty("spring.datasource.password"));
    }
    final Map<HttpClient,String> accessTokens = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> call(HttpClient c, String method, String path, String body, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        b.header("Origin","http://127.0.0.1:4174");
        if(accessTokens.containsKey(c)) b.header("Authorization","Bearer "+accessTokens.get(c));
        if (token != null) b.header("X-CSRF-TOKEN", token);
        if (body != null) b.header("Content-Type", "application/json");
        var result=c.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if(result.statusCode()==200 && (path.equals("/auth/login") || path.equals("/auth/refresh")))accessTokens.put(c,JsonPath.read(result.body(),"$.accessToken"));
        return result;
    }
    String csrf(HttpClient c) throws Exception {
        var response = call(c, "GET", "/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.token");
    }
    String registration(HttpClient c, String name, String password) throws Exception {
        String email = name + "@example.com";
        var sent = call(c, "POST", "/auth/email-codes", "{\"purpose\":\"REGISTER\",\"email\":\"" + email + "\"}", csrf(c));
        assertThat(sent.statusCode()).isEqualTo(202);
        String id = JsonPath.read(sent.body(), "$.challengeId");
        return "{\"username\":\"" + name + "\",\"password\":\"" + password + "\",\"email\":\"" + email
            + "\",\"challengeId\":\"" + id + "\",\"code\":\"" + mailbox.take(email) + "\"}";
    }

}
