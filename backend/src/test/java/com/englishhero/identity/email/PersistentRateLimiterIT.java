package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PersistentRateLimiterIT {
    @Autowired PersistentRateLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    private final List<RateKey> cleanupKeys = new ArrayList<>();

    @DynamicPropertySource
    static void dedicatedDatabase(DynamicPropertyRegistry registry) {
        com.englishhero.identity.token.TokenTestSupport.register(registry);
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        String url = properties.getProperty("spring.datasource.url", "");
        assertThat(url).matches("^jdbc:mysql://127\\.0\\.0\\.1:3306/english_hero_test(?:\\?.*)?$");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> properties.getProperty("spring.datasource.username"));
        registry.add("spring.datasource.password", () -> properties.getProperty("spring.datasource.password"));
    }

    @AfterEach
    void removeOnlyThisTestRateKeys() {
        for (RateKey key : cleanupKeys) {
            jdbc.update("DELETE FROM identity_auth_rate WHERE scope=? AND subject_hash=? AND window_seconds=?",
                key.scope(), key.hash(), key.windowSeconds());
        }
    }

    @Test
    void concurrentEmailRequestsSerializeOnThePersistentCooldownRow() throws Exception {
        String email = "limit_" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        String ip = uniqueIp();
        rememberEmailKeys(email, ip);
        int requests = 12;
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(requests)) {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < requests; i++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    try {
                        limiter.emailIssue(email, ip);
                        return true;
                    } catch (RateLimitException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            List<Boolean> accepted = new ArrayList<>();
            for (var future : futures) accepted.add(future.get(10, TimeUnit.SECONDS));
            assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
            assertThat(accepted.stream().filter(value -> !value).count()).isEqualTo(requests - 1);
        }

        byte[] emailKey = EmailDigests.sha256("EMAIL_SEND\u0000" + email);
        String events = jdbc.queryForObject("SELECT event_times FROM identity_auth_rate "
            + "WHERE scope='EMAIL_SEND' AND subject_hash=? AND window_seconds=60", String.class, emailKey);
        assertThat(events.split(",")).hasSize(1);
    }

    @Test
    void usernameAndIpLoginBudgetPersistsAcrossRequests() {
        String username = "limit_" + UUID.randomUUID().toString().replace("-", "");
        String ip = uniqueIp();
        cleanupKeys.add(new RateKey("LOGIN_USER_IP", hash("LOGIN_USER_IP", username + "\u0000" + ip), 900));
        cleanupKeys.add(new RateKey("LOGIN_IP", hash("LOGIN_IP", ip), 900));

        for (int i = 0; i < 25; i++) limiter.loginAttempt(username, ip);

        assertThatThrownBy(() -> limiter.loginAttempt(username, ip))
            .isInstanceOfSatisfying(RateLimitException.class, error -> {
                assertThat(error.retryAfter()).isBetween(1L, 900L);
                assertThat(error.status).isEqualTo(429);
            });
    }

    private void rememberEmailKeys(String email, String ip) {
        cleanupKeys.add(new RateKey("EMAIL_SEND", hash("EMAIL_SEND", email), 60));
        cleanupKeys.add(new RateKey("EMAIL_HOUR", hash("EMAIL_HOUR", email), 3_600));
        cleanupKeys.add(new RateKey("EMAIL_DAY", hash("EMAIL_DAY", email), 86_400));
        cleanupKeys.add(new RateKey("EMAIL_IP_HOUR", hash("EMAIL_IP_HOUR", ip), 3_600));
    }

    private static byte[] hash(String scope, String subject) {
        return EmailDigests.sha256(scope + "\u0000" + subject);
    }

    private static String uniqueIp() {
        String bits = UUID.randomUUID().toString().replace("-", "");
        return "2001:db8:" + bits.substring(0, 4) + ":" + bits.substring(4, 8) + "::" + bits.substring(8, 12);
    }

    private record RateKey(String scope, byte[] hash, int windowSeconds) {}
}
