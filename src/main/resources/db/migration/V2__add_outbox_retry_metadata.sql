ALTER TABLE outbox_event
    ADD COLUMN retry_count INTEGER;

UPDATE outbox_event
SET retry_count = 0
WHERE retry_count IS NULL;

ALTER TABLE outbox_event
    ALTER COLUMN retry_count SET DEFAULT 0;

ALTER TABLE outbox_event
    ALTER COLUMN retry_count SET NOT NULL;

ALTER TABLE outbox_event
    ADD COLUMN last_error TEXT;

ALTER TABLE outbox_event
    ADD COLUMN last_attempt_at TIMESTAMPTZ;
