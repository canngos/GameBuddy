-- Ways to earn coins.
--
-- Run after upgrade-2026-19-boost-and-rewind.sql. Idempotent.
--
-- The economy did not work. Lifetime income was 975 coins from thirteen one-time badge
-- rewards and nothing else, ever; the paid catalogue costs 9,400. Somebody who earned every
-- badge in the game could afford a tenth of the shelf and was then finished — which makes
-- the shelf furniture rather than a goal, and makes the coin packs the only way anything is
-- ever bought. A currency you cannot earn is not a currency, it is a price list.
--
-- Three faucets here: a daily streak, three weekly quests, and a monthly Gold stipend.
-- (The fourth, rewarded video, is blocked on choosing an ad network.)
--
-- All state lives on `gamer` rather than in a wallet table, matching where the swipe quota
-- and the boost expiry already live. These are per-gamer counters read on one screen and
-- written by one action; a separate table would add a join to every read of them and a row
-- to create for every account, in exchange for tidiness nobody benefits from.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS daily_claimed_at   timestamptz,
    ADD COLUMN IF NOT EXISTS daily_streak       integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS stipend_claimed_at timestamptz;

COMMENT ON COLUMN gamebuddy.gamer.daily_claimed_at IS
    'When the daily coins were last taken. Drives both "again yet?" and the streak.';
COMMENT ON COLUMN gamebuddy.gamer.daily_streak IS
    'Consecutive days claimed. Resets to 1 after a missed day, never to 0 by a claim.';

-- ---------------------------------------------------------------------------
-- Weekly quests
-- ---------------------------------------------------------------------------
--
-- Quests measure a week's activity, and the badge metrics they reuse are lifetime totals —
-- "messages sent", not "messages sent since Monday". Rather than build a second counting
-- system alongside BadgeMetric, the week's starting totals are recorded here and progress
-- is the difference. That keeps one source of truth for what a gamer has done, and means a
-- quest over a metric that already exists costs nothing new to measure.
--
-- One column per quest rather than a snapshot table. There are three quests; a table keyed
-- by (user_id, metric) would be three rows per gamer per week, a join on the one screen
-- that reads them, and a cleanup job for last week's. Changing the quest set is then a
-- migration, which is the honest cost of changing what the game asks people to do.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS quest_week_started_at timestamptz,
    ADD COLUMN IF NOT EXISTS quest_base_messages   integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quest_base_matches    integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quest_base_posts      integer NOT NULL DEFAULT 0,
    -- Bitmask of quests already paid out this week. A mask rather than three booleans
    -- because it is read and written as one value and never queried by individual quest.
    ADD COLUMN IF NOT EXISTS quest_claimed_mask    integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN gamebuddy.gamer.quest_week_started_at IS
    'Start of the week the baselines below were taken at. Null means never started one.';
COMMENT ON COLUMN gamebuddy.gamer.quest_claimed_mask IS
    'Which of this week''s quests have been paid. Cleared when the week rolls over.';
