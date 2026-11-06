ALTER TABLE vocabulary_learning_word ADD COLUMN personal_enrolled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE vocabulary_wordbook (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,code VARCHAR(48) NOT NULL,data_version VARCHAR(64) NOT NULL,title VARCHAR(160) NOT NULL,
 source_url VARCHAR(500) NOT NULL,notice VARCHAR(1000) NOT NULL,manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_count INT NOT NULL,available_count INT NOT NULL,missing_phonetic_count INT NOT NULL,excluded_json JSON NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',UNIQUE KEY wordbook_version(code,data_version),CHECK(status IN ('PUBLISHED','UNLISTED')),
 CHECK(original_count>=available_count AND available_count>0),CHECK(missing_phonetic_count BETWEEN 0 AND available_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_wordbook_entry (
 book_id BIGINT NOT NULL,entry_id BIGINT NOT NULL,sort_order INT NOT NULL,PRIMARY KEY(book_id,entry_id),UNIQUE KEY wordbook_order(book_id,sort_order),
 FOREIGN KEY(book_id) REFERENCES vocabulary_wordbook(id),FOREIGN KEY(entry_id) REFERENCES dictionary_entry(id),CHECK(sort_order>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_source (
 user_id BIGINT PRIMARY KEY,kind VARCHAR(16) NOT NULL,book_id BIGINT NULL,FOREIGN KEY(user_id) REFERENCES identity_user(id),FOREIGN KEY(book_id) REFERENCES vocabulary_wordbook(id),
 CHECK((kind='PERSONAL' AND book_id IS NULL) OR (kind='BOOK' AND book_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
