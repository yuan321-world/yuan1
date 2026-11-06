CREATE TABLE reading_progress (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,
 sentence_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,position_offset INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'READING',lock_version INT NOT NULL DEFAULT 0,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),completed_at TIMESTAMP(6) NULL,
 CONSTRAINT reading_progress_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT reading_progress_version FOREIGN KEY(article_id,article_version_id) REFERENCES reading_article_version(article_id,id),
 CONSTRAINT reading_progress_status CHECK(status IN ('READING','COMPLETED')),
 CONSTRAINT reading_progress_offset CHECK(position_offset>=0),
 UNIQUE KEY reading_progress_owner(user_id,article_version_id),INDEX reading_progress_history(user_id,updated_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
