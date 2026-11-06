CREATE TABLE practice_audio_asset (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_by BIGINT NOT NULL, storage_key VARCHAR(64) NOT NULL UNIQUE,
 original_name VARCHAR(200) NOT NULL, byte_size BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT practice_audio_creator FOREIGN KEY(created_by) REFERENCES identity_user(id),
 CONSTRAINT practice_audio_size CHECK(byte_size BETWEEN 1 AND 83886080)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE practice_set
 ADD COLUMN section VARCHAR(16) NOT NULL DEFAULT 'READING',
 DROP CHECK practice_set_exam,
 ADD CONSTRAINT practice_set_exam CHECK(exam_code='CET4' AND ((section='READING' AND reading_type IN ('DETAILED','CLOZE','MATCH')) OR (section='LISTENING' AND reading_type='LISTENING')));
ALTER TABLE practice_set_version
 ADD COLUMN listening_json JSON NULL,
 ADD COLUMN audio_asset_id BIGINT NULL,
 ADD CONSTRAINT practice_audio_version FOREIGN KEY(audio_asset_id) REFERENCES practice_audio_asset(id),
 DROP CHECK practice_version_type,
 ADD CONSTRAINT practice_version_type CHECK((reading_type IN ('DETAILED','CLOZE','MATCH') AND audio_asset_id IS NULL) OR (reading_type='LISTENING' AND audio_asset_id IS NOT NULL AND listening_json IS NOT NULL));
ALTER TABLE reading_question DROP CHECK question_type,
 ADD CONSTRAINT question_type CHECK(question_type IN ('MAIN_IDEA','DETAIL','INFERENCE','VOCABULARY','ATTITUDE','CLOZE','MATCH','LISTENING'));
ALTER TABLE practice_attempt
 ADD COLUMN playback_json JSON NULL,
 DROP CHECK practice_attempt_total,
 ADD CONSTRAINT practice_attempt_total CHECK(total_count BETWEEN 1 AND 50);
