-- Every table records when its rows were created, and mutable tables record when they
-- last changed.
--
-- Run after upgrade-2026-16-keyword-descriptions.sql. Idempotent.
--
-- Seventeen of thirty tables carried no timestamp at all. "When did these two become
-- friends", "when did this account join that community", "when was this game added" were
-- all unanswerable, which matters for support, for moderation, and for any analytics that
-- is not just a count.
--
-- Three deliberate departures from "created_at and updated_at on everything":
--
-- 1. Tables that already have a well-named creation time keep it. `declined_at`,
--    `purchased_at`, `served_at`, `earned_at` and `acquired_at` each say what the event
--    was; `created_at` says only that a row appeared. Adding a second column that always
--    equals the first would be duplication, and renaming them would lose meaning.
--
-- 2. Join tables get `created_at` and no `updated_at`. A row in `friends` is inserted or
--    deleted; there is nothing to update, and a column that is guaranteed to equal
--    `created_at` forever is worse than no column — someone will eventually trust it.
--
-- 3. `recommendation_impression` gets neither. It already has `served_at`, and it is by a
--    wide margin the fastest-growing table here — a few thousand active gamers produce
--    millions of rows a month. Sixteen bytes a row for a value nothing reads is a real
--    cost paid for tidiness.
--
-- `updated_at` is maintained by a trigger rather than by Hibernate's @UpdateTimestamp.
-- The trigger cannot be bypassed: it fires for the application, for a migration, and for
-- somebody fixing data by hand in psql. @UpdateTimestamp only fires when the write goes
-- through an entity, and this schema is written by native queries and bulk updates too.

BEGIN;

-- ---------------------------------------------------------------------------
-- The trigger that maintains updated_at
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION gamebuddy.set_updated_at()
RETURNS trigger AS $$
BEGIN
    -- Only when something actually changed. An UPDATE that writes the same values is not
    -- a modification, and treating it as one makes "last changed" mean "last touched by
    -- any job that happened to rewrite the row".
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at = now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- Join tables: created_at only. See note 2 above.
-- ---------------------------------------------------------------------------
--
-- A default rather than a Java field. These are Hibernate @ManyToMany join tables with no
-- entity behind them, so there is nothing to hang a @CreationTimestamp on; giving them one
-- would mean promoting each to an @Entity with an @EmbeddedId, which is a domain-model
-- rewrite to record a timestamp. The database fills the column, Hibernate keeps writing
-- the two id columns it knows about, and neither has to learn anything about the other.
--
-- Existing rows get now(), which is a lie about when they were created. It is a lie the
-- alternatives do not improve on: NULL would need every reader to handle it forever, and
-- there is no record anywhere of when these rows actually appeared.

ALTER TABLE gamebuddy.approved_matches       ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.blocked_friends        ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.comment_likes_join     ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.community_members_join ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.friends                ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.gamer_games_join       ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.gamer_keywords_join    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.post_likes_join        ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.waiting_friends        ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

-- ---------------------------------------------------------------------------
-- Catalogue tables: both. Edited by the moderator, so both times are real.
-- ---------------------------------------------------------------------------

ALTER TABLE gamebuddy.avatars ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.avatars ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.games   ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.games   ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

-- ---------------------------------------------------------------------------
-- Tables with a creation time that are mutated: add updated_at.
-- ---------------------------------------------------------------------------
--
-- chat_message and recommendation_impression are deliberately absent: both are
-- append-only and both are large.

ALTER TABLE gamebuddy.chat_participant     ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.chat_participant     ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.chat_room            ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.comment              ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.community            ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.content_report       ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.cosmetic             ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.gamer_badge          ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.gamer_cosmetic       ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.keywords             ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.notification_outbox  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.notifications        ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.post                 ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.purchase             ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.session              ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE gamebuddy.verification_code    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

-- ---------------------------------------------------------------------------
-- Attach the trigger to every table that now has updated_at
-- ---------------------------------------------------------------------------
--
-- Driven off the catalogue rather than listed by hand, so a table that gains the column
-- later cannot be given the column and forgotten for the trigger. `gamer` is excluded:
-- Hibernate already maintains its last_modified_date, and two mechanisms writing the same
-- idea in one row is how they end up disagreeing.

DO $$
DECLARE
    target record;
BEGIN
    FOR target IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'gamebuddy'
          AND c.column_name = 'updated_at'
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS set_updated_at ON gamebuddy.%I', target.table_name);
        EXECUTE format(
            'CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.%I '
            'FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at()', target.table_name);
    END LOOP;
END $$;

COMMIT;
