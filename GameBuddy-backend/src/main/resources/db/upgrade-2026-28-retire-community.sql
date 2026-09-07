-- Retires the Community feature. Lobbies (upgrade-2026-27) are its replacement.
--
-- Apply after upgrade-2026-27-lobby.sql. Idempotent: safe to run twice.
--
-- The forum never connected to what this app is for — finding people to play with — and
-- its tab slot went to lobbies. This migration removes the storage. It is deliberately a
-- separate migration from the one that created lobbies, so the new feature could ship and
-- prove itself while this one waited.
--
-- What is destroyed here is user content: every community, post, comment and like.
-- There is no undo. The moderation queue survives — content_report stays, now owned by
-- the moderation module — because PROFILE reports never had anything to do with
-- communities beyond sharing their queue.

SET search_path TO gamebuddy;

BEGIN;

-- Open reports about content this migration is about to destroy cannot be actioned any
-- more; a moderator looking at them would see a row pointing at nothing. Dismissed by
-- the system, not deleted: the record that somebody complained is still a record.
UPDATE content_report
SET status = 'DISMISSED', reviewed_by = 'system', reviewed_at = now()
WHERE status = 'OPEN' AND content_type IN ('POST', 'COMMENT');

-- Children before parents, likes before what they liked.
DROP TABLE IF EXISTS comment_likes_join;
DROP TABLE IF EXISTS post_likes_join;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS community_members_join;
DROP TABLE IF EXISTS community;

-- The notification switch for a category that no longer exists. Not repurposed for
-- lobbies on purpose: it holds people's old "mute community noise" choice, which must
-- not silently apply to a different feature. Lobby kinds ride the SOCIAL and MESSAGES
-- switches instead.
ALTER TABLE gamer DROP COLUMN IF EXISTS notify_communities;

-- The weekly "write 2 posts" quest became "join a lobby" (same slot, same reward). The
-- baseline column follows the metric it baselines; zeroed because a posts count is not a
-- lobbies count, and the weekly rollover recomputes it anyway.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'gamebuddy' AND table_name = 'gamer'
                 AND column_name = 'quest_base_posts') THEN
        ALTER TABLE gamer RENAME COLUMN quest_base_posts TO quest_base_lobbies;
        UPDATE gamer SET quest_base_lobbies = 0;
    END IF;
END $$;

COMMIT;
