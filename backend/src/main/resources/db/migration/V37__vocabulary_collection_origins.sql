CREATE TABLE user_word_learning_origin (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 word_id BIGINT NOT NULL,
 session_id BIGINT NULL,
 source_key VARCHAR(100) NOT NULL,
 source_kind VARCHAR(16) NOT NULL,
 title VARCHAR(240) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY word_learning_origin_once(word_id,source_key),
 CONSTRAINT word_learning_origin_word FOREIGN KEY(word_id) REFERENCES user_word(id) ON DELETE CASCADE,
 CONSTRAINT word_learning_origin_session FOREIGN KEY(session_id) REFERENCES vocabulary_learning_session(id) ON DELETE SET NULL,
 CONSTRAINT word_learning_origin_kind CHECK(source_kind IN ('BOOK','LEARNING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
