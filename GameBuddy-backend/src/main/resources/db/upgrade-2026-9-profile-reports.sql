-- A gamer's profile can be reported, not only what they wrote.
--
-- Run after upgrade-2026-8-badges.sql. Idempotent.
--
-- Reporting used to require the person to have posted something, or to have sent a
-- message worth reporting — which asks somebody to keep talking to the account they want
-- rid of. A profile picture or a username can be the entire problem.
--
-- The report lands in the same queue as posts and comments. Not because a profile is
-- community content, but because a moderator who has to check two queues checks one.

BEGIN;

ALTER TABLE gamebuddy.content_report
    DROP CONSTRAINT IF EXISTS content_report_content_type_check;

ALTER TABLE gamebuddy.content_report
    ADD CONSTRAINT content_report_content_type_check
    CHECK (content_type IN ('POST', 'COMMENT', 'PROFILE'));

COMMIT;

-- For a PROFILE report, content_id is the reported gamer's own id and equals author_id:
-- a profile is the one piece of content whose author is the content. That also means the
-- existing uq_report_once_per_reporter constraint gives "one report per gamer per person"
-- for free, with no extra index.
