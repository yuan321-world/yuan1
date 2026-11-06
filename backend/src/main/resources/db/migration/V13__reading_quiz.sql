CREATE TABLE reading_quiz (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,
 draft_json JSON NOT NULL,published_version_id BIGINT NULL,
 revision INT NOT NULL DEFAULT 0,lock_version INT NOT NULL DEFAULT 1,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY quiz_article_version(article_id,article_version_id),
 CONSTRAINT quiz_article_owner FOREIGN KEY(article_id,article_version_id) REFERENCES reading_article_version(article_id,id),
 CONSTRAINT quiz_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT quiz_editor FOREIGN KEY(updated_by) REFERENCES identity_user(id),
 CONSTRAINT quiz_status CHECK(status IN ('DRAFT','PUBLISHED','UNLISTED')),
 CONSTRAINT quiz_published CHECK(status <> 'PUBLISHED' OR published_version_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE reading_quiz_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,quiz_id BIGINT NOT NULL,version_no INT NOT NULL,
 created_by BIGINT NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY quiz_version_no(quiz_id,version_no),UNIQUE KEY quiz_version_owner(quiz_id,id),
 CONSTRAINT quiz_version_quiz FOREIGN KEY(quiz_id) REFERENCES reading_quiz(id),
 CONSTRAINT quiz_version_creator FOREIGN KEY(created_by) REFERENCES identity_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE reading_quiz ADD CONSTRAINT quiz_published_owner FOREIGN KEY(id,published_version_id) REFERENCES reading_quiz_version(quiz_id,id);
CREATE TABLE reading_question (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,quiz_version_id BIGINT NOT NULL,
 question_key VARCHAR(64) NOT NULL,question_type VARCHAR(24) NOT NULL,stem TEXT NOT NULL,options_json JSON NOT NULL,
 correct_option CHAR(1) NOT NULL,explanation TEXT NOT NULL,sort_order INT NOT NULL,
 UNIQUE KEY quiz_question_key(quiz_version_id,question_key),INDEX quiz_question_order(quiz_version_id,sort_order,id),
 CONSTRAINT question_version FOREIGN KEY(quiz_version_id) REFERENCES reading_quiz_version(id),
 CONSTRAINT question_choice CHECK(correct_option IN ('A','B','C','D')),
 CONSTRAINT question_type CHECK(question_type IN ('MAIN_IDEA','DETAIL','INFERENCE','VOCABULARY','ATTITUDE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
