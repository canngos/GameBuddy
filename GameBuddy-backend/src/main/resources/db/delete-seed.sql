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
-- and it has never worked. Most of the foreign keys pointing at `gamer` are NO ACTION
-- rather than CASCADE, so the first child table refuses:
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
-- that other people's conversations survive.
--
-- The community section this file used to carry (570 bot-owned communities, ownership
-- succession, abandoned-community teardown) went with the community tables in
-- upgrade-2026-28. Lobbies took its place below, with a simpler rule: bots cannot own
-- lobbies (creation is Gold-gated), but a bot that somehow sits in one is just a row.
--
-- Note the two join tables key on `gamer_id` while everything else uses `user_id`. That
-- inconsistency is most of why hand-writing this goes wrong.

\set ON_ERROR_STOP on
SET search_path TO gamebuddy;

BEGIN;

CREATE TEMP TABLE doomed ON COMMIT DROP AS
SELECT user_id FROM gamer WHERE email LIKE '%@bot.gamebuddy.invalid';

CREATE INDEX ON doomed (user_id);

-- Lobby traffic: messages before members, and a doomed owner's whole lobby goes — its
-- members' rows included, because a membership in a deleted lobby points at nothing.
DELETE FROM lobby_message
 WHERE sender_id IN (SELECT user_id FROM doomed)
    OR lobby_id IN (SELECT id FROM lobby WHERE owner_id IN (SELECT user_id FROM doomed));
DELETE FROM lobby_member
 WHERE user_id IN (SELECT user_id FROM doomed)
    OR lobby_id IN (SELECT id FROM lobby WHERE owner_id IN (SELECT user_id FROM doomed));
DELETE FROM lobby WHERE owner_id IN (SELECT user_id FROM doomed);

-- Chat before the social graph, because rooms are reached through their participants.
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
       (SELECT count(*) FROM lobby) AS lobbies;
