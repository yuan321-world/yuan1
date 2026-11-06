package com.englishhero.identity;

import com.englishhero.EnglishHeroApplication;
import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class IdentityRestartIT {
    String access;
    HttpResponse<String> call(HttpClient client, int port, String method, String path, String body, String csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        request.header("Origin","http://127.0.0.1:4174");
        if(access!=null)request.header("Authorization","Bearer "+access);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        var result=client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if(result.statusCode()==200 && (path.equals("/auth/login")||path.equals("/auth/refresh")))access=JsonPath.read(result.body(),"$.accessToken");
        return result;
    }
    @Test void jwtAndRefreshSurviveApplicationRestartAndIdleExpiryReallyDeniesAccess() throws Exception {
        var yaml = new YamlPropertiesFactoryBean(); yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var p = Objects.requireNonNull(yaml.getObject()); var url = p.getProperty("spring.datasource.url", "");
        assertThat(url).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        var properties = new HashMap<String, Object>();
        properties.put("spring.config.import", "");
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", p.getProperty("spring.datasource.username"));
        properties.put("spring.datasource.password", p.getProperty("spring.datasource.password"));
        com.englishhero.identity.token.TokenTestSupport.properties().forEach((k,v)->properties.put(k.toString(),v));
        properties.put("server.port", "0");
        properties.put("english-hero.bootstrap.enabled", "false");
        // Builder defaults do not override application.yml; explicit in-memory PropertySource wins without secret CLI args.
        var initializer = (org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext>) context -> context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("guarded-test", properties));
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String name = "restart_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String password = UUID.randomUUID() + "aA!";
        var body = "{\"username\":\"" + name + "\",\"password\":\"" + password + "\",\"rememberMe\":true}";
        try {
            try (var context = new SpringApplicationBuilder(EnglishHeroApplication.class).initializers(initializer).run()) {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                context.getBean(IdentityService.class).register(name, password, "Restart Test");
                String token = JsonPath.read(call(client, port, "GET", "/auth/csrf", null, null).body(), "$.token");
                assertThat(call(client, port, "POST", "/auth/login", body, token).statusCode()).isEqualTo(200);
                assertThat(call(client, port, "GET", "/auth/me", null, null).statusCode()).isEqualTo(200);
            }
            try (var context = new SpringApplicationBuilder(EnglishHeroApplication.class).initializers(initializer).run()) {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                assertThat(call(client, port, "GET", "/auth/me", null, null).statusCode()).isEqualTo(200);
                String csrf=JsonPath.read(call(client,port,"GET","/auth/csrf",null,null).body(),"$.token");
                assertThat(call(client,port,"POST","/auth/refresh",null,csrf).statusCode()).isEqualTo(200);
                var verified=context.getBean(com.englishhero.identity.token.JwtService.class).verifySignatureAndClaims(access);
                var repo=context.getBean(com.englishhero.identity.token.RedisFamilyRepository.class);
                context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class).opsForHash().put(repo.familyKey(verified.sid()),"lastSeen","0");
                assertThat(call(client, port, "GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
            }
        } finally {
            try (var connection = java.sql.DriverManager.getConnection(url, p.getProperty("spring.datasource.username"), p.getProperty("spring.datasource.password"))) {
                try (var delete = connection.prepareStatement("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?")) { delete.setString(1, name); delete.executeUpdate(); }
                try (var delete = connection.prepareStatement("DELETE FROM identity_user WHERE username=?")) { delete.setString(1, name); delete.executeUpdate(); }
            }
        }
    }
}
