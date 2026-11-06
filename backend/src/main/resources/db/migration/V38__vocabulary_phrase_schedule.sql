ALTER TABLE vocabulary_phrase
 ADD COLUMN stage INT NOT NULL DEFAULT 0,
 ADD COLUMN due_at DATETIME(6) NULL,
 ADD COLUMN rule_version VARCHAR(40) NOT NULL DEFAULT 'phrase-v1-seven',
 ADD CONSTRAINT chk_phrase_stage CHECK(stage BETWEEN 0 AND 7),
 ADD UNIQUE KEY uq_phrase_id_owner(id,user_id),
 ADD KEY ix_phrase_due_owner(user_id,enrolled,exempt,stage,due_at);
ALTER TABLE vocabulary_phrase_round ADD UNIQUE KEY uq_phrase_round_id_owner(id,user_id);
CREATE TABLE vocabulary_phrase_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 phrase_id BIGINT NOT NULL,
 round_id BIGINT NOT NULL,
 request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action VARCHAR(16) NOT NULL,
 correct BOOLEAN NOT NULL,
 independent BOOLEAN NOT NULL,
 assisted BOOLEAN NOT NULL,
 failures_before INT NOT NULL,
 failures_after INT NOT NULL,
 stage_before INT NOT NULL,
 stage_after INT NOT NULL,
 due_at DATETIME(6) NULL,
 rule_version VARCHAR(40) NOT NULL,
 evidence_json JSON NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_phrase_event_command(user_id,request_id),
 KEY ix_phrase_event_owner_time(user_id,created_at),
 CONSTRAINT fk_phrase_event_phrase FOREIGN KEY(phrase_id,user_id) REFERENCES vocabulary_phrase(id,user_id),
 CONSTRAINT fk_phrase_event_round FOREIGN KEY(round_id,user_id) REFERENCES vocabulary_phrase_round(id,user_id),
 CONSTRAINT fk_phrase_event_command FOREIGN KEY(user_id,request_id) REFERENCES vocabulary_phrase_command(user_id,request_id)
);
