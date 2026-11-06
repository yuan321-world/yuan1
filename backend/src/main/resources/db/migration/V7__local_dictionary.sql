CREATE TABLE dictionary_entry (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 lemma VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 phonetic VARCHAR(128) NULL,senses JSON NOT NULL,source_note VARCHAR(500) NOT NULL,
 UNIQUE KEY dictionary_lemma(lemma)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE dictionary_form (
 form VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,entry_id BIGINT NOT NULL,
 PRIMARY KEY(form,entry_id),CONSTRAINT dictionary_form_entry FOREIGN KEY(entry_id) REFERENCES dictionary_entry(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
