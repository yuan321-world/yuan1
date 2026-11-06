CREATE TABLE vocabulary_phrase (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 canonical_text VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 display_text VARCHAR(160) NOT NULL,
 meaning_zh VARCHAR(500) NOT NULL,
 note TEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 enrolled BOOLEAN NOT NULL DEFAULT FALSE,
 exempt BOOLEAN NOT NULL DEFAULT FALSE,
 independent_passes INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_phrase_owner_text(user_id,canonical_text),
 CONSTRAINT fk_phrase_owner FOREIGN KEY(user_id) REFERENCES identity_user(id)
);
CREATE TABLE vocabulary_phrase_source (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 phrase_id BIGINT NOT NULL,
 article_id BIGINT NOT NULL,
 article_version_id BIGINT NOT NULL,
 title_snapshot VARCHAR(500) NOT NULL,
 sentence_key VARCHAR(100) NOT NULL,
 start_offset INT NOT NULL,
 end_offset INT NOT NULL,
 sentence_snapshot TEXT NOT NULL,
 practice_attempt_id BIGINT NULL,
 paper_attempt_id BIGINT NULL,
 paper_slot VARCHAR(24) NULL,
 UNIQUE KEY uq_phrase_source(phrase_id,article_version_id,sentence_key,start_offset,end_offset),
 CONSTRAINT fk_phrase_source FOREIGN KEY(phrase_id) REFERENCES vocabulary_phrase(id)
);
CREATE TABLE vocabulary_phrase_round (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 state_json JSON NOT NULL,
 CONSTRAINT fk_phrase_round_owner FOREIGN KEY(user_id) REFERENCES identity_user(id),
 KEY ix_phrase_round_owner(user_id,status)
);
CREATE TABLE vocabulary_phrase_command (
 user_id BIGINT NOT NULL,
 request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) NOT NULL,
 response_json JSON NOT NULL,
 PRIMARY KEY(user_id,request_id),
 CONSTRAINT fk_phrase_command_owner FOREIGN KEY(user_id) REFERENCES identity_user(id)
);
