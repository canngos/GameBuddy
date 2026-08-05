-- Achievements become badges.
--
-- Run after upgrade-2026-7-cosmetics.sql. Idempotent.
--
-- The old model was a three-row `achievements` catalogue plus two join tables — earned,
-- and collected — and the rule that awarded each one lived in whichever service happened
-- to notice. That meant the *name* of an achievement was its identity, matched as a
-- string from three different modules, and the condition for earning it existed nowhere
-- in particular.
--
-- The catalogue is now the Badge enum: a mission is a rule, and a rule cannot live in a
-- row. What is left for the database is the only thing it was ever the authority on —
-- which gamer earned which badge, when, whether they claimed the coins, and which three
-- they put on show.

BEGIN;

-- --------------------------------------------------------------------------
-- 1. Earned badges
-- --------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_badge (
    user_id       VARCHAR(255) NOT NULL REFERENCES gamebuddy.gamer(user_id),
    -- Badge.code. Not a foreign key: there is no catalogue table to point at, and a code
    -- whose mission has been retired should leave an inert row rather than a dangling one.
    badge_code    VARCHAR(48)  NOT NULL,
    earned_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULL while the coins are still waiting to be claimed. A timestamp rather than a
    -- second membership table, which is what "collected" used to be.
    collected_at  TIMESTAMPTZ,
    -- 0..2 for one of the three badges on the profile, NULL for the rest.
    -- INTEGER rather than SMALLINT: the entity's field is an Integer, and ddl-auto=validate
    -- rejects int2 against it. Two bytes are not worth a schema the application refuses.
    showcase_slot INTEGER,
    PRIMARY KEY (user_id, badge_code),
    CONSTRAINT gamer_badge_slot_check CHECK (showcase_slot BETWEEN 0 AND 2)
);

-- One badge per slot. Partial, because NULL is the normal case and every gamer would
-- otherwise be limited to a single unshowcased badge.
CREATE UNIQUE INDEX IF NOT EXISTS idx_gamer_badge_showcase
    ON gamebuddy.gamer_badge (user_id, showcase_slot)
    WHERE showcase_slot IS NOT NULL;

-- --------------------------------------------------------------------------
-- 2. Carry the old achievements across
-- --------------------------------------------------------------------------
-- All three survive as missions with the same meaning, so nobody loses anything they
-- earned. Their ids are hard-coded because that is what seed-local.sql inserted; a
-- database that never had them simply copies nothing.

INSERT INTO gamebuddy.gamer_badge (user_id, badge_code, earned_at, collected_at)
SELECT
    e.gamer_id,
    CASE a.id
        WHEN 'd1709f16-3061-5432-aa06-69314a18500f' THEN 'squad-forming'      -- Talkative Person
        WHEN 'eb9e0898-17e8-53fc-8e0f-514f9035cad1' THEN 'friendly-person'
        WHEN 'd5563604-5957-5595-8357-870da80657c0' THEN 'rich-in-the-hood'
    END,
    now(),
    -- Anything already collected stays collected: re-crediting the coins would pay twice
    -- for one achievement.
    (SELECT now() FROM gamebuddy.gamer_collected_achievements c
      WHERE c.gamer_id = e.gamer_id AND c.achievement_id = e.achievement_id)
FROM gamebuddy.gamer_earned_achievements e
JOIN gamebuddy.achievements a ON a.id = e.achievement_id
WHERE a.id IN (
    'd1709f16-3061-5432-aa06-69314a18500f',
    'eb9e0898-17e8-53fc-8e0f-514f9035cad1',
    'd5563604-5957-5595-8357-870da80657c0'
)
ON CONFLICT (user_id, badge_code) DO NOTHING;

-- --------------------------------------------------------------------------
-- 3. The old model goes
-- --------------------------------------------------------------------------

DROP TABLE IF EXISTS gamebuddy.gamer_collected_achievements;
DROP TABLE IF EXISTS gamebuddy.gamer_earned_achievements;
DROP TABLE IF EXISTS gamebuddy.achievements;

COMMIT;
