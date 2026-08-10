-- Cosmetics that come with a Gold membership rather than being bought.
--
-- Run after upgrade-2026-17-row-timestamps.sql. Idempotent.
--
-- Two items: the Gold frame, which already existed and was on sale for 400 coins, and a
-- new Gold banner drawn for this. Neither is purchasable now — they arrive with a
-- subscription and are taken back when it lapses.
--
-- **Why they are withdrawn on lapse.** The whole argument for giving a subscriber
-- something visible is that every one of them advertises the tier for free. That only
-- works if wearing it means you are currently a member. A lapsed member keeping the frame
-- turns it from a signal into decoration, and the second person who notices that stops
-- reading it as anything at all.
--
-- Anyone who bought the Gold frame with coins before this is refunded below. There are no
-- real users yet — this is written to be correct rather than because it will refund
-- anybody today.

BEGIN;

-- Not for sale, and not merely price = 0: free items are already a concept here
-- (Cosmetic.isFree), and they mean "everyone has this", which is the opposite.
ALTER TABLE gamebuddy.cosmetic
    ADD COLUMN IF NOT EXISTS membership_only boolean NOT NULL DEFAULT false;

-- The new banner. Fixed uuid so re-running this cannot create a second one, and so every
-- environment agrees on the id.
INSERT INTO gamebuddy.cosmetic (id, kind, name, asset_key, animated, price, sort_order, membership_only)
VALUES ('1f0c9f4e-2b6a-4d3e-9c47-5a8b1e7d6c20', 'BANNER', 'Gold', 'banners/banner-gold.jpg', false, 0, 0, true)
ON CONFLICT (id) DO UPDATE
    SET kind = EXCLUDED.kind,
        name = EXCLUDED.name,
        asset_key = EXCLUDED.asset_key,
        price = EXCLUDED.price,
        membership_only = EXCLUDED.membership_only;

-- The existing Gold frame stops being for sale.
UPDATE gamebuddy.cosmetic
SET membership_only = true, price = 0
WHERE kind = 'FRAME' AND asset_key = 'frames/frame-gold.png';

-- Refund anybody who already bought it. `paid` on the ownership row is what they were
-- charged, so the refund is exact rather than a guess at the current price.
WITH refunds AS (
    SELECT gc.user_id, gc.paid
    FROM gamebuddy.gamer_cosmetic gc
    JOIN gamebuddy.cosmetic c ON c.id = gc.cosmetic_id
    WHERE c.membership_only = true AND gc.paid > 0
)
UPDATE gamebuddy.gamer g
SET coin = g.coin + r.paid
FROM refunds r
WHERE g.user_id = r.user_id;

-- ...and take the item back, so ownership is only ever granted by the membership from
-- here. The membership job re-grants it on the next run for anyone who is actually Gold.
DELETE FROM gamebuddy.gamer_cosmetic gc
USING gamebuddy.cosmetic c
WHERE c.id = gc.cosmetic_id AND c.membership_only = true;

-- Nobody is left wearing something they no longer own.
UPDATE gamebuddy.gamer g
SET equipped_frame_id = NULL
WHERE equipped_frame_id IN (SELECT id FROM gamebuddy.cosmetic WHERE membership_only = true);

UPDATE gamebuddy.gamer g
SET equipped_banner_id = NULL
WHERE equipped_banner_id IN (SELECT id FROM gamebuddy.cosmetic WHERE membership_only = true);

COMMIT;
