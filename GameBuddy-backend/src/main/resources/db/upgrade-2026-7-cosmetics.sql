-- Frames and banners replace paid avatars.
--
-- Run after upgrade-2026-6-user-avatars.sql. Idempotent.

BEGIN;

-- --------------------------------------------------------------------------
-- 1. The cosmetics catalogue
-- --------------------------------------------------------------------------
-- Selling avatars stopped making sense the moment gamers could upload their own: a
-- picture everyone else can also have is a weak thing to charge for, and the good ones
-- are the ones people bring themselves. Frames and banners decorate an avatar rather
-- than being one, so they compose with an uploaded photo instead of competing with it.

CREATE TABLE IF NOT EXISTS gamebuddy.cosmetic (
    id            UUID          PRIMARY KEY,
    -- FRAME rings the avatar; BANNER sits behind the profile header.
    kind          VARCHAR(16)   NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    -- An object key, not a URL. Same reasoning as gamer.avatar_key: which host serves
    -- these changes, and a stored URL would bake today's answer into every row.
    asset_key     VARCHAR(255)  NOT NULL,
    -- Animated frames are animated WebP. The flag exists so the client can label them
    -- and the store can sort by it, not so it can pick a renderer — expo-image plays
    -- both, and a still frame is just an animation of length one.
    animated      BOOLEAN       NOT NULL DEFAULT FALSE,
    -- In coins. Zero means everyone owns it without buying it; see the ownership rule
    -- in CosmeticService, which treats price 0 as owned rather than seeding a row per
    -- gamer per free item.
    price         INTEGER       NOT NULL DEFAULT 0,
    -- Ordering in the store. Explicit because "cheapest first" and "newest first" are
    -- both wrong for a shelf someone curates.
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    created_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT cosmetic_kind_check CHECK (kind IN ('FRAME', 'BANNER')),
    CONSTRAINT cosmetic_price_check CHECK (price >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cosmetic_asset_key ON gamebuddy.cosmetic (asset_key);
CREATE INDEX IF NOT EXISTS idx_cosmetic_kind ON gamebuddy.cosmetic (kind, sort_order);

-- --------------------------------------------------------------------------
-- 2. Who owns what
-- --------------------------------------------------------------------------
-- Only paid items get a row. A free cosmetic is owned by everyone by definition, and
-- writing one row per gamer per free item would mean the table grows with the product
-- of two numbers to record something already known from the price.

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_cosmetic (
    user_id      VARCHAR(255)  NOT NULL REFERENCES gamebuddy.gamer(user_id),
    cosmetic_id  UUID          NOT NULL REFERENCES gamebuddy.cosmetic(id),
    -- What it cost at the time. Prices change; a receipt that silently changes with them
    -- is not a receipt.
    paid         INTEGER       NOT NULL DEFAULT 0,
    acquired_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, cosmetic_id)
);

CREATE INDEX IF NOT EXISTS idx_gamer_cosmetic_user ON gamebuddy.gamer_cosmetic (user_id);

-- --------------------------------------------------------------------------
-- 3. What each gamer is wearing
-- --------------------------------------------------------------------------
-- NULL means none, which is the default and a perfectly good look. Nullable foreign
-- keys rather than a join table because a gamer wears at most one of each — a table
-- would allow states the product does not have.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS equipped_frame_id UUID REFERENCES gamebuddy.cosmetic(id);

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS equipped_banner_id UUID REFERENCES gamebuddy.cosmetic(id);

-- --------------------------------------------------------------------------
-- 4. Paid avatars go
-- --------------------------------------------------------------------------
-- `avatars` survives as the catalogue of *default* pictures — the ones offered to
-- someone who does not want to upload anything — but nothing in it is for sale any
-- more, so the ownership table has nothing left to record. Nothing was ever bought:
-- this is a launch that has not happened, so no purchase is being destroyed.

DROP TABLE IF EXISTS gamebuddy.bought_avatars;

-- is_special was the "this one is paid" flag. With nothing paid it can only be false,
-- and a column that is always one value is a trap for the next person reading it.
ALTER TABLE gamebuddy.avatars DROP COLUMN IF EXISTS is_special;
ALTER TABLE gamebuddy.avatars DROP COLUMN IF EXISTS price;

COMMIT;
