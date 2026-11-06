ALTER TABLE reading_quiz ADD COLUMN `usage` VARCHAR(16) NOT NULL DEFAULT 'ARTICLE',
 ADD CONSTRAINT quiz_usage CHECK (`usage` IN ('ARTICLE','PRACTICE')),
 ADD INDEX quiz_article_prefix(article_id,article_version_id),
 ADD UNIQUE KEY practice_quiz_identity(article_id,article_version_id,id);
ALTER TABLE reading_quiz DROP INDEX quiz_article_version,
 ADD UNIQUE KEY quiz_article_version_usage(article_id,article_version_id,`usage`);
ALTER TABLE reading_question ADD UNIQUE KEY practice_question_identity(quiz_version_id,id);

CREATE TABLE practice_set (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,article_id BIGINT NOT NULL,
 exam_code VARCHAR(24) NOT NULL,reading_type VARCHAR(24) NOT NULL,
 draft_article_version_id BIGINT NOT NULL,draft_quiz_id BIGINT NOT NULL,draft_metadata_json JSON NOT NULL,
 published_version_id BIGINT NULL,status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',revision INT NOT NULL DEFAULT 0,lock_version INT NOT NULL DEFAULT 1,
 created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY practice_article_exam(article_id,exam_code,reading_type),UNIQUE KEY practice_set_article(id,article_id),
 CONSTRAINT practice_set_article_version FOREIGN KEY(article_id,draft_article_version_id,draft_quiz_id) REFERENCES reading_quiz(article_id,article_version_id,id),
 CONSTRAINT practice_set_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT practice_set_editor FOREIGN KEY(updated_by) REFERENCES identity_user(id),
 CONSTRAINT practice_set_status CHECK(status IN ('DRAFT','PUBLISHED','UNLISTED')),
 CONSTRAINT practice_set_exam CHECK(exam_code='CET4' AND reading_type='DETAILED'),
 CONSTRAINT practice_set_published CHECK(status<>'PUBLISHED' OR published_version_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE practice_set_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,set_id BIGINT NOT NULL,version_no INT NOT NULL,
 article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,quiz_id BIGINT NOT NULL,quiz_version_id BIGINT NOT NULL,
 metadata_json JSON NOT NULL,created_by BIGINT NOT NULL,published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY practice_version_no(set_id,version_no),UNIQUE KEY practice_version_set(set_id,id),UNIQUE KEY practice_version_quiz(id,quiz_version_id),
 CONSTRAINT practice_version_set_owner FOREIGN KEY(set_id,article_id) REFERENCES practice_set(id,article_id),
 CONSTRAINT practice_version_article_owner FOREIGN KEY(article_id,article_version_id,quiz_id) REFERENCES reading_quiz(article_id,article_version_id,id),
 CONSTRAINT practice_version_quiz_owner FOREIGN KEY(quiz_id,quiz_version_id) REFERENCES reading_quiz_version(quiz_id,id),
 CONSTRAINT practice_version_creator FOREIGN KEY(created_by) REFERENCES identity_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE practice_set ADD CONSTRAINT practice_published_owner FOREIGN KEY(id,published_version_id) REFERENCES practice_set_version(set_id,id);
CREATE TABLE practice_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,set_id BIGINT NOT NULL,set_version_id BIGINT NOT NULL,quiz_version_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',source_attempt_id BIGINT NULL,
 correct_count INT NULL,total_count INT NOT NULL,lock_version INT NOT NULL DEFAULT 0,current_question_id BIGINT NULL,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),completed_at TIMESTAMP(6) NULL,
 active_set_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='IN_PROGRESS' THEN set_id ELSE NULL END) STORED,
 UNIQUE KEY practice_active(user_id,kind,active_set_id),UNIQUE KEY practice_attempt_owner(user_id,id),UNIQUE KEY practice_attempt_quiz(id,quiz_version_id),
 INDEX practice_history(user_id,started_at,id),
 CONSTRAINT practice_attempt_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT practice_attempt_version FOREIGN KEY(set_id,set_version_id) REFERENCES practice_set_version(set_id,id),
 CONSTRAINT practice_attempt_quiz_version FOREIGN KEY(set_version_id,quiz_version_id) REFERENCES practice_set_version(id,quiz_version_id),
 CONSTRAINT practice_attempt_source_owner FOREIGN KEY(user_id,source_attempt_id) REFERENCES practice_attempt(user_id,id),
 CONSTRAINT practice_attempt_kind CHECK(kind IN ('FULL','MISTAKES')),
 CONSTRAINT practice_attempt_status CHECK(status IN ('IN_PROGRESS','COMPLETED')),
 CONSTRAINT practice_attempt_total CHECK(total_count BETWEEN 1 AND 10),
 CONSTRAINT practice_attempt_score CHECK((status='IN_PROGRESS' AND correct_count IS NULL AND completed_at IS NULL) OR (status='COMPLETED' AND correct_count BETWEEN 0 AND total_count AND completed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE practice_answer (
 attempt_id BIGINT NOT NULL,quiz_version_id BIGINT NOT NULL,question_id BIGINT NOT NULL,ordinal INT NOT NULL,
 draft_choice CHAR(1) NULL,submitted_choice CHAR(1) NULL,is_correct BOOLEAN NULL,submitted_at TIMESTAMP(6) NULL,
 PRIMARY KEY(attempt_id,question_id),UNIQUE KEY practice_answer_order(attempt_id,ordinal),
 CONSTRAINT practice_answer_attempt FOREIGN KEY(attempt_id,quiz_version_id) REFERENCES practice_attempt(id,quiz_version_id),
 CONSTRAINT practice_answer_question FOREIGN KEY(quiz_version_id,question_id) REFERENCES reading_question(quiz_version_id,id),
 CONSTRAINT practice_answer_draft CHECK(draft_choice IS NULL OR draft_choice IN ('A','B','C','D')),
 CONSTRAINT practice_answer_choice CHECK(submitted_choice IS NULL OR submitted_choice IN ('A','B','C','D')),
 CONSTRAINT practice_answer_ordinal CHECK(ordinal BETWEEN 0 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE practice_request (
 user_id BIGINT NOT NULL,request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,operation VARCHAR(16) NOT NULL,request_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 attempt_id BIGINT NULL,set_id BIGINT NULL,response_version INT NOT NULL,
 PRIMARY KEY(user_id,request_id),
 CONSTRAINT practice_request_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT practice_request_attempt FOREIGN KEY(user_id,attempt_id) REFERENCES practice_attempt(user_id,id),
 CONSTRAINT practice_request_set FOREIGN KEY(set_id) REFERENCES practice_set(id),
 CONSTRAINT practice_request_result CHECK((operation IN ('START','SUBMIT','RETRY') AND attempt_id IS NOT NULL AND set_id IS NULL) OR (operation='IMPORT' AND attempt_id IS NULL AND set_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE practice_mistake (
 user_id BIGINT NOT NULL,question_id BIGINT NOT NULL,set_id BIGINT NOT NULL,set_version_id BIGINT NOT NULL,
 source_attempt_id BIGINT NOT NULL,latest_attempt_id BIGINT NOT NULL,status VARCHAR(16) NOT NULL,wrong_count INT NOT NULL,
 first_wrong_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),last_completed_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(user_id,question_id),INDEX practice_mistake_state(user_id,status,last_completed_at,question_id),
 CONSTRAINT practice_mistake_source FOREIGN KEY(user_id,source_attempt_id) REFERENCES practice_attempt(user_id,id),
 CONSTRAINT practice_mistake_latest FOREIGN KEY(user_id,latest_attempt_id) REFERENCES practice_attempt(user_id,id),
 CONSTRAINT practice_mistake_set FOREIGN KEY(set_id,set_version_id) REFERENCES practice_set_version(set_id,id),
 CONSTRAINT practice_mistake_question FOREIGN KEY(question_id) REFERENCES reading_question(id),
 CONSTRAINT practice_mistake_status CHECK(status IN ('PENDING','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
