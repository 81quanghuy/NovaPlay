-- Phase 4: Create outbox_events table for transactional outbox pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic       VARCHAR(255) NOT NULL,
    event_key   VARCHAR(255),
    payload     JSONB        NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    error_message VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events (created_at);

-- Cleanup job: delete SENT events older than 7 days (run as cron or pg_cron)
-- DELETE FROM outbox_events WHERE status = 'SENT' AND sent_at < now() - interval '7 days';
