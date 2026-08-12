-- What the monetisation funnel needs recorded.
--
-- Run after upgrade-2026-21-consumables.sql. Idempotent.
--
-- Four of the six numbers the analysis asks for cannot be computed from anything the
-- database currently holds, and no amount of querying will change that — the events never
-- happened as far as storage is concerned. This adds the recording. The queries are the
-- easy half; the half that has to exist before launch is this one, because a funnel metric
-- can only ever be measured forwards.
--
--   paywall view -> trial start      needs paywall views, which nothing records
--   trial -> paid                    needs trials distinguished from paid, which purchase does not
--   free -> paid at 30 days          computable already, from created_date and purchase
--   month-2 renewal                  needs renewals distinguished, same gap as trials
--   coins earned vs spent            needs a ledger; only the balance is stored
--   day-7 retention by cap cohort    needs a cohort, which does not exist

-- ---------------------------------------------------------------------------
-- Coin ledger
-- ---------------------------------------------------------------------------
--
-- Every movement of the currency, signed. Positive is a faucet, negative a sink.
--
-- The balance on `gamer` stays the source of truth for spending decisions — a running
-- SUM() on the hot path would be a poor trade — so this is a record of what happened
-- rather than a derivation of what is. That means the two can in principle disagree, and
-- the reconciliation query in AnalyticsRepository is what says whether they do.
--
-- It pays for itself twice over beyond the metric: "where did my coins go" is otherwise
-- unanswerable, for the gamer and for whoever is answering their email.

CREATE TABLE IF NOT EXISTS gamebuddy.coin_ledger (
    id         uuid         NOT NULL,
    user_id    varchar(255) NOT NULL,
    -- Signed. Never zero: a movement of nothing is not a movement.
    delta      integer      NOT NULL,
    -- Where it came from or went. A string rather than an enum column so a new faucet does
    -- not need a migration; the values are a Java enum, which is where the constraint that
    -- matters lives.
    reason     varchar(32)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

COMMENT ON TABLE gamebuddy.coin_ledger IS
    'Every coin movement, signed. Positive earns, negative spends.';

-- The two queries this table exists for: one gamer's history, and the weekly earned-versus
-- -spent ratio across everybody.
CREATE INDEX IF NOT EXISTS idx_coin_ledger_user ON gamebuddy.coin_ledger (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_coin_ledger_time ON gamebuddy.coin_ledger (created_at);

-- ---------------------------------------------------------------------------
-- Funnel events
-- ---------------------------------------------------------------------------
--
-- Deliberately thin: who, what, when. No properties bag, no free-text — a generic event
-- store invites the client to log everything and turns into a second, worse database that
-- nobody prunes. The kinds are a closed enum and the endpoint refuses anything else.
--
-- These are the only events the client is trusted to report, and they are all harmless to
-- forge: somebody who fakes a paywall view distorts a conversion rate downwards, which is
-- a poor exploit. Anything that grants something stays server-side.

CREATE TABLE IF NOT EXISTS gamebuddy.funnel_event (
    id         uuid         NOT NULL,
    user_id    varchar(255) NOT NULL,
    kind       varchar(32)  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

COMMENT ON TABLE gamebuddy.funnel_event IS
    'Client-reported funnel steps. Nothing here grants anything; see CoinLedger for money.';

CREATE INDEX IF NOT EXISTS idx_funnel_event_kind ON gamebuddy.funnel_event (kind, created_at);
CREATE INDEX IF NOT EXISTS idx_funnel_event_user ON gamebuddy.funnel_event (user_id, kind);

-- ---------------------------------------------------------------------------
-- Trials, renewals, and the like-cap cohort
-- ---------------------------------------------------------------------------
--
-- period_type comes straight from RevenueCat (TRIAL, INTRO, NORMAL, PROMOTIONAL, PREPAID)
-- and event_type says whether a row was a first purchase or a renewal. Without both, a
-- trial and a paid month are the same row and neither conversion rate exists.

ALTER TABLE gamebuddy.purchase
    ADD COLUMN IF NOT EXISTS period_type varchar(16),
    ADD COLUMN IF NOT EXISTS event_type  varchar(32);

COMMENT ON COLUMN gamebuddy.purchase.period_type IS
    'RevenueCat period_type. TRIAL is what separates a trial start from a paid month.';
COMMENT ON COLUMN gamebuddy.purchase.event_type IS
    'RevenueCat event type. RENEWAL is what makes month-2 retention countable.';

-- The cohort every account is assigned to at registration, for the free like-cap A/B.
--
-- Recorded now even though the cap is still uniform, because a cohort assigned later is
-- not a cohort: the accounts that already exist would land in whichever bucket the new
-- rule put them, and their history would be attributed to an experiment that was not
-- running when they lived it.
ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS like_cap_cohort varchar(16);

COMMENT ON COLUMN gamebuddy.gamer.like_cap_cohort IS
    'Stable A/B bucket for the free daily like cap. Assigned once, at registration.';

-- Existing accounts get a bucket too, derived from the id so it is stable and evenly
-- split. They pre-date the experiment, which the analysis will have to account for, but
-- leaving them null would make every cohort query need a special case forever.
UPDATE gamebuddy.gamer
   SET like_cap_cohort = CASE WHEN ('x' || substr(md5(user_id), 1, 8))::bit(32)::bigint % 2 = 0
                              THEN 'CONTROL' ELSE 'VARIANT' END
 WHERE like_cap_cohort IS NULL;

CREATE INDEX IF NOT EXISTS idx_gamer_cohort ON gamebuddy.gamer (like_cap_cohort, created_date);
