ALTER TABLE user_word_learning_origin
DROP CHECK word_learning_origin_kind,
ADD CONSTRAINT word_learning_origin_kind CHECK (source_kind IN ('BOOK','LEARNING','DICTIONARY'));
