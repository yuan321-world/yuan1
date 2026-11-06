ALTER TABLE reading_selection_translation
 ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'TRANSLATION',
 ADD COLUMN analysis_json JSON NULL,
 ADD CONSTRAINT selection_translation_mode CHECK(mode IN ('TRANSLATION','GRAMMAR'));
