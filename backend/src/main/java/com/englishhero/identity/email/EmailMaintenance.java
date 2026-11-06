package com.englishhero.identity.email;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailMaintenance {
    private final JdbcTemplate jdbc;

    public EmailMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void removeExpiredEmailState() {
        jdbc.update("DELETE FROM identity_email_challenge WHERE expires_at < UTC_TIMESTAMP(6)");
        jdbc.update("DELETE FROM identity_auth_rate WHERE expires_at < UTC_TIMESTAMP(6)");
    }
}
