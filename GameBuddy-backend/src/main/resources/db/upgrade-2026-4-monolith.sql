-- GameBuddy, five services to one.
--
-- Run once, after every service-level upgrade script has been applied. Idempotent, but
-- read the section on the chat migration before running it twice.
--
-- Three things happen here:
--   1. everything moves into one schema
--   2. chat moves from MongoDB into Postgres
--   3. the duplicate community membership mapping is repaired
--
-- Take a backup first. This moves tables between schemas, which is fast but not something
-- you want to discover was wrong afterwards.

BEGIN;

-- --------------------------------------------------------------------------
-- 1. One schema
-- --------------------------------------------------------------------------
-- schauth, schcomm, schnotif and schappl isolated nothing: all five services connected to
-- the same database with the same credentials. What the split did achieve was letting the
-- same table resolve differently depending on which service asked — avatars and
-- achievements were schappl.* to three services and schauth.* to a fourth, so an avatar
-- bought through one was invisible to the others.

CREATE SCHEMA IF NOT EXISTS gamebuddy;

-- Moved rather than copied: ALTER TABLE ... SET SCHEMA is a catalogue update, so this is
-- fast regardless of table size and keeps every index, constraint and foreign key intact.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname IN ('schauth', 'schcomm', 'schnotif', 'schappl')
    LOOP
        -- A table already present in the target means a previous run got this far.
        IF NOT EXISTS (
            SELECT 1 FROM pg_tables WHERE schemaname = 'gamebuddy' AND tablename = r.tablename
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I SET SCHEMA gamebuddy', r.schemaname, r.tablename);
        END IF;
    END LOOP;
END $$;

-- --------------------------------------------------------------------------
-- 2. Community membership
-- --------------------------------------------------------------------------
-- Gamer.joinedCommunities and Community.members each declared their own @JoinTable over
-- community_members_join, neither with mappedBy, so Hibernate treated them as two
-- unrelated relationships and the service wrote to both. Joining a community issued two
-- inserts of the same logical row.
--
-- Community.members is the single owning side now. Any duplicate rows the old code left
-- behind are collapsed, and the constraint that should always have existed is added so it
-- cannot happen again.

DELETE FROM gamebuddy.community_members_join a
    USING gamebuddy.community_members_join b
    WHERE a.ctid < b.ctid
      AND a.community_id = b.community_id
      AND a.user_id = b.user_id;

-- Added only if the table has no primary key at all. Checking for a constraint by name
-- is not enough: whichever tool created the table chose its own name, so a hardcoded
-- DROP ... IF EXISTS silently matches nothing and the ADD then fails with "multiple
-- primary keys are not allowed".
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'gamebuddy'
          AND t.relname = 'community_members_join'
          AND c.contype = 'p'
    ) THEN
        ALTER TABLE gamebuddy.community_members_join
            ADD CONSTRAINT pk_community_members_join PRIMARY KEY (community_id, user_id);
    END IF;
END $$;

-- --------------------------------------------------------------------------
-- 3. Chat
-- --------------------------------------------------------------------------
-- MongoDB held exactly two collections, chatrooms and messages, and both were entirely
-- relational: every field scalar, both indexes translating one for one. The second
-- datastore bought nothing and cost a managed service in every deployment.
--
-- Three changes come with the move:
--
--   * One room row per conversation. Mongo wrote two documents per room, one per
--     direction, both carrying the same chatId — so every room existed twice and the
--     inbox only ever found the copy where the caller was the "sender".
--
--   * Read state is a watermark per participant, not a status on every message. The old
--     status column was indexed and changed twice per message, so opening a fifty message
--     thread issued fifty row updates and fifty index updates.
--
--   * Bodies are encrypted by the application before they are written. A leaked dump or a
--     stolen disk yields ciphertext. This is not end to end: the server holds the key, so
--     a reported message can still be read by a moderator, which is what makes the
--     reporting flow mean anything on a product that pairs strangers.

CREATE TABLE IF NOT EXISTS gamebuddy.chat_room (
    id         UUID         PRIMARY KEY,
    -- min(userA,userB) || '|' || max(userA,userB). Order independent by construction, so
    -- "the room for these two people" is one unique index read whoever is asking.
    pair_key   VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- Same conditional shape as the primary key above: add it only if the table has no
-- unique constraint on pair_key, rather than dropping one by a name that may not match.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'gamebuddy'
          AND t.relname = 'chat_room'
          AND c.contype = 'u'
    ) THEN
        ALTER TABLE gamebuddy.chat_room
            ADD CONSTRAINT uk_chat_room_pair UNIQUE (pair_key);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS gamebuddy.chat_participant (
    room_id      UUID         NOT NULL REFERENCES gamebuddy.chat_room(id) ON DELETE CASCADE,
    user_id      VARCHAR(255) NOT NULL,
    -- NULL until the conversation is first opened, which means everything is unread.
    last_read_at TIMESTAMPTZ,
    PRIMARY KEY (room_id, user_id)
);

-- The inbox reads every room a gamer belongs to.
CREATE INDEX IF NOT EXISTS idx_chat_participant_user
    ON gamebuddy.chat_participant (user_id);

CREATE TABLE IF NOT EXISTS gamebuddy.chat_message (
    id          UUID         PRIMARY KEY,
    room_id     UUID         NOT NULL REFERENCES gamebuddy.chat_room(id) ON DELETE CASCADE,
    sender_id   VARCHAR(255) NOT NULL,
    -- AES-GCM ciphertext. Never plaintext, at any point.
    body        BYTEA        NOT NULL,
    -- Per message initialisation vector. Reusing one under a key destroys GCM entirely.
    nonce       BYTEA        NOT NULL,
    -- Which key encrypted this row, so the key can be rotated without a rewrite.
    key_version SMALLINT     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    reported_at TIMESTAMPTZ
);

-- Serves both reads this table ever gets: a conversation, and the latest message per room
-- for the inbox preview.
CREATE INDEX IF NOT EXISTS idx_chat_message_room_time
    ON gamebuddy.chat_message (room_id, created_at DESC, id DESC);

-- Partial: reports are rare, so indexing the nulls would be almost the whole table for
-- the sake of a screen only moderators open.
CREATE INDEX IF NOT EXISTS idx_chat_message_reported
    ON gamebuddy.chat_message (reported_at)
    WHERE reported_at IS NOT NULL;

-- --------------------------------------------------------------------------
-- Migrating existing chat history
-- --------------------------------------------------------------------------
-- Not done here, and deliberately so. The bodies in MongoDB are plaintext and the new
-- column is ciphertext, so moving them needs the encryption key — which means application
-- code, not SQL. There is no correct way to write it in this file.
--
-- If there is history worth keeping, run a one-off job that reads each Mongo document,
-- calls MessageCipher.encrypt on the body, and inserts the row. If this is a fresh launch,
-- there is nothing to move and MongoDB can simply be decommissioned.
--
-- Either way, do not point the application at these tables while a migration job is only
-- partway through: the inbox would show half a history and look like data loss.

-- --------------------------------------------------------------------------
-- After this runs
-- --------------------------------------------------------------------------
-- Set DB_SCHEMA=gamebuddy (it is the default), then start the monolith with
-- ddl-auto: validate and let it fail loudly if anything here did not land.
--
-- Once it starts clean, the old schemas should be empty and can be dropped:
--
--   DROP SCHEMA schauth  CASCADE;
--   DROP SCHEMA schcomm  CASCADE;
--   DROP SCHEMA schnotif CASCADE;
--   DROP SCHEMA schappl  CASCADE;
--
-- Left as a manual step on purpose. CASCADE on the wrong schema is not recoverable, and
-- there is no reason to automate something run exactly once.

COMMIT;
