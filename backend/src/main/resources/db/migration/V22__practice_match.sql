ALTER TABLE practice_set
 DROP CHECK practice_set_exam,
 ADD CONSTRAINT practice_set_exam CHECK(exam_code='CET4' AND reading_type IN ('DETAILED','CLOZE','MATCH'));

ALTER TABLE practice_set_version
 DROP CHECK practice_version_type,
 ADD CONSTRAINT practice_version_type CHECK(reading_type IN ('DETAILED','CLOZE','MATCH'));

ALTER TABLE reading_question
 DROP CHECK question_choice,
 DROP CHECK question_type,
 ADD CONSTRAINT question_choice CHECK(correct_option IN (
  'A','B','C','D','E','F','G','H','I','J','K','L','M',
  'N','O','P','Q','R','S','T','U','V','W','X','Y','Z')),
 ADD CONSTRAINT question_type CHECK(question_type IN (
  'MAIN_IDEA','DETAIL','INFERENCE','VOCABULARY','ATTITUDE','CLOZE','MATCH'));

-- The attempt service validates each choice against its frozen reading type and MATCH passage letters.
ALTER TABLE practice_answer
 ADD COLUMN flagged BOOLEAN NOT NULL DEFAULT FALSE,
 DROP CHECK practice_answer_draft,
 DROP CHECK practice_answer_choice,
 ADD CONSTRAINT practice_answer_draft CHECK(draft_choice IS NULL OR draft_choice IN (
  'A','B','C','D','E','F','G','H','I','J','K','L','M',
  'N','O','P','Q','R','S','T','U','V','W','X','Y','Z')),
 ADD CONSTRAINT practice_answer_choice CHECK(submitted_choice IS NULL OR submitted_choice IN (
  'A','B','C','D','E','F','G','H','I','J','K','L','M',
  'N','O','P','Q','R','S','T','U','V','W','X','Y','Z'));
