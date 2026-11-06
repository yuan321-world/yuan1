CREATE TABLE vocabulary_learning_setting (
 user_id BIGINT PRIMARY KEY,new_goal INT NOT NULL,review_goal INT NOT NULL,
 FOREIGN KEY(user_id) REFERENCES identity_user(id),CHECK(new_goal BETWEEN 1 AND 100),CHECK(review_goal BETWEEN 1 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_word (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,entry_id BIGINT NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,
 stage INT NOT NULL DEFAULT 0,due_at TIMESTAMP(6) NULL,last_passed_at TIMESTAMP(6) NULL,rule_version VARCHAR(32) NOT NULL DEFAULT 'fixed-v1-binary',
 revision INT NOT NULL DEFAULT 0,UNIQUE KEY learning_owner_entry(user_id,entry_id),UNIQUE KEY learning_word_owner(id,user_id),
 INDEX learning_due(user_id,active,due_at,id),FOREIGN KEY(user_id) REFERENCES identity_user(id),FOREIGN KEY(entry_id) REFERENCES dictionary_entry(id),CHECK(stage BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_session (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,mode VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',phase VARCHAR(16) NOT NULL DEFAULT 'QUESTION',version INT NOT NULL DEFAULT 0,
 queue_json JSON NOT NULL,created_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6) NULL,
 UNIQUE KEY learning_session_owner(id,user_id),INDEX learning_open(user_id,status,id),FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CHECK(kind IN ('NEW','REVIEW')),CHECK(mode IN ('RECOGNITION','SPELLING')),CHECK(status IN ('ACTIVE','PAUSED','COMPLETED')),CHECK(phase IN ('QUESTION','DEFINITION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,session_id BIGINT NOT NULL,learning_word_id BIGINT NOT NULL,
 snapshot JSON NOT NULL,passed BOOLEAN NOT NULL DEFAULT FALSE,failure_count INT NOT NULL DEFAULT 0,outcome JSON NULL,
 UNIQUE KEY learning_session_word(session_id,learning_word_id),UNIQUE KEY learning_item_owner(id,user_id),
 FOREIGN KEY(session_id,user_id) REFERENCES vocabulary_learning_session(id,user_id),
 FOREIGN KEY(learning_word_id,user_id) REFERENCES vocabulary_learning_word(id,user_id),CHECK(failure_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,session_id BIGINT NOT NULL,item_id BIGINT NOT NULL,
 action VARCHAR(16) NOT NULL,correct BOOLEAN NOT NULL,snapshot JSON NOT NULL,created_at TIMESTAMP(6) NOT NULL,
 INDEX learning_event_day(user_id,created_at),FOREIGN KEY(session_id,user_id) REFERENCES vocabulary_learning_session(id,user_id),
 FOREIGN KEY(item_id,user_id) REFERENCES vocabulary_learning_item(id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE vocabulary_learning_command (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,response_json JSON NOT NULL,created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY learning_command_once(user_id,request_id),FOREIGN KEY(user_id) REFERENCES identity_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
