-- Notification durability, and declines that expire.
--
-- Run after upgrade-2026-4-monolith.sql. Idempotent.

BEGIN;

-- --------------------------------------------------------------------------
-- 1. Notification outbox
-- --------------------------------------------------------------------------
-- Notifications used to be sent on an after-commit thread. That works until the process
-- dies between the commit and the send — a pod eviction, a rolling deploy, an OOM — at
-- which point the notification is gone with nothing to say it was ever owed.
--
-- The row is written inside the transaction that caused it, so the promise and the state
-- change commit together, and a poller delivers afterwards. The trade is explicit: the
-- same notification can be sent twice if the process dies between sending and marking it
-- sent. At-least-once is the achievable guarantee, and a duplicate push is a much better
-- failure than a missing one.

CREATE TABLE IF NOT EXISTS gamebuddy.notification_outbox (
    id              UUID          PRIMARY KEY,
    fcm_token       VARCHAR(512)  NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    -- NULL until delivered. This is the poller's filter, which is why there is no status
    -- column: one nullable timestamp answers "sent?" and "when?" at once.
    sent_at         TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    last_error      VARCHAR(500)
);

-- The poller's only query: unsent and due, oldest first. Partial, because the table is
-- mostly delivered rows and indexing those would be indexing the answer nobody asks for.
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON gamebuddy.notification_outbox (next_attempt_at, created_at)
    WHERE sent_at IS NULL;

-- --------------------------------------------------------------------------
-- 2. Declines expire
-- --------------------------------------------------------------------------
-- declined_matches was a bare join table, so a pass was permanent. The recommendation
-- ranking is a deterministic function of profiles, so anyone ever declined was excluded
-- forever and the candidate pool could only shrink — an active swiper ends up with an
-- empty feed and no way back.
--
-- With a timestamp, a pass expires after thirty days. Someone declined months ago has
-- probably changed their games, keywords or photo since; the original decision was about a
-- profile that no longer exists.

ALTER TABLE gamebuddy.declined_matches
    ADD COLUMN IF NOT EXISTS declined_at TIMESTAMPTZ;

-- Existing rows have no recorded time. Dated now rather than in the past, so nobody is
-- suddenly re-shown to a gamer who passed on them yesterday; they simply start their
-- thirty days from the migration.
UPDATE gamebuddy.declined_matches SET declined_at = NOW() WHERE declined_at IS NULL;

ALTER TABLE gamebuddy.declined_matches ALTER COLUMN declined_at SET NOT NULL;

-- Duplicates are possible if the old code ever inserted twice; collapse them before the
-- key goes on.
DELETE FROM gamebuddy.declined_matches a
    USING gamebuddy.declined_matches b
    WHERE a.ctid < b.ctid
      AND a.user_id = b.user_id
      AND a.declined_id = b.declined_id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'gamebuddy' AND t.relname = 'declined_matches' AND c.contype = 'p'
    ) THEN
        ALTER TABLE gamebuddy.declined_matches
            ADD CONSTRAINT pk_declined_matches PRIMARY KEY (user_id, declined_id);
    END IF;
END $$;

-- Serves the exclusion lookup: this gamer's declines newer than the window.
CREATE INDEX IF NOT EXISTS idx_declined_user_time
    ON gamebuddy.declined_matches (user_id, declined_at);

COMMIT;
