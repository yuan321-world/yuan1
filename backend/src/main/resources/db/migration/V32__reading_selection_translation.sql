CREATE TABLE reading_selection_translation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,article_id BIGINT NOT NULL,version_id BIGINT NOT NULL,
 scope_hash CHAR(64) CHARACTER SET ascii NOT NULL,source_text TEXT NOT NULL,status VARCHAR(16) NOT NULL,
 translated_text TEXT NULL,model VARCHAR(80) NOT NULL,error_code VARCHAR(64) NULL,
 budget_day DATE NOT NULL,reserved_microusd BIGINT NOT NULL DEFAULT 5000,settled_microusd BIGINT NULL,input_tokens BIGINT NULL,output_tokens BIGINT NULL,
 lease_token CHAR(36) NULL,lease_until DATETIME(6) NULL,created_at DATETIME(6) NOT NULL,completed_at DATETIME(6) NULL,
 CONSTRAINT selection_translation_owner FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT selection_translation_version FOREIGN KEY(version_id) REFERENCES reading_article_version(id),
 CONSTRAINT selection_translation_article FOREIGN KEY(article_id) REFERENCES reading_article(id),
 CONSTRAINT selection_translation_status CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT selection_translation_cost CHECK(reserved_microusd>=0 AND (settled_microusd IS NULL OR settled_microusd>=0)),
 UNIQUE KEY selection_translation_user_id(user_id,id),INDEX selection_translation_scope(user_id,scope_hash),INDEX selection_translation_queue(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE reading_selection_translation_request (
 user_id BIGINT NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,request_hash CHAR(64) CHARACTER SET ascii NOT NULL,translation_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,request_id),CONSTRAINT selection_translation_request_owner FOREIGN KEY(user_id,translation_id) REFERENCES reading_selection_translation(user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
