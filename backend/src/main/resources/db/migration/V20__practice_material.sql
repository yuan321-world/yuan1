ALTER TABLE reading_article
 ADD COLUMN content_usage VARCHAR(16) NOT NULL DEFAULT 'READING',
 ADD CONSTRAINT reading_article_usage CHECK(content_usage IN ('READING','PRACTICE'));

ALTER TABLE practice_request DROP CHECK practice_request_result;
ALTER TABLE practice_request ADD CONSTRAINT practice_request_result CHECK(
 (operation IN ('START','SUBMIT','RETRY') AND attempt_id IS NOT NULL AND set_id IS NULL)
 OR (operation IN ('IMPORT','MATERIAL') AND attempt_id IS NULL AND set_id IS NOT NULL)
);

-- Existing articles keep their original use; linking a reading article never moves it.
