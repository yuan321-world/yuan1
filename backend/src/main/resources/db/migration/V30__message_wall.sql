CREATE TABLE wall_layout_state (id INT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB;
INSERT INTO wall_layout_state(id) VALUES(1);
CREATE TABLE wall_message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL, category VARCHAR(20) NOT NULL, color VARCHAR(12) NOT NULL,
 title VARCHAR(320) NOT NULL DEFAULT '', body TEXT NOT NULL, author_admin BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(12) NOT NULL DEFAULT 'VISIBLE', edited_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0, position_x INT NOT NULL, position_y INT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 KEY wall_public(status,id),KEY wall_owner(user_id,id),
 CONSTRAINT wall_message_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT wall_message_kind CHECK(kind IN ('MESSAGE','ANNOUNCEMENT')),
 CONSTRAINT wall_message_status CHECK(status IN ('VISIBLE','HIDDEN','DELETED')),
 CONSTRAINT wall_message_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wall_comment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, message_id BIGINT NOT NULL,user_id BIGINT NOT NULL,
 body TEXT NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'VISIBLE',version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY wall_comment_parent(id,message_id),KEY wall_comments(message_id,status,id),
 CONSTRAINT wall_comment_message FOREIGN KEY(message_id) REFERENCES wall_message(id),
 CONSTRAINT wall_comment_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT wall_comment_status CHECK(status IN ('VISIBLE','HIDDEN','DELETED')),
 CONSTRAINT wall_comment_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wall_like (
 user_id BIGINT NOT NULL,message_id BIGINT NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(user_id,message_id),KEY wall_like_message(message_id),
 CONSTRAINT wall_like_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT wall_like_message FOREIGN KEY(message_id) REFERENCES wall_message(id)
) ENGINE=InnoDB;
CREATE TABLE wall_report (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,message_id BIGINT NOT NULL,comment_id BIGINT NULL,
 target_type VARCHAR(12) NOT NULL,reason VARCHAR(24) NOT NULL,detail TEXT NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING',created_at DATETIME(6) NOT NULL,handled_at DATETIME(6) NULL,
 pending_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN CONCAT(target_type,':',COALESCE(comment_id,message_id)) ELSE NULL END) STORED,
 UNIQUE KEY wall_report_pending(user_id,pending_key),KEY wall_reports(status,id),
 CONSTRAINT wall_report_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT wall_report_message FOREIGN KEY(message_id) REFERENCES wall_message(id),
 CONSTRAINT wall_report_comment FOREIGN KEY(comment_id,message_id) REFERENCES wall_comment(id,message_id),
 CONSTRAINT wall_report_target CHECK((target_type='MESSAGE' AND comment_id IS NULL) OR (target_type='COMMENT' AND comment_id IS NOT NULL)),
 CONSTRAINT wall_report_status CHECK(status IN ('PENDING','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wall_moderation_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,actor_id BIGINT NOT NULL,message_id BIGINT NOT NULL,comment_id BIGINT NULL,
 action VARCHAR(12) NOT NULL,before_text TEXT NOT NULL,after_text TEXT NOT NULL,
 author_reason VARCHAR(800) NOT NULL,internal_note TEXT NOT NULL,created_at DATETIME(6) NOT NULL,
 CONSTRAINT wall_log_actor FOREIGN KEY(actor_id) REFERENCES identity_user(id),
 CONSTRAINT wall_log_message FOREIGN KEY(message_id) REFERENCES wall_message(id),
 CONSTRAINT wall_log_comment FOREIGN KEY(comment_id,message_id) REFERENCES wall_comment(id,message_id),
 CONSTRAINT wall_log_action CHECK(action IN ('EDIT','HIDE','RESTORE','DELETE','KEEP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wall_notification (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,recipient_id BIGINT NOT NULL,moderation_id BIGINT NOT NULL,
 message_id BIGINT NOT NULL,comment_id BIGINT NULL,action VARCHAR(12) NOT NULL,
 author_reason VARCHAR(800) NOT NULL,excerpt TEXT NOT NULL,created_at DATETIME(6) NOT NULL,read_at DATETIME(6) NULL,
 UNIQUE KEY wall_notice_once(moderation_id,recipient_id),KEY wall_notice_owner(recipient_id,id),
 CONSTRAINT wall_notice_recipient FOREIGN KEY(recipient_id) REFERENCES identity_user(id),
 CONSTRAINT wall_notice_log FOREIGN KEY(moderation_id) REFERENCES wall_moderation_log(id),
 CONSTRAINT wall_notice_message FOREIGN KEY(message_id) REFERENCES wall_message(id),
 CONSTRAINT wall_notice_comment FOREIGN KEY(comment_id,message_id) REFERENCES wall_comment(id,message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE wall_request (
 user_id BIGINT NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation VARCHAR(80) NOT NULL,request_hash CHAR(64) CHARACTER SET ascii NOT NULL,result_json JSON NOT NULL,
 target_id BIGINT NULL,created_at DATETIME(6) NOT NULL,PRIMARY KEY(user_id,request_id),
 CONSTRAINT wall_request_user FOREIGN KEY(user_id) REFERENCES identity_user(id)
) ENGINE=InnoDB;
CREATE TABLE wall_user_quota (
 user_id BIGINT PRIMARY KEY,quota_day DATE NOT NULL,message_count INT NOT NULL DEFAULT 0,
 comment_count INT NOT NULL DEFAULT 0,report_count INT NOT NULL DEFAULT 0,
 last_message DATETIME(6) NULL,last_comment DATETIME(6) NULL,
 CONSTRAINT wall_quota_user FOREIGN KEY(user_id) REFERENCES identity_user(id)
) ENGINE=InnoDB;
