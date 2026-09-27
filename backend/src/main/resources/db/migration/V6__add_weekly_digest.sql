-- Weekly digest email: on by default, and the time of the last one sent (also the claim that prevents duplicates).
ALTER TABLE dp_user
    ADD COLUMN weekly_digest_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN weekly_digest_sent_at DATETIME(6) NULL;
