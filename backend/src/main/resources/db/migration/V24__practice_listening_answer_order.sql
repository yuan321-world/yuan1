-- V23 has already been applied to the isolated test database; preserve its checksum.
-- Reading services still enforce their unchanged per-group limits.
ALTER TABLE practice_answer DROP CHECK practice_answer_ordinal,
 ADD CONSTRAINT practice_answer_ordinal CHECK(ordinal BETWEEN 0 AND 49);
