package com.englishhero.identity;

import com.jayway.jsonpath.JsonPath;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "english-hero.email.enabled=true", "english-hero.email.hmac-secret=verified-email-test-only-secret-2026-not-production",
    "english-hero.email.from=test@example.com"})
@Import(TestEmailConfiguration.class)
class VerifiedEmailFlowIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityService identities;
    @Autowired com.englishhero.identity.email.EmailVerificationService proofs;
    @Autowired TestEmailConfiguration.Mailbox mailbox;
    @Autowired TestEmailConfiguration.TestClock clock;
    final List<String> names = new ArrayList<>();
    final List<String> emails = new ArrayList<>();
    record Proof(String id, String code) {}
    @BeforeEach void isolateHttpTestRateWindowWithoutChangingProductionLimits() {
        // Browser tests use the same dedicated schema and loopback IP. Advance only this test
        // context's injected clock so its quotas do not share a rolling hour with another server.
        clock.advance(3601);
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        com.englishhero.identity.token.TokenTestSupport.register(r);
        var yaml = new YamlPropertiesFactoryBean(); yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var p = Objects.requireNonNull(yaml.getObject());
        String url = p.getProperty("spring.datasource.url", "");
        assertThat(url).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> p.getProperty("spring.datasource.username"));
        r.add("spring.datasource.password", () -> p.getProperty("spring.datasource.password"));
    }
    String name() { String n = "mail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); names.add(n); return n; }
    String email() { String e = "mail_" + UUID.randomUUID().toString().replace("-", "") + "@example.com"; emails.add(e); return e; }
    @AfterEach void cleanup() {
        for (String e : emails) { jdbc.update("DELETE FROM identity_email_challenge WHERE email_hash=UNHEX(SHA2(?,256))", e); mailbox.clear(e); }
        for (String n : names) {
            jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", n);
            jdbc.update("DELETE FROM identity_user WHERE username=?", n);
        }
    }
    final Map<HttpClient,String> accessTokens = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> call(HttpClient c, String method, String path, String body, String token) throws Exception {
        var r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        r.header("Origin","http://127.0.0.1:4174");
        if(accessTokens.containsKey(c)) r.header("Authorization","Bearer "+accessTokens.get(c));
        if (token != null) r.header("X-CSRF-TOKEN", token);
        if (body != null) r.header("Content-Type", "application/json");
        var result=c.send(r.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if(result.statusCode()==200 && (path.equals("/auth/login") || path.equals("/auth/refresh")))accessTokens.put(c,JsonPath.read(result.body(),"$.accessToken"));
        return result;
    }
    String csrf(HttpClient c) throws Exception { return JsonPath.read(call(c, "GET", "/auth/csrf", null, null).body(), "$.token"); }
    HttpResponse<String> post(HttpClient c, String path, String body) throws Exception { return call(c, "POST", path, body, csrf(c)); }
    String json(String... pairs) {
        var b = new StringJoiner(",", "{", "}");
        for (int i=0; i<pairs.length; i+=2) b.add("\"" + pairs[i] + "\":\"" + pairs[i+1] + "\"");
        return b.toString();
    }
    Proof issue(HttpClient c, String purpose, String email, String name) throws Exception {
        clock.advance(61);
        var response = post(c, "/auth/email-codes", json("purpose", purpose, "email", email, "username", name));
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).doesNotContain("@", "code\"");
        return new Proof(JsonPath.read(response.body(), "$.challengeId"), mailbox.take(email));
    }
    String registration(String n, String e, Proof p) { return json("username", n, "password", "valid-password", "email", e, "challengeId", p.id(), "code", p.code()); }
    void login(HttpClient c, String n, String password) throws Exception {
        assertThat(post(c, "/auth/login", json("username", n, "password", password)).statusCode()).isEqualTo(200);
    }

    @Test void publicRegistrationRequiresProofAndNeverAcceptsRolesOrEmailAsLogin() throws Exception {
        var c = client(); String n = name(), e = email();
        assertThat(post(c, "/auth/register", json("username", n, "password", "valid-password")).statusCode()).isEqualTo(400);
        var p = issue(c, "REGISTER", e, n);
        assertThat(call(c, "POST", "/auth/register", registration(n,e,p), null).statusCode()).isEqualTo(403);
        assertThat(post(c, "/auth/register", registration(n,e,p).replace("}", ",\"roles\":[\"ADMIN\"]}")).statusCode()).isEqualTo(400);
        var result = post(c, "/auth/register", registration(n,e,p));
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat((Boolean) JsonPath.read(result.body(), "$.emailVerified")).isTrue();
        assertThat((List<String>) JsonPath.read(result.body(), "$.roles")).containsExactly("USER");
        assertThat(post(c, "/auth/register", registration(name(),e,p)).statusCode()).isIn(400,409);
        assertThat(post(c, "/auth/login", json("username", e, "password", "valid-password")).statusCode()).isEqualTo(400);
        login(c,n,"valid-password");
        assertThat(call(c, "GET", "/admin/identity", null, null).statusCode()).isEqualTo(403);
    }

    @Test void bindingOldAdminRequiresPasswordAndProofAndPreservesRoles() throws Exception {
        var c = client(); String n = name(), e = email();
        identities.bootstrap(n, "valid-password", "Email Test Admin");
        assertThat(post(c, "/account/email-codes", json("email",e,"currentPassword","valid-password")).statusCode()).isEqualTo(401);
        login(c,n,"valid-password");
        assertThat(post(c, "/account/email-codes", json("email",e,"currentPassword","wrong-password")).statusCode()).isEqualTo(401);
        var sent=post(c, "/account/email-codes", json("email",e,"currentPassword","valid-password"));
        assertThat(sent.statusCode()).isEqualTo(202);
        // First binding proof is delivered to the supplied address, never returned by an account endpoint.
        String code = mailbox.take(e);
        String id = JsonPath.read(sent.body(),"$.challengeId");
        String body = json("email",e,"currentPassword","valid-password","challengeId",id,"code",code);
        assertThat(post(c,"/auth/register",registration(name(),e,new Proof(id,code))).statusCode()).isEqualTo(400);
        assertThat(post(c,"/account/email",body.replace("}",",\"userId\":\"1\"}")).statusCode()).isEqualTo(400);
        var bound = post(c,"/account/email",body);
        assertThat(bound.statusCode()).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(bound.body(),"$.roles")).containsExactly("ADMIN","USER");
        assertThat(post(c,"/account/email",body).statusCode()).isEqualTo(409);
        assertThat(call(c,"GET","/admin/identity",null,null).statusCode()).isEqualTo(200);
    }

    @Test void resetRevokesNormalAndRememberedSessionsAndProofCannotBeReplayed() throws Exception {
        var c = client(); var other = client(); var recovery = client(); String n=name(), e=email();
        Proof registration = issue(c,"REGISTER",e,n);
        assertThat(post(c,"/auth/register",registration(n,e,registration)).statusCode()).isEqualTo(201);
        String wrongEmail=email();
        var wrongAddress=post(c,"/auth/email-codes",json("purpose","RESET","username",n,"email",wrongEmail));
        assertThat(wrongAddress.statusCode()).isEqualTo(202);
        assertThat(mailbox.deliveries).doesNotContainKey(wrongEmail);
        login(c,n,"valid-password");
        var remembered=post(other,"/auth/login",json("username",n,"password","valid-password").replace("}",",\"rememberMe\":true}"));
        assertThat(remembered.statusCode()).isEqualTo(200);
        assertThat(remembered.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.contains("Max-Age=604"))).isTrue();
        Proof p=issue(recovery,"RESET",e,n);
        String body=json("username",n,"email",e,"challengeId",p.id(),"code",p.code(),"newPassword","replacement-password");
        assertThat(post(recovery,"/auth/password-reset",body.replace(n,name())).statusCode()).isEqualTo(400);
        assertThat(post(recovery,"/auth/password-reset",body).statusCode()).isEqualTo(204);
        assertThat(post(recovery,"/auth/password-reset",body).statusCode()).isEqualTo(400);
        assertThat(call(c,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(call(other,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(call(recovery,"GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(post(recovery,"/auth/login",json("username",n,"password","valid-password")).statusCode()).isEqualTo(401);
        login(recovery,n,"replacement-password");
    }

    @Test void failedAttemptsSurviveRollbackExpiryAndResendInvalidatePreviousCode() throws Exception {
        var c=client(); String n=name(),e=email(); Proof p=issue(c,"REGISTER",e,n);
        String wrong=p.code().equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++) assertThat(post(c,"/auth/register",registration(n,e,new Proof(p.id(),wrong))).statusCode()).isEqualTo(400);
        assertThat(post(c,"/auth/register",registration(n,e,p)).statusCode()).isEqualTo(400);
        Proof expired=issue(c,"REGISTER",e,n); clock.advance(300);
        assertThat(post(c,"/auth/register",registration(n,e,expired)).statusCode()).isEqualTo(400);
        Proof old=issue(c,"REGISTER",e,n); Proof fresh=issue(c,"REGISTER",e,n);
        assertThat(post(c,"/auth/register",registration(n,e,old)).statusCode()).isEqualTo(400);
        assertThat(post(c,"/auth/register",registration(n,e,fresh)).statusCode()).isEqualTo(201);
    }

    @Test void missingResetAccountsReceiveUniformShapeAndSendCooldownReturnsRetryAfter() throws Exception {
        var c=client();String n=name(),e=email();
        var first=post(c,"/auth/email-codes",json("purpose","RESET","username",n,"email",e));
        assertThat(first.statusCode()).isEqualTo(202);
        assertThat((Integer)JsonPath.read(first.body(),"$.expiresIn")).isEqualTo(300);
        assertThat(first.body()).doesNotContain(e,"code\"");
        var again=post(c,"/auth/email-codes",json("purpose","REGISTER","email",e));
        assertThat(again.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(again.headers().firstValue("Retry-After").orElseThrow())).isPositive();
        assertThat(post(c,"/auth/email-codes",json("purpose","BIND","email",e)).statusCode()).isEqualTo(400);
    }

    @Test void mismatchedRequestDoesNotDeliverOrDisableAnExistingResetProof() throws Exception {
        var c=client(); String n=name(),e=email();
        Proof registration=issue(c,"REGISTER",e,n);
        assertThat(post(c,"/auth/register",registration(n,e,registration)).statusCode()).isEqualTo(201);
        Proof valid=issue(c,"RESET",e,n);
        int deliveries=mailbox.deliveries.get(e);
        clock.advance(61);
        var mismatch=post(c,"/auth/email-codes",json("purpose","RESET","username",name(),"email",e));
        assertThat(mismatch.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(mismatch.body(),"$.requestState")).isEqualTo("ACCEPTED");
        assertThat((Integer)JsonPath.read(mismatch.body(),"$.resendAfter")).isEqualTo(60);
        assertThat(mismatch.body()).doesNotContain("deliveryState","matched","sent");
        assertThat(mailbox.deliveries.get(e)).isEqualTo(deliveries);
        assertThat(post(c,"/auth/password-reset",json("username",n,"email",e,"challengeId",valid.id(),"code",valid.code(),"newPassword","replacement-password")).statusCode()).isEqualTo(204);
    }

    @Test void bindingRejectsTakenEmailBeforeQueueAndStatusIsRestrictedToItsOwner() throws Exception {
        var owner=client(); String n=name(),e=email();
        identities.bootstrap(n,"valid-password","Owner"); login(owner,n,"valid-password");
        var issued=post(owner,"/account/email-codes",json("email",e,"currentPassword","valid-password"));
        assertThat(issued.statusCode()).isEqualTo(202); mailbox.take(e);
        assertThat(JsonPath.<String>read(issued.body(),"$.requestState")).isEqualTo("ACCEPTED");
        String id=JsonPath.read(issued.body(),"$.challengeId"), path="/account/email-codes/"+id;
        assertThat(call(owner,"GET",path,null,null).statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(call(owner,"GET",path,null,null).body(),"$.state")).isEqualTo("SMTP_ACCEPTED");
        assertThat(call(client(),"GET",path,null,null).statusCode()).isEqualTo(401);
        var other=client(); String otherName=name(); identities.bootstrap(otherName,"valid-password","Other"); login(other,otherName,"valid-password");
        assertThat(call(other,"GET",path,null,null).statusCode()).isEqualTo(404);
        assertThat(call(owner,"GET","/account/email-codes/not-a-uuid",null,null).statusCode()).isEqualTo(404);
        assertThat(call(owner,"GET","/auth/email-codes/"+id,null,null).statusCode()).isNotEqualTo(200);
        // Occupied address is rejected only after authentication and current-password checks.
        String taken=email(), registered=name(); Proof proof=issue(client(),"REGISTER",taken,registered);
        assertThat(post(client(),"/auth/register",registration(registered,taken,proof)).statusCode()).isEqualTo(201);
        int sent=mailbox.deliveries.get(taken);
        var rejected=post(other,"/account/email-codes",json("email",taken,"currentPassword","valid-password"));
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(rejected.body(),"$.code")).isEqualTo("EMAIL_TAKEN");
        assertThat(mailbox.deliveries.get(taken)).isEqualTo(sent);
    }

    @Test void bindingDeliveryFailureIsVisibleOnlyToOwnerAndDisablesProof() throws Exception {
        var c=client(); String n=name(),e=email(); identities.bootstrap(n,"valid-password","Owner"); login(c,n,"valid-password");
        mailbox.failures.add(e);
        var issued=post(c,"/account/email-codes",json("email",e,"currentPassword","valid-password"));
        assertThat(issued.statusCode()).isEqualTo(202);
        String id=JsonPath.read(issued.body(),"$.challengeId"), state="";
        for(int i=0;i<50;i++) {
            var status=call(c,"GET","/account/email-codes/"+id,null,null);
            assertThat(status.statusCode()).isEqualTo(200);
            state=JsonPath.read(status.body(),"$.state"); if(state.equals("FAILED")) break;
            Thread.sleep(20);
        }
        assertThat(state).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT disabled_at IS NOT NULL FROM identity_email_challenge WHERE challenge_id=?",Boolean.class,id)).isTrue();
    }

    @Test void concurrentPasswordResetsConsumeTheProofExactlyOnce() throws Exception {
        var c=client(); String n=name(),e=email(); Proof registration=issue(c,"REGISTER",e,n);
        assertThat(post(c,"/auth/register",registration(n,e,registration)).statusCode()).isEqualTo(201);
        Proof proof=issue(c,"RESET",e,n);
        String body=json("username",n,"email",e,"challengeId",proof.id(),"code",proof.code(),"newPassword","replacement-password");
        var first=client(); var second=client(); String firstCsrf=csrf(first),secondCsrf=csrf(second);
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(() -> { barrier.await(); return call(first,"POST","/auth/password-reset",body,firstCsrf).statusCode(); });
            var b=executor.submit(() -> { barrier.await(); return call(second,"POST","/auth/password-reset",body,secondCsrf).statusCode(); });
            assertThat(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);
        }
    }

    @Test void fourConcurrentVerifiedRegistrationsCompleteWithinTheFourConnectionPool() throws Exception {
        record RegistrationAttempt(HttpClient client, String body, String token) {}
        var requests = new ArrayList<RegistrationAttempt>();
        for (int i=0;i<4;i++) {
            var c=client(); String n=name(),e=email(); Proof p=issue(c,"REGISTER",e,n);
            requests.add(new RegistrationAttempt(c,registration(n,e,p),csrf(c)));
        }
        var barrier=new java.util.concurrent.CyclicBarrier(4);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(var r:requests) results.add(executor.submit(() -> {
                barrier.await(); return call(r.client(),"POST","/auth/register",r.body(),r.token()).statusCode();
            }));
            for(var result:results) assertThat(result.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        }
    }

    @Test void failedAccountTransactionDoesNotConsumeTheEmailProof() throws Exception {
        var c=client();String n=name(),e=email();Proof p=issue(c,"REGISTER",e,n);
        assertThatThrownBy(() -> proofs.executeVerified(com.englishhero.identity.email.EmailPurpose.REGISTER,
            e,null,null,p.id(),p.code(),() -> { throw new IdentityException(409,"ACCOUNT_CHANGED","测试事务失败"); }))
            .isInstanceOf(IdentityException.class);
        assertThat(post(c,"/auth/register",registration(n,e,p)).statusCode()).isEqualTo(201);
    }
}
