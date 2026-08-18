-- Forgot-password: purpose-scoped codes, hashed codes, and reset tickets.
--
-- Run after upgrade-2026-32-lobby-boost.sql. Idempotent.
--
-- Three changes, and the first two are what make a six-digit code safe to reset a password
-- with.
--
-- 1. `purpose`. A code carried no indication of what it was for, so a code mailed with the
--    "Password Reset" wording was redeemable at POST /auth/verify — which signs the account
--    in and revokes its tokens. That is a passwordless login by another name. Scoping means
--    a reset code only resets and a signup code only verifies.
--
-- 2. `code_hash` replaces `code`. The code was stored in plaintext, so anyone who could read
--    this table could take over any account mid-flow. It is now a bcrypt hash: a fast digest
--    would be useless here, because six digits is a million values and an attacker with the
--    table would exhaust them in seconds. Bcrypt's cost is the whole point.
--
--    Existing rows are deleted rather than migrated. They cannot be hashed — the plaintext is
--    the only copy and re-hashing it would defeat the exercise — and they are 15-minute codes,
--    so the worst case is that somebody mid-signup asks for a new one.
--
-- 3. `password_reset_ticket`. What the app holds between "your code was correct" and "here is
--    my new password". It is a 32-byte random token stored as a SHA-256 hash, the same shape
--    as `session.token_hash`: unlike the code it has enough entropy that a fast digest is the
--    right choice.
--
-- schema-baseline.sql is updated to match, so a fresh volume does not come up with a
-- `code integer NOT NULL` column that nothing writes to any more.

BEGIN;

-- Nothing here is worth keeping: every row is a code that expires within 15 minutes, and the
-- new column is NOT NULL with no sensible backfill.
DELETE FROM gamebuddy.verification_code;

ALTER TABLE gamebuddy.verification_code
    DROP COLUMN IF EXISTS code,
    ADD COLUMN IF NOT EXISTS code_hash character varying(60) NOT NULL,
    ADD COLUMN IF NOT EXISTS purpose character varying(20) NOT NULL DEFAULT 'REGISTRATION';

COMMENT ON COLUMN gamebuddy.verification_code.code_hash IS
    'bcrypt of the six-digit code. Never the code itself — six digits is a million values.';
COMMENT ON COLUMN gamebuddy.verification_code.purpose IS
    'REGISTRATION or PASSWORD_RESET. A code is only redeemable by the flow it was issued for.';

CREATE TABLE IF NOT EXISTS gamebuddy.password_reset_ticket (
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    -- SHA-256 hex of the 32 random bytes handed to the client, which are never stored.
    token_hash character varying(64) NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT password_reset_ticket_pkey PRIMARY KEY (id)
);

-- The lookup is always by hash: the client presents the token, we hash and match.
CREATE UNIQUE INDEX IF NOT EXISTS idx_password_reset_ticket_hash
    ON gamebuddy.password_reset_ticket (token_hash);

-- Redeeming one burns every other outstanding ticket for the address, so this is the
-- second access path.
CREATE INDEX IF NOT EXISTS idx_password_reset_ticket_email
    ON gamebuddy.password_reset_ticket (email);

COMMIT;
