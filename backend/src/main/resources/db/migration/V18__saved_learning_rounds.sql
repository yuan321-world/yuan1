-- Existing app allowed only one unfinished round and locked source selection.
ALTER TABLE vocabulary_learning_session
 ADD COLUMN source_key VARCHAR(64) NOT NULL DEFAULT 'PERSONAL',
 ADD COLUMN source_title VARCHAR(200) NOT NULL DEFAULT '我的阅读生词';
UPDATE vocabulary_learning_session s
LEFT JOIN vocabulary_learning_source c ON c.user_id=s.user_id AND c.kind='BOOK'
LEFT JOIN vocabulary_wordbook b ON b.id=c.book_id
SET s.source_key=CASE WHEN s.kind='REVIEW' THEN 'GLOBAL' WHEN b.id IS NOT NULL THEN CONCAT('BOOK:',b.id) ELSE 'PERSONAL' END,
 s.source_title=CASE WHEN s.kind='REVIEW' THEN '全部来源' WHEN b.id IS NOT NULL THEN b.title ELSE '我的阅读生词' END
WHERE s.status<>'COMPLETED';
ALTER TABLE vocabulary_learning_session
 ADD COLUMN unfinished_scope VARCHAR(80) GENERATED ALWAYS AS (CASE WHEN status<>'COMPLETED' THEN CONCAT(kind,':',source_key) ELSE NULL END) STORED,
 ADD COLUMN active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END) STORED,
 ADD UNIQUE KEY learning_saved_scope(user_id,unfinished_scope),
 ADD UNIQUE KEY learning_one_active(user_id,active_slot);
