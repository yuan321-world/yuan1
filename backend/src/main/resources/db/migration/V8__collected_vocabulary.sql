CREATE TABLE user_word (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,entry_id BIGINT NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY user_word_owner(user_id,entry_id),INDEX user_word_list(user_id,active,updated_at,id),
 CONSTRAINT user_word_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT user_word_entry FOREIGN KEY(entry_id) REFERENCES dictionary_entry(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_word_source (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,word_id BIGINT NOT NULL,article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,
 sentence_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,start_offset INT NOT NULL,end_offset INT NOT NULL,
 surface_form VARCHAR(64) NOT NULL,sentence_snapshot TEXT NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY user_word_source_position(word_id,article_version_id,sentence_key,start_offset,end_offset),
 CONSTRAINT user_word_source_word FOREIGN KEY(word_id) REFERENCES user_word(id),
 CONSTRAINT user_word_source_version FOREIGN KEY(article_id,article_version_id) REFERENCES reading_article_version(article_id,id),
 CONSTRAINT user_word_source_offsets CHECK(start_offset>=0 AND end_offset>start_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
