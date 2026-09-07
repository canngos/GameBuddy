-- Animated banners, two more animated frames, and the bundles that sell them in pairs.
--
-- Run after upgrade-2026-35-shelf-expansion.sql. Idempotent.
--
-- Three things arrive together because they are one product idea. The shop had nothing to
-- sell as a *set*: every row was a single item at a single price, so there was no offer
-- worth coming back for and no reason to buy two things at once. Two matched sets fix that
-- — an animated frame drawn to go with an animated banner, priced about a fifth under the
-- sum of its parts — and the fifth item, Signal, is the standalone animated banner that
-- proves the format on its own.
--
-- **Animated banners are new.** Until now every banner was a still JPEG; these are looping
-- WebP at half the still banners' resolution (see ANIM_BANNER_W in cosmetics/generate.py
-- for why that is invisible on a 96dp header and necessary for the file size). Nothing in
-- the app had to change to play them: `animated` was always a labelling flag rather than a
-- renderer switch, and expo-image autoplays by default at every site that draws a banner.
--
-- Ids are uuid5(NAMESPACE_DNS, asset_key), as everywhere else in this catalogue.
--
-- CONFLICT KEYS: cosmetics key on asset_key for the reason upgrade-2026-35 gives (the CDN
-- key is what an item *is*, and production was populated out of band). The bundle tables
-- are new here, so they key on id.
--
-- The art has to be uploaded before this runs, or five rows point at nothing:
--     python cosmetics/generate.py && tools/upload-assets.sh frames banners

BEGIN;

-- --------------------------------------------------------------------------
-- 1. The five new cosmetics
-- --------------------------------------------------------------------------

INSERT INTO gamebuddy.cosmetic (id, kind, name, asset_key, animated, price, sort_order, membership_only, created_date) VALUES
  ('b60b497f-4675-5431-80db-73ee6fad0109', 'FRAME',  'Pulsar',  'frames/frame-pulsar.webp',    true,  900, 14, false, now()),
  ('4b5d091f-1563-5e06-b9f0-3771c537484b', 'FRAME',  'Reactor', 'frames/frame-reactor.webp',   true, 1100, 16, false, now()),
  ('0a03c68f-90d1-535e-8ee8-ac3a3a0cdbe0', 'BANNER', 'Signal',  'banners/banner-signal.webp',  true, 1000, 16, false, now()),
  ('13b630c2-67f9-5771-91e6-5b0cd5e23c0b', 'BANNER', 'Nebula',  'banners/banner-nebula.webp',  true, 1100, 17, false, now()),
  ('b80de761-d961-5ec0-854a-587ae2b03839', 'BANNER', 'Core',    'banners/banner-core.webp',    true, 1300, 18, false, now())
ON CONFLICT (asset_key) DO UPDATE
    SET kind = EXCLUDED.kind,
        name = EXCLUDED.name,
        animated = EXCLUDED.animated,
        price = EXCLUDED.price,
        sort_order = EXCLUDED.sort_order,
        membership_only = EXCLUDED.membership_only;

-- Pulsar (900) and Reactor (1100) land inside the existing animated frame ladder, so the
-- four rows above them shift up. Banners need no renumber — all three new ones are dearer
-- than Zone and simply append.
UPDATE gamebuddy.cosmetic c
SET sort_order = v.sort_order
FROM (VALUES
  ('frames/frame-comet.webp', 15),
  ('frames/frame-rotor.webp', 17),
  ('frames/frame-glitch.webp', 18),
  ('frames/frame-ember.webp', 19)
) AS v(asset_key, sort_order)
WHERE c.asset_key = v.asset_key AND c.membership_only = false;

-- --------------------------------------------------------------------------
-- 2. Bundles
-- --------------------------------------------------------------------------
--
-- A bundle is a price on a set, not a new kind of thing to own: buying one writes the same
-- gamer_cosmetic rows a pair of separate purchases would, so everything downstream —
-- equipping, the membership sweep, the badge that counts owned cosmetics — keeps working
-- without knowing bundles exist.

CREATE TABLE IF NOT EXISTS gamebuddy.cosmetic_bundle (
    id           uuid PRIMARY KEY,
    name         varchar(64)  NOT NULL,
    price        int          NOT NULL CHECK (price >= 0),
    sort_order   int          NOT NULL DEFAULT 0,
    created_date timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gamebuddy.cosmetic_bundle_item (
    bundle_id   uuid NOT NULL REFERENCES gamebuddy.cosmetic_bundle(id) ON DELETE CASCADE,
    cosmetic_id uuid NOT NULL REFERENCES gamebuddy.cosmetic(id),
    PRIMARY KEY (bundle_id, cosmetic_id)
);

-- Deep Space: Pulsar 900 + Nebula 1100 = 2000, sold at 1600.
-- Meltdown:   Reactor 1100 + Core 1300 = 2400, sold at 1900.
INSERT INTO gamebuddy.cosmetic_bundle (id, name, price, sort_order, created_date) VALUES
  ('3956f857-1381-56a6-aabd-11795ec8d26e', 'Deep Space', 1600, 0, now()),
  ('bf79fbbb-191e-59cc-b24f-3c145740aa5d', 'Meltdown',   1900, 1, now())
ON CONFLICT (id) DO UPDATE
    SET name = EXCLUDED.name, price = EXCLUDED.price, sort_order = EXCLUDED.sort_order;

INSERT INTO gamebuddy.cosmetic_bundle_item (bundle_id, cosmetic_id) VALUES
  ('3956f857-1381-56a6-aabd-11795ec8d26e', 'b60b497f-4675-5431-80db-73ee6fad0109'),
  ('3956f857-1381-56a6-aabd-11795ec8d26e', '13b630c2-67f9-5771-91e6-5b0cd5e23c0b'),
  ('bf79fbbb-191e-59cc-b24f-3c145740aa5d', '4b5d091f-1563-5e06-b9f0-3771c537484b'),
  ('bf79fbbb-191e-59cc-b24f-3c145740aa5d', 'b80de761-d961-5ec0-854a-587ae2b03839')
ON CONFLICT DO NOTHING;

COMMIT;
