-- The shelf gets 40% dearer.
--
-- Run after upgrade-2026-43-review-prompt.sql. Idempotent: every UPDATE sets an absolute
-- price, so running it twice changes nothing the second time.
--
-- WHY. Coins are what the shop is priced in, and the shop is the business. Priced against
-- the pack people actually buy -- 3000 coins for $8.99, not the 500-coin pack the old
-- arithmetic in cosmetics-market-research.md used -- a coin was worth $0.0030, which put
-- the dearest item in the whole catalogue (the Ember frame, 1500) at $4.50 and the dearest
-- purchase (the Meltdown bundle, 1900) at $5.70. Discord charges $5.99-$12.99 for a single
-- avatar decoration. Our entire ceiling sat below the market's floor, and at the shipped
-- earn rate of ~320 coins a week a free player reached that ceiling in under five weeks --
-- so the flagship was not really a purchase at all. This raises the shelf 40% and the coin
-- packs double in the stores at the same time; together the Ember frame goes from $4.50 to
-- about $11.89, which is inside the band the market already charges.
--
-- WHAT IS DELIBERATELY NOT TOUCHED.
--
--   * The seven price-0 rows. Steel is the one free item and it is claimed, not owned
--     (upgrade-2026-30); the two Gold items are membership_only; the four trophy frames are
--     unlocked_by_badge (upgrade-2026-39). The guards below make it impossible to hit them
--     even if an asset_key were wrong.
--   * sort_order. It runs by price within each kind, but this reprice is monotonic -- every
--     item keeps its neighbours -- so unlike upgrade-2026-35 there is nothing to renumber.
--   * Ownership. Prices are going UP, so anything already bought stays bought at what was
--     paid, and gamer_cosmetic.paid keeps the honest historical figure. upgrade-2026-18 had
--     to refund because an item became free; the reverse needs nothing.
--   * Earn rates. gamebuddy.coins.* stays as tuned on 2026-08-31 (~320/wk). Raising the
--     shelf already lengthens the grind; moving both at once would overshoot.
--
-- The entry rung stays inside week one on purpose -- 150 coins is about 3 days of free play.
-- A player who never buys anything never learns that coins matter, and then never buys a
-- pack either.
--
-- KEYED ON asset_key, not id: production was populated out of band, so a differing id must
-- be a no-op rather than a wrong update. Confirm the shelf afterwards -- and do not trust
-- "migration applied", because an UPDATE against rows that are not there yet reports success
-- and changes nothing (see README.md on baseline -> seed -> upgrades ordering):
--     SELECT name, price FROM gamebuddy.cosmetic WHERE price > 0 ORDER BY kind, sort_order;
--
-- seed-local.sql carries the same numbers and was changed in the same commit. It upserts
-- with price = EXCLUDED.price, so a stale seed silently reverts this file.

BEGIN;

UPDATE gamebuddy.cosmetic AS c
SET price = v.price
FROM (VALUES
  -- Frames. Static below, animated above -- the split is at Pulse, and animated has always
  -- cost more because it took more to make and is the thing people actually want.
  ('frames/frame-brand.png',      150),
  ('frames/frame-reticle.png',    200),
  ('frames/frame-bronze.png',     300),
  ('frames/frame-ticker.png',     350),
  ('frames/frame-scope.png',      400),
  ('frames/frame-hazard.png',     500),
  ('frames/frame-hive.png',       550),
  ('frames/frame-pulse.webp',     850),
  ('frames/frame-frost.webp',    1000),
  ('frames/frame-sweep.webp',    1050),
  ('frames/frame-radar.webp',    1200),
  ('frames/frame-orbit.webp',    1250),
  ('frames/frame-toxic.webp',    1250),
  ('frames/frame-pulsar.webp',   1250),
  ('frames/frame-comet.webp',    1400),
  ('frames/frame-reactor.webp',  1550),
  ('frames/frame-rotor.webp',    1700),
  ('frames/frame-glitch.webp',   1800),
  ('frames/frame-ember.webp',    2100),
  -- Banners.
  ('banners/banner-hex.jpg',       150),
  ('banners/banner-crt.jpg',       150),
  ('banners/banner-overworld.jpg', 200),
  ('banners/banner-bokeh.jpg',     300),
  ('banners/banner-dungeon.jpg',   350),
  ('banners/banner-circuit.jpg',   400),
  ('banners/banner-wasd.jpg',      500),
  ('banners/banner-void.jpg',      550),
  ('banners/banner-gamepad.jpg',   650),
  ('banners/banner-arena.jpg',     700),
  ('banners/banner-chicane.jpg',   750),
  ('banners/banner-dusk.jpg',      850),
  ('banners/banner-minimap.jpg',   900),
  ('banners/banner-tabletop.jpg', 1000),
  ('banners/banner-synthwave.jpg',1100),
  ('banners/banner-zone.jpg',     1250),
  ('banners/banner-signal.webp',  1400),
  ('banners/banner-nebula.webp',  1550),
  ('banners/banner-core.webp',    1800),
  -- Themes. A slug rather than a file: the colours live in the app, in cardThemes.js.
  ('themes/viridian',  550),
  ('themes/glacier',   700),
  ('themes/midnight',  850),
  ('themes/cinder',   1050),
  ('themes/royal',    1250),
  ('themes/nova',     1700)
) AS v(asset_key, price)
WHERE c.asset_key = v.asset_key
  -- Belt and braces. Neither guard should ever bite, because no key above belongs to a
  -- free, membership or trophy item -- but a reprice that could accidentally put a price on
  -- the Gold frame would put a members-only item back on the shelf, which is exactly the
  -- bug upgrade-2026-18 existed to fix.
  AND c.membership_only = false
  AND c.price > 0;

-- Bundles hold their ~20% discount against the NEW parts total: Deep Space is Pulsar 1250 +
-- Nebula 1550 = 2800, and Meltdown is Reactor 1550 + Core 1800 = 3350. partsPrice is summed
-- server-side from the items, so it follows the reprice above on its own and only the
-- bundle's own price needs setting here.
UPDATE gamebuddy.cosmetic_bundle AS b
SET price = v.price
FROM (VALUES
  ('Deep Space', 2250),
  ('Meltdown',   2700)
) AS v(name, price)
WHERE b.name = v.name;

COMMIT;
