CREATE TABLE practice_subjective_review (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,
 subjective_attempt_id BIGINT NULL,paper_attempt_id BIGINT NULL,slot VARCHAR(24) NOT NULL,
 scope_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_json JSON NOT NULL,response_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) NOT NULL,consent_version VARCHAR(32) NOT NULL,
 model VARCHAR(80) NOT NULL,prompt_version VARCHAR(48) NOT NULL,rubric_version VARCHAR(48) NOT NULL,
 result_json JSON NULL,error_code VARCHAR(40) NULL,lease_token CHAR(36) NULL,
 created_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6) NULL,lease_until TIMESTAMP(6) NULL,
 budget_day DATE NOT NULL,reserved_microusd BIGINT NOT NULL,settled_microusd BIGINT NULL,
 input_tokens BIGINT NULL,output_tokens BIGINT NULL,
 active_scope CHAR(64) GENERATED ALWAYS AS(CASE WHEN status IN ('QUEUED','RUNNING') THEN scope_hash ELSE NULL END) STORED,
 UNIQUE KEY review_active(user_id,active_scope),UNIQUE KEY review_owner(user_id,id),
 INDEX review_history(user_id,scope_hash,id),INDEX review_queue(status,created_at,id),
 CONSTRAINT ai_review_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT ai_review_subjective_owner FOREIGN KEY(user_id,subjective_attempt_id) REFERENCES practice_subjective_attempt(user_id,id),
 CONSTRAINT ai_review_paper_owner FOREIGN KEY(user_id,paper_attempt_id) REFERENCES practice_paper_attempt(user_id,id),
 CONSTRAINT ai_review_paper_slot FOREIGN KEY(paper_attempt_id,slot) REFERENCES practice_paper_response(attempt_id,slot),
 CONSTRAINT ai_review_one_source CHECK((subjective_attempt_id IS NOT NULL AND paper_attempt_id IS NULL) OR (subjective_attempt_id IS NULL AND paper_attempt_id IS NOT NULL)),
 CONSTRAINT ai_review_slot CHECK(slot IN ('WRITING','TRANSLATION')),
 CONSTRAINT ai_review_state CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT ai_review_budget_positive CHECK(reserved_microusd>=0 AND (settled_microusd IS NULL OR settled_microusd>=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE practice_subjective_review_request (
 user_id BIGINT NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii NOT NULL,review_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,request_id),CONSTRAINT ai_review_request_owner FOREIGN KEY(user_id,review_id) REFERENCES practice_subjective_review(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE practice_ai_daily_budget (
 budget_day DATE PRIMARY KEY,used_microusd BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ai_review_budget_nonnegative CHECK(used_microusd>=0)
) ENGINE=InnoDB;
