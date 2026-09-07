-- The deck boost is gone.
--
-- Run after upgrade-2026-30-claimable-steel.sql. Idempotent.
--
-- It bought thirty minutes at the front of decks in your country for 300 coins, free once a
-- week on Gold. It was removed because nobody could tell it had worked: what it promoted was
-- a face inside somebody else's stack, so the only evidence was whatever likes happened to
-- arrive next. Promotion moved to lobbies, where the boosted thing sits at the top of a list
-- with a frame around it and stays there until it starts — see upgrade-2026-32-lobby-boost.
--
-- Nothing is refunded. Boosts were consumed as they were bought and the last one expired
-- half an hour after it was paid for; there is no unspent balance to give back.
--
-- CoinReason.BOOST is deliberately NOT removed from the ledger. Those rows are history and
-- must keep resolving.

BEGIN;

DROP INDEX IF EXISTS gamebuddy.idx_gamer_boost_active;

ALTER TABLE gamebuddy.gamer
    DROP COLUMN IF EXISTS boost_expires_at,
    DROP COLUMN IF EXISTS last_free_boost_at;

COMMIT;
