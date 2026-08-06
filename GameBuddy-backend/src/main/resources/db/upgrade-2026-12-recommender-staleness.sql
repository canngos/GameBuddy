-- Marks a gamer whose profile the trained model no longer describes.
--
-- Run after upgrade-2026-11-notification-preferences.sql. Idempotent.
--
-- The recommender is trained offline, and /predict ranks a gamer it knows from features
-- pickled at training time. Changing your games or keywords therefore changed nothing
-- about who you were shown: the feed only fell back to the live-profile path when
-- /predict came back empty, which for a known gamer it never does. This column is set
-- whenever those two collections change, and the feed reads it to decide which path to
-- take. See Gamer#recommenderProfileChangedAt and RecommenderStalenessListener.
--
-- Nullable, with no default and no backfill. NULL means "the artefact is a fair
-- description of this gamer", which is the correct answer for every existing row: the
-- model was last trained on the data as it stands, so nobody is stale yet. Backfilling
-- now() instead would send the entire user base down the cold-start path at once for no
-- reason.
--
-- No index. It is read one row at a time, by primary key, on the feed path — the query
-- that wants it has already found the gamer. The retrain job that will eventually clear
-- these (see task #24) sweeps the table anyway.

BEGIN;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS recommender_profile_changed_at timestamp(6) with time zone;

COMMIT;
