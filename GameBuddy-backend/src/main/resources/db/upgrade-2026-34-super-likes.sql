-- Remembers which likes were super likes, so the "liked you" list can say so.
--
-- Run after upgrade-2026-33-password-reset.sql. Idempotent.
--
-- A super like already cost the sender something and already sent a different push, but it
-- left no trace once delivered: `approved_matches` holds two ids and nothing else, and the
-- only other record — `gamer.last_decision_*` — keeps just the most recent swipe, for
-- rewind. So the screen that most needs to know, the one showing who likes you, had no way
-- to tell a super like from an ordinary one.
--
-- Its own table rather than a column on `approved_matches`, for the same reason declines got
-- one: that mapping is a JPA `@ManyToMany`, which cannot carry attributes. Adding a column
-- there would mean either restructuring the association every read path in the app depends
-- on, or writing the column behind Hibernate's back. A companion table costs one lookup on
-- one screen and leaves the association exactly as it was.
--
-- No backfill is possible or attempted. Super likes sent before this table existed were
-- never recorded anywhere, so they stay ordinary likes; the distinction starts now.
--
-- schema-baseline.sql is updated to match, so a fresh volume gets this table too.

BEGIN;

CREATE TABLE IF NOT EXISTS gamebuddy.super_likes (
    user_id character varying(255) NOT NULL,
    target_id character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT super_likes_pkey PRIMARY KEY (user_id, target_id)
);

COMMENT ON TABLE gamebuddy.super_likes IS
    'One row per super like sent. Deleted when the sender rewinds the swipe that created it.';

-- The read is always "who super liked me", so the target is the lookup key. The primary key
-- above leads with user_id and cannot serve it.
CREATE INDEX IF NOT EXISTS idx_super_like_target
    ON gamebuddy.super_likes (target_id);

COMMIT;
