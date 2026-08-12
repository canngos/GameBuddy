-- Remembering that the day-3 upgrade prompt has been shown.
--
-- Run after upgrade-2026-22-funnel-instrumentation.sql. Idempotent.
--
-- The prompt is meant to appear exactly once, on the third day, to somebody who has
-- already had a match. "Exactly once" is the only hard part: every other input is
-- derivable from rows that already exist (created_date for the age of the account,
-- approved_matches for whether it has produced anything), but whether the gamer has
-- already been asked is a fact about the past that nothing currently records.
--
-- Server-side rather than in the client's own storage, and this is the reason: a flag
-- kept on the device is cleared by a reinstall, by a new phone, and by clearing app data
-- — so the one-time prompt becomes an every-few-months prompt for exactly the people who
-- have already declined it once. Nagging is the failure mode that costs the most here,
-- because the audience for a second showing is entirely people who said no to the first.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS upgrade_prompt_shown_at timestamptz;

COMMENT ON COLUMN gamebuddy.gamer.upgrade_prompt_shown_at IS
    'When the one-time day-3 Gold prompt was shown. Null means never; set once, never cleared.';

-- Deliberately no backfill.
--
-- Null is the correct value for every existing account: none of them has been shown the
-- prompt, because it did not exist. Accounts already older than three days with a match
-- will therefore see it on their next visit, which is the intended behaviour rather than
-- an accident — they are exactly the people it was written for.
