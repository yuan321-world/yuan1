-- Preserve prior words, due dates, settings and saved session semantics.
ALTER TABLE vocabulary_learning_setting ADD COLUMN auto_next BOOLEAN NOT NULL DEFAULT TRUE, ADD COLUMN review_scope VARCHAR(16) NOT NULL DEFAULT 'ALL', ADD CONSTRAINT learning_review_scope CHECK(review_scope IN ('ALL','CURRENT'));
INSERT INTO vocabulary_learning_setting(user_id,new_goal,review_goal) SELECT id,20,50 FROM identity_user WHERE id NOT IN (SELECT user_id FROM vocabulary_learning_setting);
ALTER TABLE vocabulary_learning_word ADD COLUMN proficient BOOLEAN NOT NULL DEFAULT FALSE;
SET @stage_check = (SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='vocabulary_learning_word' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%stage%' LIMIT 1);
SET @drop_stage = CONCAT('ALTER TABLE vocabulary_learning_word DROP CHECK `',@stage_check,'`');
PREPARE learning_stage_stmt FROM @drop_stage;
EXECUTE learning_stage_stmt;
DEALLOCATE PREPARE learning_stage_stmt;
ALTER TABLE vocabulary_learning_word ADD CONSTRAINT learning_stage_v2 CHECK(stage BETWEEN 0 AND 7);
ALTER TABLE vocabulary_learning_session ADD COLUMN flow_version VARCHAR(8) NOT NULL DEFAULT 'v1', ADD COLUMN review_scope VARCHAR(16) NOT NULL DEFAULT 'ALL', ADD CONSTRAINT learning_flow_version CHECK(flow_version IN ('v1','v2'));
ALTER TABLE vocabulary_learning_item ADD COLUMN rule_version VARCHAR(32) NOT NULL DEFAULT 'fixed-v1-binary', ADD COLUMN attempt_id INT NOT NULL DEFAULT 0, ADD COLUMN hint_count INT NOT NULL DEFAULT 0, ADD COLUMN assisted BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN proficient BOOLEAN NOT NULL DEFAULT FALSE;
