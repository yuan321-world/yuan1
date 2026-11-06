CREATE TABLE reading_writing_material (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 article_id BIGINT NOT NULL,article_version_id BIGINT NOT NULL,
 content_json JSON NOT NULL,published_content_json JSON NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',revision INT NOT NULL DEFAULT 0,lock_version INT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),published_at TIMESTAMP(6) NULL,
 CONSTRAINT writing_material_version FOREIGN KEY(article_id,article_version_id) REFERENCES reading_article_version(article_id,id),
 CONSTRAINT writing_material_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT writing_material_editor FOREIGN KEY(updated_by) REFERENCES identity_user(id),
 CONSTRAINT writing_material_status CHECK(status IN ('DRAFT','PUBLISHED','UNLISTED','ARCHIVED')),
 CONSTRAINT writing_material_public CHECK(status <> 'PUBLISHED' OR published_content_json IS NOT NULL),
 INDEX writing_material_listing(article_id,article_version_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
