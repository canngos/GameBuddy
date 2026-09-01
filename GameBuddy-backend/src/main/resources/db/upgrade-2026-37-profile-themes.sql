-- Profile card themes: the third kind of cosmetic, and the first one that is not a picture.
--
-- Run after upgrade-2026-36-market-wave-2.sql. Idempotent.
--
-- A theme is a decorative edge drawn around the whole card: the deck card other people
-- swipe past, and the owner's profile card. It is the frame idea applied one level out —
-- avatar frames ring a picture, a theme rings the card that picture sits on — and it is the
-- first cosmetic seen on somebody's *card* rather than on their avatar.
--
-- It is deliberately NOT a background. Filling the card with the theme's colours was tried
-- first and every theme read as a second banner; a border is what makes it read as worn.
--
-- **There is no image.** A theme is two colour stops, and the stops live in the app
-- (src/theme/cardThemes.js) rather than in this table. The row carries only a slug, in
-- asset_key, and the client looks the colours up. Three reasons, in order of weight:
--
--   1. The contrast gate (`npm run check`) can only test colours it can see at build time.
--      Stops arriving from a database row would ship past CI unchecked, and this is
--      precisely the surface where a bad pair makes a username unreadable.
--   2. `CosmeticKind` already promises that a third kind arrives with a renderer for it —
--      the renderer holds the palette.
--   3. asset_key stays NOT NULL and unique, and uuid5(NAMESPACE_DNS, asset_key) keeps
--      minting ids the same way it does for every other row.
--
-- Adding a theme in future therefore means a row here *and* an entry in cardThemes.js; a
-- row alone renders nothing, which is the failure mode we want (invisible, not wrong).

BEGIN;

-- --------------------------------------------------------------------------
-- 1. Let the kind exist
-- --------------------------------------------------------------------------

ALTER TABLE gamebuddy.cosmetic DROP CONSTRAINT IF EXISTS cosmetic_kind_check;
ALTER TABLE gamebuddy.cosmetic
    ADD CONSTRAINT cosmetic_kind_check CHECK (kind IN ('FRAME', 'BANNER', 'THEME'));

-- --------------------------------------------------------------------------
-- 2. The third equipped slot
-- --------------------------------------------------------------------------
--
-- A slot of its own rather than reusing one: a gamer wears a frame, a banner and a theme
-- at the same time, and they are independent choices.

ALTER TABLE gamebuddy.gamer ADD COLUMN IF NOT EXISTS equipped_theme_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'gamer_equipped_theme_id_fkey'
    ) THEN
        ALTER TABLE gamebuddy.gamer
            ADD CONSTRAINT gamer_equipped_theme_id_fkey
            FOREIGN KEY (equipped_theme_id) REFERENCES gamebuddy.cosmetic(id);
    END IF;
END $$;

-- --------------------------------------------------------------------------
-- 3. The catalogue
-- --------------------------------------------------------------------------
--
-- Six duotones, deliberately deep so the edge reads against both the card and the page
-- behind it, and nowhere near the bright mid-tones avatarGradient() derives from a user id,
-- so a themed card never looks like an untuned one. No gold — that is the membership
-- signal. `animated` marks the two whose edge turns; what that means is drawn by
-- src/theme/cardThemes.js, and this flag is the shop's label for it.

INSERT INTO gamebuddy.cosmetic (id, kind, name, asset_key, animated, price, sort_order, membership_only, created_date) VALUES
  ('9851c503-c432-5da5-8901-a4be534fdf7b', 'THEME', 'Viridian', 'themes/viridian', false,  400, 0, false, now()),
  ('440b494f-4d46-55b6-a4b4-14ecdc5df2a6', 'THEME', 'Glacier',  'themes/glacier',  false,  500, 1, false, now()),
  ('6881863d-b621-509d-ab32-ad50b8fcebcb', 'THEME', 'Midnight', 'themes/midnight', false,  600, 2, false, now()),
  ('b92b2dee-2e69-50e0-8401-c49d0adcc8a1', 'THEME', 'Cinder',   'themes/cinder',   false,  750, 3, false, now()),
  ('a639fa37-2e66-509d-9f17-a5086ad01088', 'THEME', 'Royal',    'themes/royal',    true,   900, 4, false, now()),
  ('fafea371-1ac5-54ad-bca2-715478f149a5', 'THEME', 'Nova',     'themes/nova',     true,  1200, 5, false, now())
ON CONFLICT (asset_key) DO UPDATE
    SET kind = EXCLUDED.kind,
        name = EXCLUDED.name,
        animated = EXCLUDED.animated,
        price = EXCLUDED.price,
        sort_order = EXCLUDED.sort_order,
        membership_only = EXCLUDED.membership_only;

COMMIT;
