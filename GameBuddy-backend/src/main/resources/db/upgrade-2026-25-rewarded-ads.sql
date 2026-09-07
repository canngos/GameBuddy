-- Coins for watching a rewarded advert.
--
-- Run after upgrade-2026-24-platforms.sql. Idempotent.
--
-- The grant happens on AdMob's server-side verification callback, not on the app saying it
-- watched something. That distinction is the entire security model: the callback URL is
-- public and carries no session, so anyone can call it — what they cannot do is produce
-- Google's ECDSA signature over the query string. See RewardedAdVerifier.
--
-- Two things therefore have to be stored: which callbacks have already been honoured, and
-- how many adverts each gamer has been paid for today.

-- ---------------------------------------------------------------------------
-- Honoured callbacks
-- ---------------------------------------------------------------------------
--
-- AdMob retries a callback that does not answer 200, and a signed URL stays valid as long
-- as the key does — so both an honest retry and a replayed capture arrive as a byte-for-
-- byte repeat. The transaction id is unique per reward and the primary key is what makes
-- honouring it twice impossible, rather than a check-then-insert that two concurrent
-- retries would both pass.

CREATE TABLE IF NOT EXISTS gamebuddy.rewarded_ad_grant (
    -- AdMob's transaction_id. Their value, not ours, which is the point: it is the same
    -- across every retry of the same reward.
    transaction_id varchar(128) NOT NULL,
    user_id        varchar(255) NOT NULL,
    coins          integer      NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (transaction_id)
);

COMMENT ON TABLE gamebuddy.rewarded_ad_grant IS
    'Rewarded-ad callbacks already honoured. The primary key is the replay defence.';

-- "How many has this gamer been paid for today", and the audit trail behind a support
-- question about a missing reward.
CREATE INDEX IF NOT EXISTS idx_rewarded_ad_grant_user ON gamebuddy.rewarded_ad_grant (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- The daily cap
-- ---------------------------------------------------------------------------
--
-- Counted on the gamer rather than by COUNT(*) over the table above. The cap is checked on
-- the callback path, which is the one path an attacker can hammer for free, and a counter
-- read is cheaper to serve than an aggregate — the table is append-only and grows forever.
--
-- The day is stored beside the count instead of being inferred, so a gamer who last
-- watched in a previous day is reset by comparing two values rather than by a nightly job
-- that can fail and leave everybody capped.

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS rewarded_ads_today integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rewarded_ad_day    timestamptz;

COMMENT ON COLUMN gamebuddy.gamer.rewarded_ads_today IS
    'Rewarded adverts paid for during rewarded_ad_day. Meaningless once that day has passed.';
COMMENT ON COLUMN gamebuddy.gamer.rewarded_ad_day IS
    'UTC midnight of the day rewarded_ads_today counts. Null means never watched one.';
