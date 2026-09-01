-- Promotion codes: coins or Gold, handed out by an administrator rather than bought.
--
-- Run after upgrade-2026-37-profile-themes.sql. Idempotent.
--
-- Three tables, because a code is one thing and the two lists hanging off it answer
-- different questions:
--
--   promo_code             what was issued, and on what terms
--   promo_code_assignment  who it was addressed to (no rows = anybody may type it)
--   promo_redemption       who actually took it, and what they got
--
-- Assignment and redemption are separate on purpose. A code sent to two hundred dormant
-- accounts has two hundred assignments and, realistically, a dozen redemptions; folding
-- them into one table with a nullable "redeemed_at" would make "who has not used it yet"
-- and "what did we actually pay out" the same query with different WHERE clauses, and the
-- second of those is the one that has to be right.
--
-- **The code is stored in clear**, unlike verification_code, which is bcrypted. Different
-- secret: a verification code proves an address belongs to whoever holds it, and nobody --
-- including staff -- should be able to read one back. A promotion code is a coupon. The
-- console has to display it so it can be re-sent or read out, and what stands between a
-- guesser and a free month is the eight-character space plus the per-account rate limit
-- on redemption, not confidentiality at rest.
--
-- **Both child tables cascade.** Deleting a code is a real delete, offered next to the
-- reversible disable, because this console is the owner's own back office and a mistyped
-- test code should be able to leave. Nothing that matters is lost: coins granted live in
-- coin_ledger under reason PROMO_CODE, and Gold granted lives on gamer.subscription_*.
-- A redemption row records the offer, not the money.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. The code
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.promo_code (
    id               uuid         PRIMARY KEY,
    code             varchar(32)  NOT NULL,
    kind             varchar(8)   NOT NULL,
    coin_amount      integer,
    gold_days        integer,
    expires_at       timestamptz  NOT NULL,
    max_redemptions  integer,
    redemption_count integer      NOT NULL DEFAULT 0,
    disabled_at      timestamptz,
    created_by       varchar(255) NOT NULL,
    note             varchar(200),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

-- Codes are compared uppercased, so the column holds them that way and uniqueness is over
-- what a person actually types. Without the CHECK, 'gift' and 'GIFT' would be two codes
-- and only one of them would ever be found.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promo_code_code_upper_check') THEN
        ALTER TABLE gamebuddy.promo_code
            ADD CONSTRAINT promo_code_code_upper_check CHECK (code = upper(code));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promo_code_code_key') THEN
        ALTER TABLE gamebuddy.promo_code ADD CONSTRAINT promo_code_code_key UNIQUE (code);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promo_code_kind_check') THEN
        ALTER TABLE gamebuddy.promo_code
            ADD CONSTRAINT promo_code_kind_check CHECK (kind IN ('COIN', 'GOLD'));
    END IF;

    -- Exactly one payload, matching the kind. A COIN code with gold_days set is not a
    -- half-configured row, it is a row two code paths would read differently.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promo_code_payload_check') THEN
        ALTER TABLE gamebuddy.promo_code
            ADD CONSTRAINT promo_code_payload_check CHECK (
                (kind = 'COIN' AND coin_amount IS NOT NULL AND coin_amount > 0 AND gold_days IS NULL)
             OR (kind = 'GOLD' AND gold_days IS NOT NULL AND gold_days > 0 AND coin_amount IS NULL));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'promo_code_max_redemptions_check') THEN
        ALTER TABLE gamebuddy.promo_code
            ADD CONSTRAINT promo_code_max_redemptions_check
            CHECK (max_redemptions IS NULL OR max_redemptions > 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_promo_code_created
    ON gamebuddy.promo_code USING btree (created_at DESC);

COMMENT ON TABLE  gamebuddy.promo_code IS
    'Administrator-issued coupons granting coins or Gold. Stored in clear so the console can display them.';
COMMENT ON COLUMN gamebuddy.promo_code.max_redemptions IS
    'NULL means unlimited. Enforced by a conditional UPDATE, never by a read-then-write.';
COMMENT ON COLUMN gamebuddy.promo_code.redemption_count IS
    'Maintained atomically alongside the redemption row; not a cached COUNT(*).';
COMMENT ON COLUMN gamebuddy.promo_code.disabled_at IS
    'Reversible switch-off. A hard DELETE is the other, destructive option.';

-- ---------------------------------------------------------------------------
-- 2. Who it was addressed to
-- ---------------------------------------------------------------------------
--
-- No rows means the code is public: anybody who knows the string may redeem it. One or
-- more rows means only those accounts may. That is the whole difference between a
-- campaign code and a gift, and it needs no flag column to say which this is.

CREATE TABLE IF NOT EXISTS gamebuddy.promo_code_assignment (
    code_id     uuid         NOT NULL REFERENCES gamebuddy.promo_code(id) ON DELETE CASCADE,
    user_id     varchar(255) NOT NULL,
    emailed_at  timestamptz,
    notified_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (code_id, user_id)
);

-- The user's own screen asks "what is waiting for me", so the lookup runs this way round
-- as often as it runs by code.
CREATE INDEX IF NOT EXISTS idx_promo_assignment_user
    ON gamebuddy.promo_code_assignment USING btree (user_id);

COMMENT ON TABLE  gamebuddy.promo_code_assignment IS
    'Accounts a code was addressed to. No rows at all means the code is public.';
COMMENT ON COLUMN gamebuddy.promo_code_assignment.emailed_at IS
    'Set once the message left. Re-sending is skipped while this is non-null.';

-- ---------------------------------------------------------------------------
-- 3. Who took it
-- ---------------------------------------------------------------------------
--
-- The primary key is the whole point: (code, user) is what makes "once per account"
-- atomic, via INSERT ... ON CONFLICT DO NOTHING. Checking first and inserting after would
-- pay twice for two taps that raced -- which is exactly how the rewarded-ad grant was
-- measured paying twice, before it moved to this shape.
--
-- The amounts are snapshotted rather than read back through the code, so editing a code
-- later cannot rewrite what somebody was already given.

CREATE TABLE IF NOT EXISTS gamebuddy.promo_redemption (
    code_id         uuid         NOT NULL REFERENCES gamebuddy.promo_code(id) ON DELETE CASCADE,
    user_id         varchar(255) NOT NULL,
    kind            varchar(8)   NOT NULL,
    coin_amount     integer,
    gold_days       integer,
    gold_expires_at timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (code_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_promo_redemption_user
    ON gamebuddy.promo_redemption USING btree (user_id, created_at DESC);

COMMENT ON TABLE  gamebuddy.promo_redemption IS
    'One row per account per code. The primary key is the once-per-account guarantee.';
COMMENT ON COLUMN gamebuddy.promo_redemption.gold_expires_at IS
    'The membership expiry this redemption produced. Support evidence; not read by the app.';

-- ---------------------------------------------------------------------------
-- 4. updated_at, maintained where it cannot be bypassed
-- ---------------------------------------------------------------------------
--
-- Only promo_code is mutable. The child tables are inserted and deleted, never rewritten,
-- so an updated_at on them would be guaranteed to equal created_at forever -- worse than
-- not having one, because eventually somebody trusts it. (emailed_at and notified_at are
-- set once, from null, by a targeted UPDATE.)

DROP TRIGGER IF EXISTS set_updated_at ON gamebuddy.promo_code;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON gamebuddy.promo_code
    FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();

COMMIT;
