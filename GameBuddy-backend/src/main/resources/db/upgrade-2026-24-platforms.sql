-- What each gamer plays on.
--
-- Run after upgrade-2026-23-upgrade-prompt.sql. Idempotent.
--
-- The monetisation strategy has listed "platform" as one of the four Gold filters since it
-- was written, and there has never been a platform field to filter on. This adds it.
--
-- A join table rather than a column on `gamer`, because a gamer holds a set. Most people
-- play on more than one thing, and a single-valued column would make "PC and Switch"
-- unrepresentable — which would make the filter actively wrong rather than merely
-- incomplete, hiding people from searches they belong in.

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_platform (
    user_id  varchar(255) NOT NULL,
    -- The Platform enum name, not its label. Stored as text rather than a Postgres enum
    -- type so adding a platform is a deploy and not an ALTER TYPE that has to be
    -- coordinated with the running application.
    platform varchar(16)  NOT NULL,
    -- One row per gamer per platform, and the primary key is what enforces it. Without it
    -- an edit that saves twice leaves duplicates, and every count is then wrong.
    PRIMARY KEY (user_id, platform),
    CONSTRAINT fk_gamer_platform_gamer
        FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer (user_id) ON DELETE CASCADE
);

COMMENT ON TABLE gamebuddy.gamer_platform IS
    'Which platforms each gamer plays on. A set, not a single value.';

-- The filter reads it the other way round — "who is on PlayStation" — and the primary key
-- above is no help for that, being led by user_id.
CREATE INDEX IF NOT EXISTS idx_gamer_platform_platform ON gamebuddy.gamer_platform (platform);

-- ---------------------------------------------------------------------------
-- No backfill, and no guessing
-- ---------------------------------------------------------------------------
--
-- Every existing account has an empty set, which reads as "not said" rather than "plays on
-- nothing". Inventing PC for everybody would be worse than leaving it blank: the profile
-- would assert something the account holder never told us, and the filter would then act
-- on it — showing PC players who are not, and hiding them from every other search.
--
-- The consequence is deliberate and is handled in the filter: an account with no platforms
-- recorded is never excluded by a platform filter. Silence is not an answer, and it must
-- not be read as the wrong one.
