-- Boosting a lobby: 300 coins to sit at the top of the browse list.
--
-- Run after upgrade-2026-31-retire-deck-boost.sql. Idempotent.
--
-- A stamp, not an expiry. Browse only ever shows OPEN lobbies, so the pin ends by itself
-- when the lobby locks, starts, ends or is cancelled — there is no clock to run down and no
-- sweeper that can fail and leave a dead lobby pinned. That is also what the boost promises:
-- the top of the list for the life of the plan, rather than a countdown that can expire
-- while the lobby is still filling.
--
-- The index mirrors idx_gamer_boost_active, which the previous migration dropped: partial,
-- so it holds only the handful of rows that are actually boosted rather than one entry per
-- lobby ever created.

BEGIN;

ALTER TABLE gamebuddy.lobby
    ADD COLUMN IF NOT EXISTS boosted_at timestamptz;

COMMENT ON COLUMN gamebuddy.lobby.boosted_at IS
    'When the owner paid to pin this lobby. NULL means never; the pin ends when status leaves OPEN.';

CREATE INDEX IF NOT EXISTS idx_lobby_boosted
    ON gamebuddy.lobby (boosted_at)
    WHERE boosted_at IS NOT NULL;

COMMIT;
