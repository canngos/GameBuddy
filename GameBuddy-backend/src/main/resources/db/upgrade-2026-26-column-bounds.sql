-- Column bounds and one warning, from the QA pass of 2026-08-14.
--
-- Two unrelated things, both about a column saying something the code does not.
--
-- Apply after upgrade-2026-25-rewarded-ads.sql. Idempotent: safe to run twice.

SET search_path TO gamebuddy;

BEGIN;

-- 1. gamer.fcm_token was varchar(255) while FcmTokenRequest accepts 512.
--
-- The same defect the community and post fields had, and found the same way: validation
-- passes, Postgres refuses, and the constraint violation escapes as a 500. It has not bitten
-- yet only because Firebase tokens are usually about 160 characters — but they are not
-- specified to be, they have grown before, and a device that gets a long one would lose push
-- notifications permanently with a 500 nobody would connect to it.
--
-- Widening a varchar needs no table rewrite in Postgres 9.2+, and the partial unique index
-- on this column is not rebuilt either, so this is cheap on a live table.
ALTER TABLE gamer ALTER COLUMN fcm_token TYPE varchar(512);

-- 2. subscription_tier is not the source of truth, and nothing said so.
--
-- The effective tier is derived: SubscriptionTier.effective(stored, expiresAt, now). Expiry
-- works by moving subscription_expires_at into the past, and this column keeps whatever it
-- last held — so a lapsed subscriber still reads 'GOLD' here forever.
--
-- Every query in the application already pairs the two correctly (AnalyticsRepository,
-- GamerCosmeticRepository). The risk is the next person, writing an ad-hoc count of paying
-- users against the obvious-looking column and over-counting silently. Clearing the column
-- would not fix that: a subscription whose expiry simply passes, with no webhook to react
-- to, is never written to again by anybody. The trap is structural, so the warning goes
-- where \d+ will show it.
COMMENT ON COLUMN gamer.subscription_tier IS
    'NOT the effective tier. A tier counts only while subscription_expires_at is in the '
    'future; this column is not cleared when that passes, so it reads GOLD for lapsed '
    'subscribers. Always filter on subscription_expires_at as well — see '
    'SubscriptionTier.effective().';

COMMIT;
