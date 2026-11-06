ALTER TABLE reading_progress ADD UNIQUE KEY progress_evidence_identity(user_id,id,article_id,article_version_id);
ALTER TABLE user_reading_attempt ADD UNIQUE KEY attempt_evidence_identity(user_id,id,article_id,article_version_id);
CREATE TABLE reading_article_qualification (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,
 progress_id BIGINT NOT NULL,attempt_id BIGINT NOT NULL,qualified_date DATE NOT NULL,qualified_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY qualification_article(user_id,article_id),UNIQUE KEY qualification_owner_identity(user_id,id),
 CONSTRAINT qualification_progress FOREIGN KEY(user_id,progress_id,article_id,article_version_id) REFERENCES reading_progress(user_id,id,article_id,article_version_id),
 CONSTRAINT qualification_attempt FOREIGN KEY(user_id,attempt_id,article_id,article_version_id) REFERENCES user_reading_attempt(user_id,id,article_id,article_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE reading_daily_checkin (
 user_id BIGINT NOT NULL,checkin_date DATE NOT NULL,qualification_id BIGINT NOT NULL,created_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(user_id,checkin_date),UNIQUE KEY checkin_qualification(qualification_id),
 CONSTRAINT checkin_owned_evidence FOREIGN KEY(user_id,qualification_id) REFERENCES reading_article_qualification(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
