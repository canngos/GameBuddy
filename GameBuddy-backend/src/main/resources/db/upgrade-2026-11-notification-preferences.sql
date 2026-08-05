-- Which notifications a gamer is willing to receive.
--
-- Run after upgrade-2026-10-notifications.sql. Idempotent.
--
-- reminders_enabled already existed and covered only the re-engagement nudges. These
-- three extend the same idea to everything else, because "you may interrupt me about
-- messages but not about community likes" is a distinction people genuinely want and
-- the alternative is that they switch the app's notifications off entirely at the
-- operating system — which costs the ones that mattered too.
--
-- All default to TRUE: somebody who has just agreed to notifications has agreed to the
-- app's notifications, and starting with everything off would make the permission
-- prompt a lie.

BEGIN;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS notify_messages BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS notify_social BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS notify_communities BOOLEAN NOT NULL DEFAULT TRUE;

COMMIT;
