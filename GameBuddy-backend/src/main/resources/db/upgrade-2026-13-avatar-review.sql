-- What the classifier said about an avatar, and when the avatar arrived.
--
-- Run after upgrade-2026-12-recommender-staleness.sql. Idempotent.
--
-- avatar_score separates the two reasons an upload sits at PENDING, which the status
-- alone cannot express: a score means the classifier judged it and was unsure, NULL
-- means the classifier never answered because it was unreachable. Those want opposite
-- treatments — the second is re-screened automatically, the first needs a person — and
-- before this the queue conflated them, so an outage was indistinguishable from a flood
-- of borderline photographs.
--
-- avatar_uploaded_at exists because last_modified_date is an @UpdateTimestamp: any write
-- to the gamer row moves it, so a gamer changing their username would reset how long
-- their avatar had been waiting. The auto-publish deadline needs a clock only the upload
-- winds.
--
-- Both nullable with no backfill. Existing rows genuinely have no recorded score and no
-- recorded upload time, and inventing one would be worse than admitting it: a NULL
-- avatar_uploaded_at is treated as "not yet due" by the job rather than as 1970.

BEGIN;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS avatar_score double precision;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS avatar_uploaded_at timestamp(6) with time zone;

COMMIT;
