ALTER TABLE identity_email_challenge
    ADD COLUMN delivery_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_SENT',
    ADD CONSTRAINT identity_email_delivery_state CHECK (delivery_state IN ('NOT_SENT','QUEUED','SMTP_ACCEPTED','FAILED'));
