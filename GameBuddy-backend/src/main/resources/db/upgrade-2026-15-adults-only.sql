-- GameBuddy becomes an 18+ service, and records that its account holders agreed to terms.
--
-- Run after upgrade-2026-14-username-and-device-token.sql. Idempotent.
--
-- birth_date is what eligibility is now decided from. The age column stays, because the
-- recommendation feed filters on it in native SQL and the model reads it as a feature,
-- but it is demoted to a cache of this column, refreshed nightly by BirthdayJob. No
-- backfill: an age cannot be turned back into a date of birth, and inventing 1 January
-- would put a real date in the database that nobody supplied.
--
-- terms_accepted_at / terms_version record an act, not a flag. The question asked later is
-- never "did they agree" but "what did they agree to, and when" — and a boolean cannot
-- answer it once the wording has changed. Nullable for accounts that predate the
-- requirement; they are asked on next sign-in rather than assumed to have agreed.
--
-- Nothing here removes accounts under 18. There are none in production because there is no
-- production yet, and a migration that deletes people is not something to write on
-- autopilot — if this ever runs against a database that has them, the accounts need a
-- notice and an export, not a DELETE.

BEGIN;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS birth_date date;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS terms_accepted_at timestamp(6) with time zone;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS terms_version character varying(32);

-- Finds the rows a human has to decide about before this deployment can call itself 18+.
-- Reported rather than acted on, deliberately.
DO $$
DECLARE
    minors integer;
BEGIN
    SELECT count(*) INTO minors FROM gamebuddy.gamer WHERE age IS NOT NULL AND age < 18;
    IF minors > 0 THEN
        RAISE WARNING 'There are % account(s) recorded as under 18. They are untouched by this migration and need a decision.', minors;
    END IF;
END $$;

COMMIT;
