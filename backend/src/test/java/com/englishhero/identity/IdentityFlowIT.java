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

class IdentityFlowIT extends TokenHttpITSupport {
    @Test void registrationLoginRolesLogoutAndCsrfRotation() throws Exception {
        var c = client();
        String name = fixtureName();
        String password = UUID.randomUUID() + "aA!";
        String loginBody = "{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}";
        String registration = registration(c, name, password);
        assertThat(call(c, "POST", "/auth/register", registration, null).statusCode()).isEqualTo(403);
        String before = csrf(c);
        String oldCookie = ((CookieManager) c.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("EH_CSRF")).findFirst().orElseThrow().getValue();
        assertThat(call(c, "POST", "/auth/register", registration, before).statusCode()).isEqualTo(201);
        assertThat(call(c, "POST", "/auth/register", registration, before).statusCode()).isEqualTo(409);
        assertThat(call(c, "POST", "/auth/register", registration.replace("}", ",\"roles\":[\"ADMIN\"]}"), before).statusCode()).isEqualTo(400);
        assertThat(call(c, "POST", "/auth/login", "{\"username\":\"" + name + "\",\"password\":\"wrongpass\"}", before).statusCode()).isEqualTo(401);
        var login = call(c, "POST", "/auth/login", loginBody, before);
        assertThat(login.statusCode()).isEqualTo(200);
        csrf(c);
        String newCookie = ((CookieManager) c.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("EH_CSRF")).findFirst().orElseThrow().getValue();
        assertThat(oldCookie.equals(newCookie)).isFalse();
        var oldRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me")).header("Cookie", "EH_SESSION=" + oldCookie).GET().build();
        assertThat(HttpClient.newHttpClient().send(oldRequest, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        assertThat(login.body().contains("hash") || login.body().contains("securityVersion") || login.body().contains(password)).isFalse();
        assertThat(call(c, "GET", "/auth/me", null, null).statusCode()).isEqualTo(200);
        String hash = jdbc.queryForObject("SELECT password_hash FROM identity_user WHERE username=?", String.class, name);
        var attributes = jdbc.queryForList("SELECT a.ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES a JOIN SPRING_SESSION s ON s.PRIMARY_ID=a.SESSION_PRIMARY_ID WHERE s.PRINCIPAL_NAME=?", byte[].class, name);
        assertThat(attributes).isEmpty();
        assertThat(attributes.stream().anyMatch(bytes -> new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(hash))).isFalse();
        assertThat(call(c, "GET", "/admin/identity", null, null).statusCode()).isEqualTo(403);
        assertThat(call(c, "PATCH", "/account/profile", "{\"displayName\":\"新名字\"}", before).statusCode()).isEqualTo(403);
        String after = csrf(c);
        var updatedProfile=call(c, "PATCH", "/account/profile", "{\"displayName\":\"新名字\"}", after);
        assertThat(updatedProfile.statusCode()).isEqualTo(200);
        assertThat(updatedProfile.headers().allValues("Set-Cookie").stream().anyMatch(v->v.startsWith("EH_CSRF="))).withFailMessage("profile unexpectedly replaced CSRF cookie").isFalse();
        var logout = call(c, "POST", "/auth/logout", null, after);
        assertThat(logout.statusCode()).withFailMessage("logout status %s: %s",logout.statusCode(),logout.body()).isEqualTo(204);
        assertThat(logout.headers().allValues("Set-Cookie").stream().anyMatch(value -> value.startsWith("EH_REFRESH=") && value.contains("Max-Age=0"))).isTrue();
        assertThat(call(c, "GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(call(c, "POST", "/auth/login", loginBody, after).statusCode()).isEqualTo(403);
    }
    @Test void passwordChangeRevokesTwoSessionsAndRememberCookieIsBounded() throws Exception {
        var c = client(); var other = client();
        String name = fixtureName();
        String password = UUID.randomUUID() + "aA!";
        String body = "{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}";
        identities.register(name, password, null);
        var normal = call(c, "POST", "/auth/login", body, csrf(c));
        assertThat(normal.statusCode()).isEqualTo(200);
        assertThat(normal.headers().allValues("Set-Cookie").toString()).doesNotContain("Max-Age=604800");
        var remembered = call(other, "POST", "/auth/login", body.replace("}", ",\"rememberMe\":true}"), csrf(other));
        assertThat(remembered.statusCode()).isEqualTo(200);
        assertThat(remembered.headers().allValues("Set-Cookie").toString()).contains("Max-Age=604", "HttpOnly", "SameSite=Lax");
        String replacement = UUID.randomUUID() + "zZ!";
        assertThat(call(c, "POST", "/account/password", "{\"currentPassword\":\"wrongpass\",\"newPassword\":\"" + replacement + "\"}", csrf(c)).statusCode()).isEqualTo(401);
        assertThat(call(c, "POST", "/account/password", "{\"currentPassword\":\"" + password + "\",\"newPassword\":\"" + replacement + "\"}", csrf(c)).statusCode()).isEqualTo(204);
        assertThat(call(c, "GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(call(other, "GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
    }
    @Test void controlledAdminAndDisabledIdentityAreCheckedByServer() throws Exception {
        var c = client(); String name = fixtureName(), password = UUID.randomUUID() + "aA!";
        identities.bootstrap(name, password, "Test Admin");
        String body = "{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}";
        assertThat(call(c, "POST", "/auth/login", body, csrf(c)).statusCode()).isEqualTo(200);
        assertThat(call(c, "GET", "/admin/identity", null, null).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE identity_user SET enabled=0 WHERE username=?", name);
        assertThat(call(c, "GET", "/admin/identity", null, null).statusCode()).isEqualTo(401);
        assertThat(call(c, "POST", "/auth/login", body, csrf(c)).statusCode()).isEqualTo(401);
    }
    @Test void concurrentRegistrationCreatesOnlyOneUserAndProfileRejectsForeignId() throws Exception {
        var first = client(); var second = client(); String name = fixtureName(), password = UUID.randomUUID() + "aA!";
        String body = "{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}";
        String registration = registration(first, name, password);
        String t1 = csrf(first), t2 = csrf(second);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> call(first, "POST", "/auth/register", registration, t1).statusCode());
            var b = executor.submit(() -> call(second, "POST", "/auth/register", registration, t2).statusCode());
            var results = List.of(a.get(), b.get());
            assertThat(results.stream().filter(status -> status == 201).count()).isEqualTo(1);
            assertThat(results).containsAnyOf(400, 409);
        }
        assertThat(call(first, "POST", "/auth/login", body, csrf(first)).statusCode()).isEqualTo(200);
        assertThat(call(first, "PATCH", "/account/profile", "{\"displayName\":\"Intruder\",\"userId\":\"1\"}", csrf(first)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_user WHERE username=?", Integer.class, name)).isEqualTo(1);
    }
    @Test void concurrentPasswordChangesHaveOneWinnerInRealDatabase() throws Exception {
        String name = fixtureName(), password = UUID.randomUUID() + "aA!";
        var dto = identities.register(name, password, null);
        var principal = new SessionPrincipal(Long.parseLong(dto.id()), name, 0, List.of("USER"));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Integer> change = () -> {
                barrier.await();
                try { identities.password(principal, password, UUID.randomUUID() + "zZ!"); return 204; }
                catch (IdentityException e) { return e.status; }
            };
            var a = executor.submit(change); var b = executor.submit(change);
            var results = List.of(a.get(), b.get());
            assertThat(results.stream().filter(status -> status == 204).count()).isEqualTo(1);
            assertThat(results).containsAnyOf(401, 409);
        }
        assertThat(jdbc.queryForObject("SELECT security_version FROM identity_user WHERE username=?", Integer.class, name)).isEqualTo(1);
    }
    @Test void publicRegistrationRejectsAdminPrefixIncludingMixedCaseAndWhitespace() throws Exception {
        var c = client();
        String name = "admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        fixtures.add(name);
        String body = "{\"username\":\"  " + name.replace("admin", "AdMiN") + "  \",\"password\":\"" + UUID.randomUUID() + "aA!\"}";
        assertThat(call(c, "POST", "/auth/register", body, csrf(c)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_user WHERE username=?", Integer.class, name)).isZero();
    }
}
