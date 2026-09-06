-- Verified Discord and Steam accounts on a profile.
--
-- Run after upgrade-2026-39-missions-and-badges.sql. Idempotent.
--
-- GameBuddy exists to get two people into the same lobby, and the last step of that is
-- always somewhere else: a Discord tag, a Steam friend request. Until now the app stopped
-- short of it, so the handoff happened in chat if it happened at all.
--
-- The handle is fetched from the provider, never typed. A text field would have been a
-- tenth of the work and would have let anybody claim to be anybody -- which is worse than
-- not having the feature, because a profile that displays an unverified handle is making
-- an assertion on the account holder's behalf. What is stored here was proved by an OAuth
-- round-trip (Discord) or an OpenID one (Steam), and both are free.
--
-- Two tables: the link itself, and the short-lived ticket that ties a browser callback
-- back to the account that started it.

BEGIN;

-- ---------------------------------------------------------------------------
-- The links
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.gamer_linked_account (
    user_id    character varying(255) NOT NULL,
    -- The LinkedProvider enum name. Text rather than a Postgres enum type for the same
    -- reason gamer_platform.platform is: adding a provider should be a deploy, not an
    -- ALTER TYPE coordinated with a running application.
    provider   character varying(16)  NOT NULL,
    -- The provider's own id -- a Discord snowflake, a SteamID64. This is the identity;
    -- the handle below is only its current label.
    external_id character varying(64)  NOT NULL,
    -- The display name as the provider gave it. Nullable on purpose: a handle that fails
    -- the profanity filter is not stored, and the profile then shows a verified badge with
    -- no name rather than a masked string that is not what the person is actually called.
    handle     character varying(255),
    -- PUBLIC or MATCHES. Defaults to the private end: a contact detail should not become
    -- visible to strangers because somebody did not find the setting.
    visibility character varying(16)  NOT NULL DEFAULT 'MATCHES',
    linked_at  timestamp(6) with time zone NOT NULL,
    -- When the handle was last read from the provider. People rename themselves; this is
    -- what the "refresh" button on the settings screen updates.
    handle_refreshed_at timestamp(6) with time zone,
    -- One link per provider per gamer. Linking a second Discord replaces the first.
    PRIMARY KEY (user_id, provider),
    CONSTRAINT fk_gamer_linked_account_gamer
        FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer (user_id) ON DELETE CASCADE
);

COMMENT ON TABLE gamebuddy.gamer_linked_account IS
    'Provider-verified Discord/Steam identities. The handle is fetched, never typed.';

-- The constraint that makes this worth verifying at all: one Discord account cannot be
-- claimed by two gamers. Without it the OAuth round-trip proves ownership and then lets
-- the same proof be reused on every account somebody controls, which is impersonation
-- with extra steps.
CREATE UNIQUE INDEX IF NOT EXISTS idx_gamer_linked_account_external
    ON gamebuddy.gamer_linked_account (provider, external_id);

-- ---------------------------------------------------------------------------
-- The link tickets
-- ---------------------------------------------------------------------------
--
-- Same shape and the same reasoning as password_reset_ticket: 32 random bytes handed out
-- once, stored only as a SHA-256 hash, single use, short lived.
--
-- It exists because of where the flow goes. The app hands the user to the system browser,
-- and the provider hands the browser back to an endpoint that has no session and no JWT --
-- the callback is public by necessity. The ticket is what identifies the account: it
-- travels in the URL as OAuth `state` (Discord) or inside `return_to` (Steam), it is
-- unguessable, and it is bound server-side to the gamer who asked for it. Putting the JWT
-- in that URL instead would spray a seven-day credential through browser history, the
-- provider's logs, and any referrer that happened to be attached.

CREATE TABLE IF NOT EXISTS gamebuddy.account_link_ticket (
    id         uuid NOT NULL,
    user_id    character varying(255) NOT NULL,
    provider   character varying(16)  NOT NULL,
    -- SHA-256 hex of the token handed to the client, which is never stored.
    token_hash character varying(64)  NOT NULL,
    used       boolean DEFAULT false  NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT account_link_ticket_pkey PRIMARY KEY (id),
    CONSTRAINT fk_account_link_ticket_gamer
        FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer (user_id) ON DELETE CASCADE
);

-- The lookup is always by hash: the callback presents a token, we hash and match.
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_link_ticket_hash
    ON gamebuddy.account_link_ticket (token_hash);

-- Minting one burns every other outstanding ticket for the same gamer and provider, so a
-- link started twice cannot be finished twice.
CREATE INDEX IF NOT EXISTS idx_account_link_ticket_user
    ON gamebuddy.account_link_ticket (user_id, provider);

COMMIT;

-- ---------------------------------------------------------------------------
-- No backfill
-- ---------------------------------------------------------------------------
--
-- Nothing to backfill and nothing that could be. Every existing account has no links, the
-- profile leaves the section out entirely when there are none, and there is no way to
-- infer somebody's Discord account from anything already stored.
