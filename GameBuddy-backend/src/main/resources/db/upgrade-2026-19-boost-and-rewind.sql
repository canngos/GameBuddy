-- Boost and Rewind.
--
-- Run after upgrade-2026-18-membership-cosmetics.sql. Idempotent.
--
-- Two a-la-carte features, both paid for in coins rather than with new store products.
-- That is deliberate: coins are already a purchasable currency with a working grant path,
-- so selling these for coins needs no new SKU in either console and no new webhook mapping.
-- Adding `gamebuddy.boost.single` later is a pricing decision, not a schema one.
--
-- ---------------------------------------------------------------------------
-- Rewind: the last decision, recorded on the gamer
-- ---------------------------------------------------------------------------
--
-- Three columns on `gamer` rather than a decisions table or a lookup across
-- `approved_matches` and `declined_matches`.
--
-- The join-table route looked cheaper and is wrong in a way that matters: "the most recent
-- decision" would mean comparing a `created_at` in one table against a `declined_at` in
-- another, and a decision that *replaced* another (accept then decline of the same person)
-- leaves rows in both. Recording the last decision explicitly makes "what did I just do"
-- a single read that cannot disagree with itself, and clearing it after a rewind is what
-- makes rewind un-repeatable without a second counter.
--
-- No history is kept on purpose. Rewind undoes one swipe — the one you just regretted —
-- and a log of every decision a gamer ever made is a much larger privacy commitment than
-- this feature earns.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS last_decision_user_id varchar(255),
    ADD COLUMN IF NOT EXISTS last_decision_accept  boolean,
    ADD COLUMN IF NOT EXISTS last_decision_at      timestamptz;

COMMENT ON COLUMN gamebuddy.gamer.last_decision_user_id IS
    'Who the most recent swipe was about. NULL when there is nothing to rewind.';
COMMENT ON COLUMN gamebuddy.gamer.last_decision_accept IS
    'True if that swipe was a like. Decides which table a rewind has to undo.';

-- ---------------------------------------------------------------------------
-- Boost: thirty minutes at the front of the deck
-- ---------------------------------------------------------------------------
--
-- An expiry, not a flag. A boolean would need something to turn it off, and a scheduled
-- job that sweeps expired boosts is a moving part that can fail silently and leave someone
-- boosted forever. A timestamp compared against now() cannot be stale.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS boost_expires_at   timestamptz,
    ADD COLUMN IF NOT EXISTS last_free_boost_at timestamptz;

COMMENT ON COLUMN gamebuddy.gamer.boost_expires_at IS
    'While in the future, this gamer is pinned to the front of decks in their country.';
COMMENT ON COLUMN gamebuddy.gamer.last_free_boost_at IS
    'When the weekly Gold boost was last taken. NULL means never.';

-- Partial: only the handful of rows boosted right now are worth indexing, and the feed
-- asks exactly this question — "who in this country is boosted" — on every request.
CREATE INDEX IF NOT EXISTS idx_gamer_boost_active
    ON gamebuddy.gamer (country, boost_expires_at)
    WHERE boost_expires_at IS NOT NULL;
