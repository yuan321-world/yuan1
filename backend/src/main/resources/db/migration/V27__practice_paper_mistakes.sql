ALTER TABLE practice_attempt
 ADD COLUMN source_paper_attempt_id BIGINT NULL,
 ADD COLUMN source_paper_slot VARCHAR(24) NULL,
 ADD CONSTRAINT review_paper_owner FOREIGN KEY(user_id,source_paper_attempt_id) REFERENCES practice_paper_attempt(user_id,id),
 ADD CONSTRAINT review_single_or_paper CHECK(source_attempt_id IS NULL OR source_paper_attempt_id IS NULL),
 ADD CONSTRAINT review_paper_context CHECK((source_paper_attempt_id IS NULL AND source_paper_slot IS NULL) OR
  (source_paper_attempt_id IS NOT NULL AND kind='MISTAKES' AND source_paper_slot IN ('LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2')));

CREATE TABLE practice_paper_mistake (
 user_id BIGINT NOT NULL, set_id BIGINT NOT NULL, set_version_id BIGINT NOT NULL, quiz_version_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 source_paper_attempt_id BIGINT NOT NULL, slot VARCHAR(24) NOT NULL, original_number INT NOT NULL,
 latest_paper_attempt_id BIGINT NULL, latest_review_attempt_id BIGINT NULL,
 status VARCHAR(16) NOT NULL, wrong_count INT NOT NULL, submitted_choice CHAR(1) NULL,
 first_wrong_at TIMESTAMP(6) NOT NULL, last_completed_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(user_id,set_version_id,question_id), INDEX paper_mistake_state(user_id,status,last_completed_at,question_id),
 CONSTRAINT paper_mistake_source FOREIGN KEY(user_id,source_paper_attempt_id) REFERENCES practice_paper_attempt(user_id,id),
 CONSTRAINT paper_mistake_latest_paper FOREIGN KEY(user_id,latest_paper_attempt_id) REFERENCES practice_paper_attempt(user_id,id),
 CONSTRAINT paper_mistake_latest_review FOREIGN KEY(user_id,latest_review_attempt_id) REFERENCES practice_attempt(user_id,id),
 CONSTRAINT paper_mistake_set FOREIGN KEY(set_id,set_version_id) REFERENCES practice_set_version(set_id,id),
 CONSTRAINT paper_mistake_quiz FOREIGN KEY(set_version_id,quiz_version_id) REFERENCES practice_set_version(id,quiz_version_id),
 CONSTRAINT paper_mistake_question FOREIGN KEY(quiz_version_id,question_id) REFERENCES reading_question(quiz_version_id,id),
 CONSTRAINT paper_mistake_state CHECK(status IN ('PENDING','RESOLVED') AND wrong_count>0),
 CONSTRAINT paper_mistake_slot CHECK(slot IN ('LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2') AND original_number BETWEEN 1 AND 55),
 CONSTRAINT paper_mistake_latest CHECK((latest_paper_attempt_id IS NOT NULL AND latest_review_attempt_id IS NULL) OR
  (latest_paper_attempt_id IS NULL AND latest_review_attempt_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BACKFILL-BEGIN: immutable completed source facts only; replay inserts missing identities, never overwrites a newer review.
INSERT INTO practice_paper_mistake
 (user_id,set_id,set_version_id,quiz_version_id,question_id,source_paper_attempt_id,slot,original_number,
 latest_paper_attempt_id,latest_review_attempt_id,status,wrong_count,submitted_choice,first_wrong_at,last_completed_at)
WITH ordered_questions AS (
 SELECT id,quiz_version_id,ROW_NUMBER() OVER(PARTITION BY quiz_version_id ORDER BY sort_order,id)-1 ordinal FROM reading_question
), events AS (
 SELECT a.user_id,a.id attempt_id,a.completed_at,c.sort_order component_order,c.slot,v.set_id,v.id set_version_id,v.quiz_version_id,q.id question_id,
 CASE c.slot WHEN 'LISTENING' THEN 1 WHEN 'CLOZE' THEN 26 WHEN 'MATCH' THEN 36 WHEN 'DETAILED_1' THEN 46 ELSE 51 END+q.ordinal original_number,
 CASE j.correct WHEN 'true' THEN 1 ELSE 0 END is_correct,
 JSON_UNQUOTE(JSON_EXTRACT(r.response_json,CONCAT('$.choices."',q.id,'"'))) submitted_choice
 FROM practice_paper_attempt a JOIN practice_paper_response r ON r.attempt_id=a.id
 JOIN practice_paper_component c ON c.paper_version_id=a.paper_version_id AND c.slot=r.slot
 JOIN practice_set_version v ON v.id=c.set_version_id
 JOIN JSON_TABLE(r.result_json,'$.answers[*]' COLUMNS(question_id BIGINT PATH '$.questionId',correct VARCHAR(5) PATH '$.correct')) j
 JOIN ordered_questions q ON q.id=j.question_id AND q.quiz_version_id=v.quiz_version_id
 WHERE a.status='COMPLETED' AND c.slot IN ('LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2')
), wrongs AS (
 SELECT e.*,ROW_NUMBER() OVER(PARTITION BY user_id,set_version_id,question_id ORDER BY completed_at,attempt_id,component_order) first_wrong,
 COUNT(*) OVER(PARTITION BY user_id,set_version_id,question_id) wrong_count FROM events e WHERE is_correct=0
), latest AS (
 SELECT e.*,ROW_NUMBER() OVER(PARTITION BY user_id,set_version_id,question_id ORDER BY completed_at DESC,attempt_id DESC,component_order DESC) last_event FROM events e
)
SELECT f.user_id,f.set_id,f.set_version_id,f.quiz_version_id,f.question_id,f.attempt_id,f.slot,f.original_number,
 l.attempt_id,NULL,IF(l.is_correct=1,'RESOLVED','PENDING'),f.wrong_count,l.submitted_choice,f.completed_at,l.completed_at
FROM wrongs f JOIN latest l ON l.user_id=f.user_id AND l.set_version_id=f.set_version_id AND l.question_id=f.question_id
WHERE f.first_wrong=1 AND l.last_event=1
ON DUPLICATE KEY UPDATE wrong_count=practice_paper_mistake.wrong_count;
-- BACKFILL-END
