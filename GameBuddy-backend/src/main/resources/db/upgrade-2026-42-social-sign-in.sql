-- Signing in with Google or Discord.
--
-- Run after upgrade-2026-41-remove-steam-links.sql. Idempotent.
--
-- Until now the only way into an account was an email address and a password, and the
-- closed-test feedback put that first: every extra field between somebody and their first
-- swipe is a share of them that never arrives. Google is one tap on Android; Discord is
-- already wired for profile linking and costs one more endpoint.
--
-- **Two tables, and neither is `gamer_linked_account`.** That table is a profile feature: a
-- visible handle, a visibility toggle, and an unlink button that costs nothing but a badge.
-- What lives here is a credential. It is never displayed, and removing the last one when no
-- password is set would lock somebody out of their own account -- so "unlink Discord" on the
-- profile screen must not be able to reach it. The same Discord snowflake may legitimately
-- appear in both: one says where to find somebody, the other says how they get in.
--
-- `gamer.pwd` is already nullable and stays that way -- no ALTER needed. An account created
-- through Google has no password at all, which is the honest representation: a random hash
-- nobody knows would make "does this account have a password" unanswerable, and the app has
-- to answer it to decide between "Change password" and "Set a password".

BEGIN;

-- ---------------------------------------------------------------------------
-- The identities
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_auth_identity (
    user_id       character varying(255) NOT NULL,
    -- The AuthProvider enum name. Text rather than a Postgres enum for the same reason
    -- gamer_linked_account.provider is: adding Apple should be a deploy, not an ALTER TYPE
    -- coordinated with a running application.
    provider      character varying(16)  NOT NULL,
    -- The provider's own subject: a Google `sub`, a Discord snowflake. This is the identity.
    -- Google's `sub` is stable per account per OAuth project, which is why the project's
    -- client id may never be swapped without a migration of these values.
    subject       character varying(255) NOT NULL,
    -- What the provider asserted the address was when the identity was attached. Audit only,
    -- never matched on afterwards: people change the email on a Google account, and the
    -- subject rather than the address is what identifies them.
    email_at_link character varying(255),
    created_at    timestamp(6) with time zone NOT NULL,
    -- Touched on every sign-in. Answers "is this how they actually get in" when somebody
    -- writes in having lost access to one of two methods.
    last_used_at  timestamp(6) with time zone,
    -- One identity per provider per gamer. Signing in with a second Google account does not
    -- add a row; it is either the same account or a different gamer.
    PRIMARY KEY (user_id, provider),
    CONSTRAINT fk_gamer_auth_identity_gamer
        FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer (user_id) ON DELETE CASCADE
);

COMMENT ON TABLE gamebuddy.gamer_auth_identity IS
    'Credentials: which external identities may sign in as this gamer. Never displayed.';

-- The constraint the whole feature rests on: one Google account cannot sign in as two
-- gamers. Without it, proving ownership once would let the same proof be reused against
-- every account somebody cared to create.
CREATE UNIQUE INDEX IF NOT EXISTS idx_gamer_auth_identity_subject
    ON gamebuddy.gamer_auth_identity (provider, subject);

-- ---------------------------------------------------------------------------
-- The Discord round trip
-- ---------------------------------------------------------------------------
--
-- Google needs nothing here: the native account sheet hands the app an ID token, which goes
-- straight to the backend in one authenticated request. Discord has no such sheet, so it
-- goes out through the system browser and comes back to a callback with no session on it --
-- and something has to carry the result of that across.
--
-- Two tickets are minted per sign-in, not one. The first is the OAuth `state`, and by the
-- time Discord redirects it has been through their logs and the browser's history. The
-- second is minted at the callback, after the exchange, and is the only one that can be
-- traded for a session. So the value that travelled is never the value that grants.

CREATE TABLE IF NOT EXISTS gamebuddy.social_login_ticket (
    id             uuid PRIMARY KEY,
    provider       character varying(16) NOT NULL,
    -- SHA-256 of the token, hex. The token itself is never stored, exactly as for
    -- account_link_ticket and password_reset_ticket.
    -- varchar rather than char: the entity maps a String of length 64, and Hibernate's
    -- schema validation refuses a bpchar against it. `account_link_ticket` next door is
    -- varchar(64) for the same reason.
    token_hash     character varying(64) NOT NULL UNIQUE,
    -- Null on the outbound ticket and filled on the inbound one: the first exists only to
    -- prove a callback belongs to a flow this server started.
    subject        character varying(255),
    email          character varying(255),
    email_verified boolean,
    display_name   character varying(255),
    used           boolean NOT NULL DEFAULT false,
    created_at     timestamp(6) with time zone NOT NULL,
    expires_at     timestamp(6) with time zone NOT NULL
);

COMMENT ON TABLE gamebuddy.social_login_ticket IS
    'Short-lived, single-use tickets carrying a Discord sign-in across the browser round trip.';

-- The sweep runs on an index rather than a sequential scan; every row here is unusable ten
-- minutes after it is written.
CREATE INDEX IF NOT EXISTS idx_social_login_ticket_expires
    ON gamebuddy.social_login_ticket (expires_at);

COMMIT;
