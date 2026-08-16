-- What each game is played on.
--
-- Run after upgrade-2026-28-retire-community.sql. Idempotent.
--
-- upgrade-2026-24 gave gamers a set of platforms and nothing to compare it against. The
-- catalogue was ranked by IGDB's popularity signals, whose strongest two are Steam, so it
-- came out PC-shaped: a Nintendo Switch owner was asked to choose the games they play from
-- a list that did not contain Donkey Kong Bananza. Widening the catalogue fixes what is in
-- it; this fixes the picker's inability to tell which half is for you.
--
-- A join table rather than a column on `games`, for the same reason gamer_platform is one:
-- a game is a set. Almost everything worth listing is on three platforms or five, and a
-- single-valued column would force a choice between filing GTA V under PC and filing it
-- under PlayStation — either of which is wrong for most of the people who play it.

CREATE TABLE IF NOT EXISTS gamebuddy.game_platform (
    game_id  varchar(255) NOT NULL,
    -- The Platform enum name, not its label, and not IGDB's platform id. IGDB distinguishes
    -- PS4 from PS5 and Switch from Switch 2; the app deliberately does not, because nobody
    -- picking games thinks of those as different catalogues. The mapping from IGDB's ids to
    -- these five names lives in add_platform_games.py and is applied before insert.
    platform varchar(16)  NOT NULL,
    -- One row per game per platform. Without the key, re-running the tagging script leaves
    -- duplicates and every "on your platforms" group silently repeats itself.
    PRIMARY KEY (game_id, platform),
    CONSTRAINT fk_game_platform_game
        FOREIGN KEY (game_id) REFERENCES gamebuddy.games (game_id) ON DELETE CASCADE
);

COMMENT ON TABLE gamebuddy.game_platform IS
    'Which platforms each game is played on. A set, not a single value.';

-- The picker reads it game-first ("what is this game on"), which the primary key serves.
-- This index is for the other direction — "what is on Switch" — which the admin console and
-- any future per-platform query need, and which a game_id-led key cannot answer.
CREATE INDEX IF NOT EXISTS idx_game_platform_platform ON gamebuddy.game_platform (platform);

-- ---------------------------------------------------------------------------
-- Backfilled, unlike gamer_platform, and the difference is the point
-- ---------------------------------------------------------------------------
--
-- upgrade-2026-24 refused to guess a gamer's platforms, because silence there is a person
-- who has not said, and inventing PC for them would make the profile assert something they
-- never told us.
--
-- A game is not a person and does not have a preference to misrepresent. What a game runs
-- on is a fact IGDB already holds, so every row is tagged from IGDB rather than left empty:
-- see add_platform_games.py for new games and backfill_game_platforms.py for the catalogue
-- that predates this table. An untagged game here is a data gap, not an answer.
--
-- The picker is built for that gap anyway. It GROUPS rather than filters — "on your
-- platforms" first, everything else below, still searchable — so a game we failed to tag
-- sorts lower and never disappears. That follows the law FeedFilters already states for
-- people: a missing answer must not be read as the wrong one, and hiding is the one
-- mistake that cannot be recovered from by scrolling.
