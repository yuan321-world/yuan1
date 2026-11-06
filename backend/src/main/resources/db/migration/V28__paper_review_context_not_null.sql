-- MySQL CHECK accepts UNKNOWN. Require the slot explicitly when a paper origin is present.
ALTER TABLE practice_attempt DROP CHECK review_paper_context,
 ADD CONSTRAINT review_paper_context CHECK((source_paper_attempt_id IS NULL AND source_paper_slot IS NULL) OR
 (source_paper_attempt_id IS NOT NULL AND source_paper_slot IS NOT NULL AND kind='MISTAKES'
  AND source_paper_slot IN ('LISTENING','CLOZE','MATCH','DETAILED_1','DETAILED_2')));
