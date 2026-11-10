CREATE TABLE reading_full_translation_chunk (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 version_id BIGINT NOT NULL,
 content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 paragraph_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 chunk_index INT NOT NULL,
 source_text TEXT NOT NULL,
 separator_before VARCHAR(1000) NOT NULL DEFAULT '',
 status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
 translated_text TEXT NULL,
 error_code VARCHAR(64) NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 rate_limit_attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(6) NOT NULL,
 retry_after DATETIME(6) NULL,
 lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 CONSTRAINT full_translation_version FOREIGN KEY(version_id) REFERENCES reading_article_version(id) ON DELETE CASCADE,
 CONSTRAINT full_translation_chunk_index CHECK(chunk_index>=0),
 CONSTRAINT full_translation_attempt_count CHECK(attempt_count>=0 AND rate_limit_attempts>=0),
 CONSTRAINT full_translation_chunk_status CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
 UNIQUE KEY full_translation_identity(version_id,content_hash,paragraph_key,chunk_index),
 INDEX full_translation_queue(status,available_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reading_full_translation_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 chunk_id BIGINT NOT NULL,
 attempt_no INT NOT NULL,
 quota_month DATE NOT NULL,
 source_characters INT NOT NULL,
 status VARCHAR(16) NOT NULL,
 started_at DATETIME(6) NOT NULL,
 settled_at DATETIME(6) NULL,
 CONSTRAINT full_translation_attempt_chunk FOREIGN KEY(chunk_id) REFERENCES reading_full_translation_chunk(id) ON DELETE CASCADE,
 CONSTRAINT full_translation_attempt_no CHECK(attempt_no>0),
 CONSTRAINT full_translation_attempt_characters CHECK(source_characters BETWEEN 1 AND 1000),
 CONSTRAINT full_translation_attempt_status CHECK(status IN ('RESERVED','RETAINED','RELEASED')),
 UNIQUE KEY full_translation_attempt_identity(chunk_id,attempt_no),
 INDEX full_translation_attempt_quota(quota_month,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reading_full_translation_seed (
 id TINYINT NOT NULL PRIMARY KEY,
 seeded_at DATETIME(6) NOT NULL,
 CONSTRAINT full_translation_seed_singleton CHECK(id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
