-- One free item, and it has to be claimed.
--
-- Run after upgrade-2026-29-game-platforms.sql. Idempotent.
--
-- Two changes, and they have to land together.
--
-- 1. Free stopped meaning "everyone already owns this". DefaultCosmeticService now reads
--    ownership out of gamer_cosmetic and nothing else, so a free item is claimed from the
--    store like any other purchase — at a price of zero. Without the backfill below, every
--    existing gamer would lose the free items they are wearing the moment this deploys, and
--    MembershipCosmeticsJob.unequipUnownedCosmetics() would strip them from their profiles
--    on its next hourly run.
--
-- 2. Only the Steel frame stays free. The Signature frame and the Hex and CRT banners
--    become 100 coins — a new entry tier below Reticle at 150 and Bokeh at 200.
--
-- Anyone already wearing one of those three keeps it, for free, forever: they had it under
-- the old rules and repricing is not a reason to take something off somebody's profile.
-- New accounts pay.
--
-- NOTE ON IDS: these are the seed uuids (seed-local.sql). Production was populated out of
-- band, so confirm the catalogue matches before running this — the WHERE clauses below key
-- on asset_key as well as id so a differing id is a no-op rather than a wrong update:
--     SELECT id, kind, name, price FROM gamebuddy.cosmetic WHERE price <= 0 AND membership_only = false;

BEGIN;

-- Steel, for everyone who exists today. It was implicitly owned by every account, and this
-- is the row that makes that ownership real. Only accounts created after this deploy have
-- to claim it from the store — which is the point of the change.
INSERT INTO gamebuddy.gamer_cosmetic (user_id, cosmetic_id, paid, acquired_at)
SELECT g.user_id, c.id, 0, now()
FROM gamebuddy.gamer g
CROSS JOIN gamebuddy.cosmetic c
WHERE c.asset_key = 'frames/frame-steel.png'
ON CONFLICT DO NOTHING;

-- Grandfather the three that stop being free, for whoever is currently wearing one.
-- Wearers only, not everybody: an item nobody chose is not something they lose.
INSERT INTO gamebuddy.gamer_cosmetic (user_id, cosmetic_id, paid, acquired_at)
SELECT g.user_id, c.id, 0, now()
FROM gamebuddy.gamer g
JOIN gamebuddy.cosmetic c
  ON c.id IN (g.equipped_frame_id, g.equipped_banner_id)
WHERE c.asset_key IN ('frames/frame-brand.png', 'banners/banner-hex.jpg', 'banners/banner-crt.jpg')
ON CONFLICT DO NOTHING;

-- ...and now they cost something. Same transaction as the grants above, so there is no
-- instant in which an equipped item is neither free nor owned.
UPDATE gamebuddy.cosmetic
SET price = 100
WHERE membership_only = false
  AND price = 0
  AND asset_key IN ('frames/frame-brand.png', 'banners/banner-hex.jpg', 'banners/banner-crt.jpg');

COMMIT;
