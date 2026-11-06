ALTER TABLE practice_set
 DROP CHECK practice_set_exam,
 DROP INDEX practice_article_exam,
 ADD UNIQUE KEY practice_article_exam(article_id,exam_code),
 ADD CONSTRAINT practice_set_exam CHECK(exam_code='CET4' AND reading_type IN ('DETAILED','CLOZE'));

ALTER TABLE practice_set_version
 ADD COLUMN reading_type VARCHAR(24) NOT NULL DEFAULT 'DETAILED',
 ADD COLUMN word_bank_json JSON NULL,
 ADD CONSTRAINT practice_version_type CHECK(reading_type IN ('DETAILED','CLOZE'));
UPDATE practice_set_version v
 JOIN practice_set s ON s.id=v.set_id
 SET v.reading_type=s.reading_type;
ALTER TABLE practice_set_version ALTER reading_type DROP DEFAULT;

ALTER TABLE reading_question
 DROP CHECK question_choice,
 DROP CHECK question_type,
 ADD CONSTRAINT question_choice CHECK(correct_option IN ('A','B','C','D','E','F','G','H','I','J','K','L','M','N','O')),
 ADD CONSTRAINT question_type CHECK(question_type IN ('MAIN_IDEA','DETAIL','INFERENCE','VOCABULARY','ATTITUDE','CLOZE'));

ALTER TABLE practice_answer
 DROP CHECK practice_answer_draft,
 DROP CHECK practice_answer_choice,
 ADD CONSTRAINT practice_answer_draft CHECK(draft_choice IS NULL OR draft_choice IN ('A','B','C','D','E','F','G','H','I','J','K','L','M','N','O')),
 ADD CONSTRAINT practice_answer_choice CHECK(submitted_choice IS NULL OR submitted_choice IN ('A','B','C','D','E','F','G','H','I','J','K','L','M','N','O'));
