CREATE TABLE user_writing_favorite (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,material_id BIGINT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,content_snapshot_json JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY writing_favorite_owner(user_id,material_id),INDEX writing_favorite_list(user_id,active,updated_at,id),
 CONSTRAINT writing_favorite_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT writing_favorite_material FOREIGN KEY(material_id) REFERENCES reading_writing_material(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_writing_imitation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,material_id BIGINT NOT NULL,
 text_content VARCHAR(2000) NOT NULL,material_revision INT NOT NULL,content_snapshot_json JSON NOT NULL,lock_version INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY writing_imitation_owner(user_id,material_id),INDEX writing_imitation_list(user_id,updated_at,id),
 CONSTRAINT writing_imitation_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT writing_imitation_material FOREIGN KEY(material_id) REFERENCES reading_writing_material(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
