CREATE TABLE IF NOT EXISTS practice_paper (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 draft_json JSON NOT NULL, source_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', revision INT NOT NULL DEFAULT 0, lock_version INT NOT NULL DEFAULT 0,
 published_version_id BIGINT NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT paper_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT paper_editor FOREIGN KEY(updated_by) REFERENCES identity_user(id),
 CONSTRAINT paper_status CHECK(status IN ('DRAFT','PUBLISHED','UNLISTED')),
 CONSTRAINT paper_has_version CHECK(status <> 'PUBLISHED' OR published_version_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_paper_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, paper_id BIGINT NOT NULL, version_no INT NOT NULL,
 metadata_json JSON NOT NULL, created_by BIGINT NOT NULL, published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY paper_revision(paper_id,version_no), UNIQUE KEY paper_version_owner(paper_id,id),
 CONSTRAINT paper_version_parent FOREIGN KEY(paper_id) REFERENCES practice_paper(id),
 CONSTRAINT paper_version_creator FOREIGN KEY(created_by) REFERENCES identity_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @paper_fk = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='practice_paper' AND constraint_name='paper_published_owner');
SET @paper_sql = IF(@paper_fk=0,'ALTER TABLE practice_paper ADD CONSTRAINT paper_published_owner FOREIGN KEY(id,published_version_id) REFERENCES practice_paper_version(paper_id,id)','SELECT 1');
PREPARE paper_stmt FROM @paper_sql;
EXECUTE paper_stmt;
DEALLOCATE PREPARE paper_stmt;
CREATE TABLE IF NOT EXISTS practice_paper_component (
 paper_version_id BIGINT NOT NULL, slot VARCHAR(24) NOT NULL, set_version_id BIGINT NOT NULL, sort_order INT NOT NULL,
 PRIMARY KEY(paper_version_id,slot), UNIQUE KEY paper_component_version(paper_version_id,set_version_id),
 CONSTRAINT paper_component_parent FOREIGN KEY(paper_version_id) REFERENCES practice_paper_version(id),
 CONSTRAINT paper_component_set FOREIGN KEY(set_version_id) REFERENCES practice_set_version(id),
 CONSTRAINT paper_component_slot CHECK(slot IN ('WRITING','LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2','TRANSLATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_paper_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, paper_id BIGINT NOT NULL, paper_version_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS', lock_version INT NOT NULL DEFAULT 0,
 current_slot VARCHAR(24) NOT NULL DEFAULT 'WRITING', current_question_id VARCHAR(32) NULL,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at TIMESTAMP(6) NULL,
 active_paper_id BIGINT GENERATED ALWAYS AS(CASE WHEN status='IN_PROGRESS' THEN paper_id ELSE NULL END) STORED,
 UNIQUE KEY paper_active(user_id,active_paper_id), UNIQUE KEY paper_attempt_user(user_id,id), UNIQUE KEY paper_attempt_version(id,paper_version_id),
 INDEX paper_history(user_id,started_at,id),
 CONSTRAINT paper_attempt_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT paper_attempt_published_parent FOREIGN KEY(paper_id,paper_version_id) REFERENCES practice_paper_version(paper_id,id),
 CONSTRAINT paper_attempt_state CHECK((status='IN_PROGRESS' AND completed_at IS NULL) OR (status='COMPLETED' AND completed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_paper_response (
 attempt_id BIGINT NOT NULL, paper_version_id BIGINT NOT NULL, slot VARCHAR(24) NOT NULL,
 response_json JSON NOT NULL, result_json JSON NULL,
 PRIMARY KEY(attempt_id,slot),
 CONSTRAINT paper_response_attempt FOREIGN KEY(attempt_id,paper_version_id) REFERENCES practice_paper_attempt(id,paper_version_id),
 CONSTRAINT paper_response_component FOREIGN KEY(paper_version_id,slot) REFERENCES practice_paper_component(paper_version_id,slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_paper_request (
 user_id BIGINT NOT NULL, operation VARCHAR(16) NOT NULL, request_id VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL, attempt_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,operation,request_id),
 CONSTRAINT paper_request_owner FOREIGN KEY(user_id,attempt_id) REFERENCES practice_paper_attempt(user_id,id),
 CONSTRAINT paper_request_operation CHECK(operation IN ('START','SUBMIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

