CREATE TABLE reading_import_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 client_import_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_by BIGINT NOT NULL,payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 article_ids JSON NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY reading_import_client(client_import_id),
 CONSTRAINT reading_import_actor FOREIGN KEY(created_by) REFERENCES identity_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
