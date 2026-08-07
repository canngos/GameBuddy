-- Real explanations for the keyword catalogue.
--
-- Run after upgrade-2026-15-adults-only.sql. Idempotent — it sets values rather than
-- inserting rows, so running it twice changes nothing the second time.
--
-- Every description was the keyword repeated back at the reader: "chill" was described as
-- "chill". That was harmless while nothing displayed it, and the picker is about to.
--
-- Written in the second person and about *matching*, not about the word. Somebody picking
-- keywords is answering "who do I want to be put in front of", so the useful sentence is
-- what picking it will get them, not a dictionary definition. Kept to roughly seventy
-- characters: this renders in a small font on the second line of a list row.

BEGIN;

UPDATE gamebuddy.keywords SET description = v.description
FROM (VALUES
    ('achievement hunter', 'You go for the full list, however long it takes.'),
    ('aim training',       'You warm up before you play, and it shows.'),
    ('anime fan',          'Anime art, anime games, anime everything.'),
    ('builder',            'You would rather build the base than defend it.'),
    ('casual',             'You play to unwind. No pressure, no schedule.'),
    ('chaotic',            'Plans are optional. Something will happen.'),
    ('chill',              'Relaxed sessions, easy company, nobody shouting.'),
    ('clutch',             'You are calm when the round is on the line.'),
    ('co-op',              'You would rather beat the game with someone than alone.'),
    ('collector',          'Every skin, every mount, every card.'),
    ('combo practice',     'You spend real time in the training room.'),
    ('competitive',        'You are here to win, and you play like it.'),
    ('completionist',      'The map is not done until all of it is done.'),
    ('couch co-op',        'Two controllers, one sofa, same screen.'),
    ('decorator',          'Your base, island or house is the whole hobby.'),
    ('emulator user',      'Older systems, running on whatever you have now.'),
    ('endgame focused',    'The story is the tutorial. The endgame is the game.'),
    ('esports',            'You follow the pro scene and know the rosters.'),
    ('explorer',           'You take the side path before the main quest.'),
    ('grinder',            'Long, repetitive, satisfying. You do not mind the hours.'),
    ('guild leader',       'You organise people, and you are good at it.'),
    ('jumpscare enjoyer',  'Horror games, lights off, sound up.'),
    ('long sessions',      'When you sit down it is for the evening.'),
    ('loot goblin',        'You open every chest and pick up everything.'),
    ('lore nerd',          'You have read the item descriptions. All of them.'),
    ('min-maxer',          'Optimal build, optimal route, spreadsheet open.'),
    ('modder',             'You install mods, and probably write a few.'),
    ('no mic',             'You play without voice chat, and prefer it that way.'),
    ('no spoilers',        'Do not tell them anything. They are getting there.'),
    ('nostalgic',          'The games you grew up with still hit hardest.'),
    ('raider',             'Scheduled runs, full team, cleared bosses.'),
    ('ranked grinder',     'Climbing the ladder is the reason you log in.'),
    ('roleplayer',         'You stay in character, and enjoy people who do too.'),
    ('short sessions',     'Half an hour here and there, whenever you can.'),
    ('shotcaller',         'You make the calls so the team does not have to.'),
    ('sim racer',          'Wheel, pedals, and lap times you actually care about.'),
    ('social',             'The people are the reason you are here.'),
    ('solo player',        'You play alone, and you like it that way.'),
    ('speedrunner',        'You have a personal best and you are chasing it.'),
    ('squad player',       'Always in a party, never queuing alone.'),
    ('story-driven',       'You are here for the writing and the characters.'),
    ('streamer',           'You play with an audience watching.'),
    ('theorycrafter',      'You work out why it is good before you use it.'),
    ('tournament goer',    'You show up in person, or at least sign up.'),
    ('toxic-free',         'No abuse, no blaming. You leave lobbies that go that way.'),
    ('tryhard',            'Even the casual mode is played properly.'),
    ('voice chat',         'Mic on, talking through it, calls being made.'),
    ('waifu collector',    'Gacha pulls and a roster you are attached to.')
) AS v(keyword_name, description)
WHERE lower(gamebuddy.keywords.keyword_name) = v.keyword_name;

-- Anything left describing itself was not in the list above — a keyword added after this
-- migration was written. Reported rather than guessed at.
DO $$
DECLARE
    unwritten integer;
BEGIN
    SELECT count(*) INTO unwritten
    FROM gamebuddy.keywords
    WHERE description IS NULL OR lower(description) = lower(keyword_name);

    IF unwritten > 0 THEN
        RAISE WARNING 'Keyword(s) still described by their own name: %. They will render with no explanation.', unwritten;
    END IF;
END $$;

COMMIT;
