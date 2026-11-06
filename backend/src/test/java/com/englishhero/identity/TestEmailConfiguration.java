package com.englishhero.identity;

import com.englishhero.identity.email.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only delivery boundary. Never provides an HTTP endpoint for codes. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEmailConfiguration {
    public static class TestClock extends java.time.Clock {
        private final java.util.concurrent.atomic.AtomicLong millis = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(millis.get()); }
        public long millis() { return millis.get(); }
        public void advance(long seconds) { millis.addAndGet(seconds * 1000); }
    }
    public static class Mailbox implements EmailDelivery {
        private final ConcurrentHashMap<String, String> codes = new ConcurrentHashMap<>();
        public final java.util.Set<String> failures = ConcurrentHashMap.newKeySet();
        public final ConcurrentHashMap<String, Integer> deliveries = new ConcurrentHashMap<>();
        public boolean available() { return true; }
        public void deliver(String email, String code, EmailPurpose purpose) {
            deliveries.merge(email, 1, Integer::sum);
            if (failures.contains(email)) throw new IllegalStateException("Test transport failure");
            codes.put(email, code);
        }
        public String take(String email) throws InterruptedException {
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until) {
                String value = codes.remove(email);
                if (value != null) return value;
                Thread.sleep(10);
            }
            throw new AssertionError("Test delivery did not arrive");
        }
        public void clear(String email) { codes.remove(email); failures.remove(email); deliveries.remove(email); }
    }
    @Bean @Primary public Mailbox testMailbox() { return new Mailbox(); }
    @Bean @Primary public TestClock testClock() { return new TestClock(); }
}
