-- Missions become a campaign, and badges get a hard tier.
--
-- Run after upgrade-2026-38-promo-codes.sql. Idempotent.
--
-- Two changes that share a migration because they share a table: the badge for finishing
-- the mission campaign reads the mission cursor, and the four hardest badges pay out in
-- cosmetics rather than coins.
--
-- --------------------------------------------------------------------------
-- 1. Missions: a table, where there were five columns
-- --------------------------------------------------------------------------
--
-- upgrade-2026-20 put mission state on the gamer row: one baseline per metric
-- (quest_base_messages, quest_base_matches, quest_base_lobbies) plus a bitmask of which of
-- the three had been paid. Its own header called that out as a trade —
--
--     "One column per quest rather than a snapshot table. There are three quests; a table
--      keyed by (user_id, metric) would be three rows per gamer per week... Changing the
--      quest set is then a migration, which is the honest cost of changing what the game
--      asks people to do."
--
-- This is that cost, paid. Three missions became twenty-four, dealt three at a time, and
-- the old shape could not describe a fourth: a new mission needed a new baseline column,
-- and because the mask was keyed on the enum's ordinal(), reordering the catalogue silently
-- reassigned everybody's claimed bits. Per-assignment state moves to gamer_mission, where a
-- row can say which mission it belongs to.
--
-- The five old columns are DROPped rather than left to rot. They are unreadable by the new
-- code and a half-migrated gamer row is worse than a reset one; the cost is that a claim
-- made between Monday and this deploy is forgotten, which is at most one set of coins.
--
-- --------------------------------------------------------------------------
-- 2. Cosmetics that cannot be bought
-- --------------------------------------------------------------------------
--
-- unlocked_by_badge points a cosmetic at the badge that grants it. That direction, and not
-- the other, on purpose: a slug on the badge enum *and* a row here would be two places
-- saying which frame goes with which badge, and two places for one fact is how a badge ends
-- up granting the wrong thing. The Badge enum only knows that it pays no coins.
--
-- These rows are priced 0 and are filtered out of the shop by the same code path that
-- already hides membership_only items, so "price 0" never reads as "free" anywhere a gamer
-- can see it.
--
-- The art has to be uploaded before this runs, or four rows point at nothing:
--     python cosmetics/generate.py && python badges/generate.py
--     tools/upload-assets.sh frames badges

BEGIN;

-- --------------------------------------------------------------------------
-- Missions
-- --------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_mission (
    user_id      VARCHAR(255) NOT NULL REFERENCES gamebuddy.gamer(user_id) ON DELETE CASCADE,
    set_index    INTEGER      NOT NULL,
    slot         SMALLINT     NOT NULL,
    mission_code VARCHAR(48)  NOT NULL,
    -- What the mission's metric read when this row was written. Progress is the metric now
    -- minus this, clamped at zero, so nothing has to be written while the gamer plays.
    baseline     INTEGER      NOT NULL,
    -- Frozen at deal time. The band rates are config so the curve can be retuned without a
    -- release, and a retune must not change the price of a mission already on screen.
    reward       INTEGER      NOT NULL,
    claimed_at   TIMESTAMPTZ,
    assigned_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, set_index, slot),
    CONSTRAINT gamer_mission_slot_check    CHECK (slot BETWEEN 0 AND 2),
    CONSTRAINT gamer_mission_set_check     CHECK (set_index >= 1),
    CONSTRAINT gamer_mission_reward_check  CHECK (reward >= 0),
    CONSTRAINT gamer_mission_baseline_check CHECK (baseline >= 0)
);

COMMENT ON TABLE gamebuddy.gamer_mission IS
    'One mission dealt to one gamer, in one slot of one set. Rows are kept after the set '
    'ends: the dealer reads them to avoid repeats, and they are the only record of what was '
    'asked and what it paid.';

-- No index beyond the primary key. Every read is "the rows for this user", and (user_id,
-- set_index, slot) is a prefix match on the PK for exactly that.

-- The timestamp trigger every table has carried since upgrade-2026-17.
DROP TRIGGER IF EXISTS set_updated_at ON gamebuddy.gamer_mission;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON gamebuddy.gamer_mission
    FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();

-- The cursor. Denormalised max(set_index) — the earn screen needs it on every load, and the
-- alternative is an aggregate just to find out whether there is anything to deal.
ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS mission_set_index INTEGER NOT NULL DEFAULT 0;

-- Lifetime daily claims, which daily_streak cannot answer because it forgets everything the
-- moment somebody misses a day. Backfilled from the current streak: it understates for
-- anybody who has ever broken one, and that is the honest direction to be wrong in — it
-- costs a returning gamer nothing they had already been paid for.
ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS daily_claims_total INTEGER NOT NULL DEFAULT 0;

UPDATE gamebuddy.gamer
   SET daily_claims_total = daily_streak
 WHERE daily_claims_total = 0
   AND daily_streak > 0;

ALTER TABLE gamebuddy.gamer
    DROP COLUMN IF EXISTS quest_week_started_at,
    DROP COLUMN IF EXISTS quest_base_messages,
    DROP COLUMN IF EXISTS quest_base_matches,
    DROP COLUMN IF EXISTS quest_base_lobbies,
    DROP COLUMN IF EXISTS quest_claimed_mask;

-- --------------------------------------------------------------------------
-- Cosmetics that are earned rather than sold
-- --------------------------------------------------------------------------

ALTER TABLE gamebuddy.cosmetic
    ADD COLUMN IF NOT EXISTS unlocked_by_badge VARCHAR(48);

COMMENT ON COLUMN gamebuddy.cosmetic.unlocked_by_badge IS
    'Badge.code of the badge that grants this, or NULL for anything on sale. A row with '
    'this set is never purchasable at any price.';

-- One cosmetic per badge. Two frames claiming the same badge would make "what does this
-- badge give me" a question with two answers, and the grant would pick whichever the
-- planner returned first.
CREATE UNIQUE INDEX IF NOT EXISTS idx_cosmetic_unlocked_by_badge
    ON gamebuddy.cosmetic (unlocked_by_badge)
    WHERE unlocked_by_badge IS NOT NULL;

-- The four trophies. The ids are uuid5(NAMESPACE_DNS, asset_key), computed offline and
-- written out as literals the way every other cosmetic row in this directory is — Postgres
-- has no uuid_generate_v5 here, and a literal is what makes re-running this a no-op rather
-- than a second copy under a fresh id.
--
-- Animated frames, because the four hardest awards in the game should not be still pictures.
-- sort_order sits past the end of the shop's range: they are never shown there, but the
-- Inventory orders by it.
INSERT INTO gamebuddy.cosmetic (id, kind, name, asset_key, animated, price, sort_order, membership_only, unlocked_by_badge, created_date, updated_at) VALUES
    ('f81ea5d7-bc93-5685-a7f1-7b54b3d6abc4', 'FRAME', 'Magnetic',    'frames/frame-magnetic.webp',    true, 0, 900, false, 'magnetic',    now(), now()),
    ('dc75388c-589d-50b5-a5e1-d8f3473e3bb9', 'FRAME', 'Unbroken',    'frames/frame-unbroken.webp',    true, 0, 901, false, 'unbroken',    now(), now()),
    ('69415c82-1b44-59d5-84b5-c2a053f96be5', 'FRAME', 'Collector',   'frames/frame-collector.webp',   true, 0, 902, false, 'collector',   now(), now()),
    ('f4d38c69-a818-52ef-afcc-3eebae60bedd', 'FRAME', 'Trailblazer', 'frames/frame-trailblazer.webp', true, 0, 903, false, 'trailblazer', now(), now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
