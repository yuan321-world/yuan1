-- Application and Flyway MySQL connections use UTC.
-- Keep already overdue words available and align future dates to 05:00 in Shanghai.
UPDATE vocabulary_learning_word
SET due_at = TIMESTAMP(DATE(due_at + INTERVAL 8 HOUR), '05:00:00') - INTERVAL 8 HOUR,
    revision = revision + 1
WHERE stage > 0 AND due_at > UTC_TIMESTAMP(6);
