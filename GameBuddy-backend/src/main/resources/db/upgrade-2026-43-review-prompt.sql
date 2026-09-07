-- When the Play in-app review card was last asked for.
--
-- Run after upgrade-2026-42-social-sign-in.sql. Idempotent.
--
-- Google Play decides for itself whether to actually show a review card, and it never tells
-- the app which way it went. So the app cannot report "it was shown" honestly, and this
-- column records the *ask* rather than the showing: the moment the server said yes. That is
-- the only number either side can be sure of, and it is the one that bounds how often
-- somebody can be interrupted.
--
-- A timestamp rather than the boolean-shaped `upgrade_prompt_shown_at` beside it, because
-- unlike the day-3 Gold pitch this one legitimately repeats. Play's own quota is a rolling
-- window, and a person who declined a year ago is a fair question again; ninety days is the
-- window this application enforces on top.

BEGIN;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS review_prompt_shown_at timestamp(6) with time zone;

COMMENT ON COLUMN gamebuddy.gamer.review_prompt_shown_at IS
    'When the Play review card was last requested. Null means never. Not proof it appeared -- Play never says.';

COMMIT;
