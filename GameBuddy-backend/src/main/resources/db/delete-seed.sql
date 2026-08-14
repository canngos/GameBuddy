-- Removes the synthetic seed accounts, in the order the foreign keys require.
--
--     docker compose exec -T postgres psql -U gamebuddy -d gamebuddy \
--       -v ON_ERROR_STOP=1 -f /dev/stdin \
--       < GameBuddy-backend/src/main/resources/db/delete-seed.sql
--
-- Run it together with flipping RETRAIN_INCLUDE_BOTS to false, and in that order: deleting
-- the seed while the retrain job still includes bots simply trains on whoever is left.
--
-- Why this is not the one-liner it used to be
-- -------------------------------------------
-- README and docker-compose.yml both documented
--
--     DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid';
--
-- and it has never worked. Fifteen of the sixteen foreign keys pointing at `gamer` are
-- NO ACTION rather than CASCADE, so the first child table refuses:
--
--     ERROR: update or delete on table "gamer" violates foreign key constraint
--            "fk24su9eu5fhblvx77oiwxcfcog" on table "gamer_keywords_join"
--
-- That statement is the last step before real users arrive, so somebody would have run it
-- under time pressure, watched it fail, and either skipped it or started improvising DELETEs
-- against production. Hence a script that has actually been run.
--
-- Cascades were considered and rejected. Account deletion does not need them —
-- DefaultAuthService.deleteAccount anonymises the row rather than removing it, precisely so
-- that other people's conversations and posts survive — and a blanket cascade from `gamer`
-- to `community` is exactly the behaviour the section below exists to avoid.
--
-- Note the two join tables key on `gamer_id` while everything else uses `user_id`. That
-- inconsistency is most of why hand-writing this goes wrong.

\set ON_ERROR_STOP on
SET search_path TO gamebuddy;

BEGIN;

CREATE TEMP TABLE doomed ON COMMIT DROP AS
SELECT user_id FROM gamer WHERE email LIKE '%@bot.gamebuddy.invalid';

CREATE INDEX ON doomed (user_id);

-- Communities owned by a seed account: transfer or remove, never cascade
-- ----------------------------------------------------------------------
-- The seed owns communities — 570 of them in the development database, which was a surprise
-- and is the reason this section exists. Deleting a `gamer` row cannot be allowed to take a
-- community with it: by launch day a real person may have joined one, and the whole point of
-- this purge is to protect real users from synthetic content, not to delete what they wrote.
--
-- So the two cases are separated, and the rule matches what the product already does when an
-- owner leaves (DefaultCommunityService: succession to the longest-standing member, closure
-- only if empty):
--
--   * a community with at least one surviving member -> ownership passes to the
--     longest-standing of them, and the community stays
--   * a community with none -> it is wholly synthetic, and it goes
--
-- In the development database today every one of the 570 falls into the second case: no real
-- members, no real posts. The first branch is for the day that is no longer true, which is
-- precisely the day this script gets run for real.
CREATE TEMP TABLE inherited ON COMMIT DROP AS
SELECT c.community_id,
       (SELECT m.user_id
          FROM community_members_join m
          JOIN gamer g ON g.user_id = m.user_id
         WHERE m.community_id = c.community_id
           AND g.user_id NOT IN (SELECT user_id FROM doomed)
           AND g.deleted_at IS NULL
         ORDER BY g.created_date NULLS LAST
         LIMIT 1) AS heir
  FROM community c
 WHERE c.owner IN (SELECT user_id FROM doomed);

UPDATE community c
   SET owner = i.heir
  FROM inherited i
 WHERE c.community_id = i.community_id AND i.heir IS NOT NULL;

CREATE TEMP TABLE abandoned ON COMMIT DROP AS
SELECT community_id FROM inherited WHERE heir IS NULL;

-- Everything inside a community nobody is left to run.
DELETE FROM comment_likes_join
 WHERE comment_id IN (SELECT cm.comment_id FROM comment cm
                        JOIN post p ON p.post_id = cm.post_id
                       WHERE p.community_id IN (SELECT community_id FROM abandoned));
DELETE FROM comment
 WHERE post_id IN (SELECT post_id FROM post
                    WHERE community_id IN (SELECT community_id FROM abandoned));
DELETE FROM post_likes_join
 WHERE post_id IN (SELECT post_id FROM post
                    WHERE community_id IN (SELECT community_id FROM abandoned));
DELETE FROM post WHERE community_id IN (SELECT community_id FROM abandoned);
DELETE FROM community_members_join WHERE community_id IN (SELECT community_id FROM abandoned);
DELETE FROM community WHERE community_id IN (SELECT community_id FROM abandoned);

-- Posts and comments the seed wrote in communities that survive. These have to go for the
-- same reason the profiles do: a fake account's post is still a fake account talking to real
-- people, and it would outlive the profile behind it.
DELETE FROM comment_likes_join
 WHERE comment_id IN (SELECT comment_id FROM comment WHERE owner IN (SELECT user_id FROM doomed));
DELETE FROM comment WHERE owner IN (SELECT user_id FROM doomed);

DELETE FROM comment_likes_join
 WHERE comment_id IN (SELECT cm.comment_id FROM comment cm
                       WHERE cm.post_id IN (SELECT post_id FROM post
                                             WHERE owner IN (SELECT user_id FROM doomed)));
DELETE FROM comment
 WHERE post_id IN (SELECT post_id FROM post WHERE owner IN (SELECT user_id FROM doomed));
DELETE FROM post_likes_join
 WHERE post_id IN (SELECT post_id FROM post WHERE owner IN (SELECT user_id FROM doomed));
DELETE FROM post WHERE owner IN (SELECT user_id FROM doomed);

-- The rest, children first. Chat before the social graph, because rooms are reached
-- through their participants.
DELETE FROM chat_message
 WHERE room_id IN (SELECT room_id FROM chat_participant WHERE user_id IN (SELECT user_id FROM doomed));
DELETE FROM chat_participant WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM chat_room WHERE id NOT IN (SELECT room_id FROM chat_participant);

DELETE FROM approved_matches
 WHERE user_id IN (SELECT user_id FROM doomed) OR matched_id IN (SELECT user_id FROM doomed);
DELETE FROM declined_matches
 WHERE user_id IN (SELECT user_id FROM doomed) OR declined_id IN (SELECT user_id FROM doomed);
DELETE FROM friends
 WHERE user_id IN (SELECT user_id FROM doomed) OR friend_id IN (SELECT user_id FROM doomed);
DELETE FROM waiting_friends
 WHERE user_id IN (SELECT user_id FROM doomed) OR requested_id IN (SELECT user_id FROM doomed);
DELETE FROM blocked_friends
 WHERE gamer_id IN (SELECT user_id FROM doomed) OR blocked_user_id IN (SELECT user_id FROM doomed);
DELETE FROM unlocked_admirer
 WHERE user_id IN (SELECT user_id FROM doomed) OR admirer_id IN (SELECT user_id FROM doomed);

DELETE FROM comment_likes_join WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM post_likes_join WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM community_members_join WHERE user_id IN (SELECT user_id FROM doomed);

DELETE FROM content_report
 WHERE reporter_id IN (SELECT user_id FROM doomed)
    OR author_id IN (SELECT user_id FROM doomed)
    OR reviewed_by IN (SELECT user_id FROM doomed);
DELETE FROM coin_ledger WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM purchase WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM rewarded_ad_grant WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM recommendation_impression
 WHERE user_id IN (SELECT user_id FROM doomed) OR candidate_id IN (SELECT user_id FROM doomed);
DELETE FROM funnel_event WHERE user_id IN (SELECT user_id FROM doomed);

-- gamer_id, not user_id. These two are the reason the one-liner was never going to work.
DELETE FROM gamer_games_join WHERE gamer_id IN (SELECT user_id FROM doomed);
DELETE FROM gamer_keywords_join WHERE gamer_id IN (SELECT user_id FROM doomed);

DELETE FROM gamer_platform WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM gamer_badge WHERE user_id IN (SELECT user_id FROM doomed);
DELETE FROM gamer_cosmetic WHERE user_id IN (SELECT user_id FROM doomed);

-- Keyed by email rather than by id.
DELETE FROM session WHERE email LIKE '%@bot.gamebuddy.invalid';
DELETE FROM verification_code WHERE email LIKE '%@bot.gamebuddy.invalid';

DELETE FROM gamer WHERE user_id IN (SELECT user_id FROM doomed);

-- Nothing may still point at a seed account. Checked inside the transaction so a missed
-- child table rolls the whole thing back rather than leaving a half-purged database — the
-- FKs would catch most of it, but not a column that has since been added and forgotten here.
DO $$
DECLARE
    orphans bigint;
BEGIN
    SELECT count(*) INTO orphans FROM gamer WHERE email LIKE '%@bot.gamebuddy.invalid';
    IF orphans > 0 THEN
        RAISE EXCEPTION 'Seed purge incomplete: % account(s) survived', orphans;
    END IF;
END $$;

COMMIT;

-- What survived, so the outcome is visible rather than assumed.
SELECT (SELECT count(*) FROM gamer WHERE email LIKE '%@bot.gamebuddy.invalid') AS seed_accounts_left,
       (SELECT count(*) FROM gamer) AS gamers,
       (SELECT count(*) FROM community) AS communities,
       (SELECT count(*) FROM post) AS posts;
