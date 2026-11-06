ALTER TABLE reading_quiz ADD UNIQUE KEY quiz_article_identity(id,article_id,article_version_id);
ALTER TABLE reading_question ADD UNIQUE KEY question_version_identity(quiz_version_id,id);
CREATE TABLE user_reading_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,quiz_id BIGINT NOT NULL,quiz_version_id BIGINT NOT NULL,
 article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,attempt_no INT NOT NULL,client_request_id CHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',correct_count INT NOT NULL DEFAULT 0,total_count INT NOT NULL,lock_version INT NOT NULL DEFAULT 0,
 active_quiz_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='IN_PROGRESS' THEN quiz_id ELSE NULL END) STORED,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),completed_at TIMESTAMP(6) NULL,
 UNIQUE KEY attempt_request(user_id,client_request_id),UNIQUE KEY attempt_number(user_id,quiz_id,attempt_no),
 UNIQUE KEY attempt_active(user_id,active_quiz_id),UNIQUE KEY attempt_version_identity(id,quiz_version_id),UNIQUE KEY attempt_user_identity(user_id,id),
 INDEX attempt_owner_history(user_id,article_version_id,id),
 CONSTRAINT attempt_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT attempt_quiz_article FOREIGN KEY(quiz_id,article_id,article_version_id) REFERENCES reading_quiz(id,article_id,article_version_id),
 CONSTRAINT attempt_quiz_version FOREIGN KEY(quiz_id,quiz_version_id) REFERENCES reading_quiz_version(quiz_id,id),
 CONSTRAINT attempt_status CHECK(status IN ('IN_PROGRESS','COMPLETED')),
 CONSTRAINT attempt_score CHECK(total_count BETWEEN 1 AND 10 AND correct_count BETWEEN 0 AND total_count),
 CONSTRAINT attempt_completion CHECK((status='COMPLETED' AND completed_at IS NOT NULL) OR (status='IN_PROGRESS' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_reading_attempt_request (
 user_id BIGINT NOT NULL,client_request_id CHAR(36) NOT NULL,attempt_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(user_id,client_request_id),
 CONSTRAINT attempt_request_owner FOREIGN KEY(user_id,attempt_id) REFERENCES user_reading_attempt(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_reading_answer (
 attempt_id BIGINT NOT NULL,quiz_version_id BIGINT NOT NULL,question_id BIGINT NOT NULL,
 draft_choice CHAR(1) NULL,submitted_choice CHAR(1) NULL,is_correct BOOLEAN NULL,submitted_at TIMESTAMP(6) NULL,
 PRIMARY KEY(attempt_id,question_id),
 CONSTRAINT answer_attempt_version FOREIGN KEY(attempt_id,quiz_version_id) REFERENCES user_reading_attempt(id,quiz_version_id),
 CONSTRAINT answer_question_version FOREIGN KEY(quiz_version_id,question_id) REFERENCES reading_question(quiz_version_id,id),
 CONSTRAINT answer_draft_choice CHECK(draft_choice IS NULL OR draft_choice IN ('A','B','C','D')),
 CONSTRAINT answer_submitted_choice CHECK(submitted_choice IS NULL OR submitted_choice IN ('A','B','C','D')),
 CONSTRAINT answer_submission CHECK((submitted_choice IS NULL AND is_correct IS NULL AND submitted_at IS NULL) OR (submitted_choice IS NOT NULL AND is_correct IS NOT NULL AND submitted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
