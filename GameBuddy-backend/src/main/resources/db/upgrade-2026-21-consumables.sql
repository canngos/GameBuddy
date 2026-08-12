-- Consumables: things bought with coins that get used up.
--
-- Run after upgrade-2026-20-coin-faucets.sql. Idempotent.
--
-- Cosmetics are bought once and then owned forever, so a gamer who wants two frames spends
-- twice and is finished. The faucets now pour about 200 coins a week in; without something
-- that drains, the balance only rises and the currency stops meaning anything. These are
-- the drains.
--
-- Boost and Rewind already exist (upgrade-2026-19) and are priced in BoostPolicy. What is
-- new here is the state behind three more: super likes, extra likes for today, and
-- unlocking a single admirer.

-- ---------------------------------------------------------------------------
-- Super likes and extra likes
-- ---------------------------------------------------------------------------
--
-- Two counters on `gamer`, next to the swipe quota they interact with.
--
-- `super_likes` is an inventory: bought in advance, spent one at a time when a like is
-- sent. `bonus_accepts` is not an inventory — it is a temporary raise to today's cap, and
-- it is deliberately consumed by the same counter reset that clears the daily allowance.
-- Somebody who buys extra likes at 11pm gets them for that day, which is the day they
-- wanted them for; carrying them over would turn a consumable into a stockpile and remove
-- the reason to buy again tomorrow.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS super_likes    integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bonus_accepts  integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN gamebuddy.gamer.super_likes IS
    'Owned super likes, spent one per highlighted like. Never expires.';
COMMENT ON COLUMN gamebuddy.gamer.bonus_accepts IS
    'Extra likes added to TODAY''s cap. Cleared when the daily quota window rolls.';

-- ---------------------------------------------------------------------------
-- Unlocking one admirer
-- ---------------------------------------------------------------------------
--
-- The most important row on the shelf, and the reason it is a table rather than a counter:
-- what was bought is the right to see *one particular person*, so it has to be remembered
-- per pair. A counter would let somebody unlock, look, and then re-spend the same unlock
-- on somebody else — or worse, lose what they paid for when the list reordered.
--
-- It exists to let a free gamer taste the single best thing about Gold at a price they can
-- earn in a week. Everyone who buys one has told us, with the only currency that means
-- anything here, that they want the subscription's headline feature.
--
-- Rows are kept after the pair matches or is declined. They are cheap, and deleting them
-- would mean a gamer who unlocked somebody, matched, then unmatched would be charged
-- again to see a face they had already paid for.

CREATE TABLE IF NOT EXISTS gamebuddy.unlocked_admirer (
    user_id     varchar(255) NOT NULL,
    admirer_id  varchar(255) NOT NULL,
    unlocked_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, admirer_id)
);

COMMENT ON TABLE gamebuddy.unlocked_admirer IS
    'Admirers revealed one at a time with coins, by gamers without Gold.';

-- The only query: "which of these admirers has this gamer already paid to see", asked once
-- per load of the admirers screen.
CREATE INDEX IF NOT EXISTS idx_unlocked_admirer_user
    ON gamebuddy.unlocked_admirer (user_id);
