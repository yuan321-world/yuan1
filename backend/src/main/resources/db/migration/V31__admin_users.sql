CREATE TABLE admin_user_command (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_id BIGINT NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_enabled BOOLEAN NOT NULL,
 result_version INT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT admin_user_command_actor FOREIGN KEY(actor_id) REFERENCES identity_user(id),
 CONSTRAINT admin_user_command_target FOREIGN KEY(target_id) REFERENCES identity_user(id),
 CONSTRAINT admin_user_command_unique UNIQUE(actor_id,request_id),
 CONSTRAINT admin_user_command_version CHECK(result_version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE admin_user_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 command_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 target_id BIGINT NOT NULL,
 before_enabled BOOLEAN NOT NULL,
 after_enabled BOOLEAN NOT NULL,
 before_version INT NOT NULL,
 after_version INT NOT NULL,
 reason VARCHAR(800) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT admin_user_audit_command UNIQUE(command_id),
 CONSTRAINT admin_user_audit_command_fk FOREIGN KEY(command_id) REFERENCES admin_user_command(id),
 CONSTRAINT admin_user_audit_actor FOREIGN KEY(actor_id) REFERENCES identity_user(id),
 CONSTRAINT admin_user_audit_target FOREIGN KEY(target_id) REFERENCES identity_user(id),
 CONSTRAINT admin_user_audit_change CHECK(before_enabled<>after_enabled AND after_version=before_version+1),
 INDEX admin_user_audit_target_index(target_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
