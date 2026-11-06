CREATE TABLE reading_category (
 id BIGINT NOT NULL PRIMARY KEY, code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 name VARCHAR(64) NOT NULL, sort_order INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO reading_category(id,code,name,sort_order) VALUES
 (1,'STUDY','学习',1),(2,'LIFE','生活',2),(3,'TECHNOLOGY','科技',3),(4,'CULTURE','文化',4),(5,'WORK','职场',5);
CREATE TABLE reading_article (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, category_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', draft_version_id BIGINT NULL,published_version_id BIGINT NULL,
 lock_version INT NOT NULL DEFAULT 0,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),published_at TIMESTAMP(6) NULL,
 CONSTRAINT reading_article_status CHECK(status IN ('DRAFT','PUBLISHED','UNLISTED')),
 CONSTRAINT reading_article_category FOREIGN KEY(category_id) REFERENCES reading_category(id),
 CONSTRAINT reading_article_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT reading_article_editor FOREIGN KEY(updated_by) REFERENCES identity_user(id),
 INDEX reading_article_listing(status,category_id,published_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE reading_article_version (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,article_id BIGINT NOT NULL,version_no INT NOT NULL,category_id BIGINT NOT NULL,
 title VARCHAR(160) NOT NULL,title_zh VARCHAR(160) NOT NULL,summary VARCHAR(500) NOT NULL,difficulty VARCHAR(8) NOT NULL,
 content_json JSON NOT NULL,content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 word_count INT NOT NULL,estimated_minutes INT NOT NULL,author VARCHAR(160) NOT NULL,
 source_type VARCHAR(16) NOT NULL,source_url VARCHAR(2048) NULL,usage_note VARCHAR(1000) NOT NULL,
 created_by BIGINT NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT reading_version_article FOREIGN KEY(article_id) REFERENCES reading_article(id),
 CONSTRAINT reading_version_category FOREIGN KEY(category_id) REFERENCES reading_category(id),
 CONSTRAINT reading_version_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT reading_version_difficulty CHECK(difficulty IN ('B1','B2')),
 CONSTRAINT reading_version_source CHECK(source_type IN ('ORIGINAL','THIRD_PARTY')),
 UNIQUE KEY reading_version_number(article_id,version_no),UNIQUE KEY reading_version_owner(article_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE reading_article
 ADD CONSTRAINT reading_article_draft FOREIGN KEY(id,draft_version_id) REFERENCES reading_article_version(article_id,id),
 ADD CONSTRAINT reading_article_published FOREIGN KEY(id,published_version_id) REFERENCES reading_article_version(article_id,id);
