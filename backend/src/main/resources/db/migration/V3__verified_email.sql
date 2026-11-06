ALTER TABLE identity_user
    ADD COLUMN email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN email_verified_at DATETIME(6) NULL,
    ADD CONSTRAINT identity_email_unique UNIQUE (email);

CREATE TABLE identity_email_challenge (
    challenge_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    purpose VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email_hash BINARY(32) NOT NULL,
    user_id BIGINT NULL,
    security_version INT NULL,
    code_hmac BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_after DATETIME(6) NOT NULL,
    attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    consumed_at DATETIME(6) NULL,
    disabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT identity_email_challenge_purpose CHECK (purpose IN ('REGISTER', 'RESET', 'BIND')),
    CONSTRAINT identity_email_challenge_user FOREIGN KEY (user_id)
        REFERENCES identity_user(id) ON DELETE CASCADE,
    INDEX identity_email_challenge_lookup (purpose, email_hash, created_at),
    INDEX identity_email_challenge_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE identity_auth_rate (
    scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_hash BINARY(32) NOT NULL,
    window_seconds INT UNSIGNED NOT NULL,
    event_times VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (scope, subject_hash, window_seconds),
    INDEX identity_auth_rate_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
