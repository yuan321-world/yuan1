ALTER TABLE vocabulary_learning_setting
ADD COLUMN batch_size INT NOT NULL DEFAULT 20,
ADD CONSTRAINT learning_batch_size CHECK (batch_size BETWEEN 1 AND 50);
