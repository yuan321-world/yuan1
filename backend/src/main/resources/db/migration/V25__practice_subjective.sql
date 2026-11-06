-- Keep objective quiz ownership; subjective material has its own immutable version FK.
ALTER TABLE practice_set
 MODIFY draft_quiz_id BIGINT NULL,
 ADD COLUMN subjective_json JSON NULL,
 ADD CONSTRAINT practice_subjective_material FOREIGN KEY(article_id,draft_article_version_id) REFERENCES reading_article_version(article_id,id),
 DROP CHECK practice_set_exam,
 ADD CONSTRAINT practice_set_exam CHECK(exam_code='CET4' AND ((section='READING' AND reading_type IN ('DETAILED','CLOZE','MATCH')) OR (section='LISTENING' AND reading_type='LISTENING') OR (section IN ('WRITING','TRANSLATION') AND section=reading_type))),
 ADD CONSTRAINT practice_set_quiz_kind CHECK((reading_type IN ('WRITING','TRANSLATION') AND draft_quiz_id IS NULL AND subjective_json IS NOT NULL) OR (reading_type NOT IN ('WRITING','TRANSLATION') AND draft_quiz_id IS NOT NULL AND subjective_json IS NULL));
ALTER TABLE practice_set_version
 MODIFY quiz_id BIGINT NULL, MODIFY quiz_version_id BIGINT NULL,
 ADD COLUMN subjective_json JSON NULL,
 ADD CONSTRAINT practice_subjective_version_material FOREIGN KEY(article_id,article_version_id) REFERENCES reading_article_version(article_id,id),
 DROP CHECK practice_version_type,
 ADD CONSTRAINT practice_version_type CHECK((reading_type IN ('DETAILED','CLOZE','MATCH') AND audio_asset_id IS NULL) OR (reading_type='LISTENING' AND audio_asset_id IS NOT NULL AND listening_json IS NOT NULL) OR (reading_type IN ('WRITING','TRANSLATION') AND audio_asset_id IS NULL AND listening_json IS NULL)),
 ADD CONSTRAINT practice_version_quiz_kind CHECK((reading_type IN ('WRITING','TRANSLATION') AND quiz_id IS NULL AND quiz_version_id IS NULL AND subjective_json IS NOT NULL) OR (reading_type NOT IN ('WRITING','TRANSLATION') AND quiz_id IS NOT NULL AND quiz_version_id IS NOT NULL AND subjective_json IS NULL));
CREATE TABLE practice_subjective_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,set_id BIGINT NOT NULL,set_version_id BIGINT NOT NULL,
 parent_attempt_id BIGINT NULL,answer_text MEDIUMTEXT NOT NULL,word_count INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',lock_version INT NOT NULL DEFAULT 0,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),completed_at TIMESTAMP(6) NULL,
 active_set_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='IN_PROGRESS' THEN set_id ELSE NULL END) STORED,
 UNIQUE KEY subjective_active(user_id,active_set_id),UNIQUE KEY subjective_owner(user_id,id),INDEX subjective_history(user_id,started_at,id),
 CONSTRAINT subjective_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT subjective_version FOREIGN KEY(set_id,set_version_id) REFERENCES practice_set_version(set_id,id),
 CONSTRAINT subjective_parent FOREIGN KEY(user_id,parent_attempt_id) REFERENCES practice_subjective_attempt(user_id,id),
 CONSTRAINT subjective_status CHECK((status='IN_PROGRESS' AND completed_at IS NULL) OR (status='COMPLETED' AND completed_at IS NOT NULL AND word_count>0)),
 CONSTRAINT subjective_length CHECK(CHAR_LENGTH(answer_text)<=20000 AND word_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE practice_subjective_request (
 user_id BIGINT NOT NULL,request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation VARCHAR(16) NOT NULL,request_hash CHAR(64) CHARACTER SET ascii NOT NULL,attempt_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,request_id),
 CONSTRAINT subjective_request_owner FOREIGN KEY(user_id,attempt_id) REFERENCES practice_subjective_attempt(user_id,id),
 CONSTRAINT subjective_operation CHECK(operation IN ('START','SUBMIT','REVISE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
