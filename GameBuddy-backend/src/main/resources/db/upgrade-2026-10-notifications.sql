-- Notifications: deep links, and knowing who has drifted away.
--
-- Run after upgrade-2026-9-profile-reports.sql. Idempotent.

BEGIN;

-- --------------------------------------------------------------------------
-- 1. A notification says what it is about
-- --------------------------------------------------------------------------
-- Without this every notification could only reopen the app wherever it happened to be,
-- which for "someone sent you a message" is the one place it must not go. The kind and
-- the target ride to the device as FCM data and the client turns them into a route.
--
-- kind is text, not an enum type: an outbox row can outlive a deploy that renames one,
-- and a row that cannot be read is a notification that never arrives.

ALTER TABLE gamebuddy.notification_outbox
    ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE gamebuddy.notification_outbox
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(64);

-- --------------------------------------------------------------------------
-- 2. Who has stopped coming back
-- --------------------------------------------------------------------------
-- Nothing tracked this. last_modified_date follows writes and the session table follows
-- logins, so somebody who opened the app daily for a month without changing anything was
-- indistinguishable from somebody who left after signing up.
--
-- Written at most once an hour per gamer — see LastActiveTracker — so this is not a
-- per-request update however it looks.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMPTZ;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS last_nudged_at TIMESTAMPTZ;

-- Nudges sent since they were last active. Reset to zero when they return, so the cap is
-- on this absence rather than on their lifetime.
ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS nudge_count INTEGER NOT NULL DEFAULT 0;

-- Covers the "come back" reminders only. Everything else the app sends is a response to
-- something another person did, and switching those off is the operating system's job.
ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Existing accounts have never been seen by the new tracking. Treat them as active now
-- rather than as dormant since the beginning of time: without this, the first run of the
-- scheduler would decide that every account ever created is three days idle and nudge the
-- entire user base at once.
UPDATE gamebuddy.gamer SET last_active_at = now() WHERE last_active_at IS NULL;

-- The scheduler's lookup: the leading column is what the query filters on first.
CREATE INDEX IF NOT EXISTS idx_gamer_dormant
    ON gamebuddy.gamer (last_active_at)
    WHERE deleted_at IS NULL AND reminders_enabled;

COMMIT;
