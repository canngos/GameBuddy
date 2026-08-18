--
-- Local seed data: the game and keyword catalogue, avatars and cosmetics.
--
-- Generated from gamebuddy_model/catalogue.py. Do not edit by hand -- see the README,
-- "Regenerating the seed". The names must match what the recommender was trained on,
-- or profiles built from this catalogue produce features the model has never seen.
--
-- Reference data only. No gamers: accounts come from the signup flow, which is the
-- thing worth exercising.
--

SET search_path TO gamebuddy;

BEGIN;

-- Games. ON CONFLICT so re-running is harmless; the ids are stable across runs.
INSERT INTO games (game_id, game_name, category, description, game_icon, avg_vote, is_popular) VALUES
  ('fa23599f-f1a6-5643-b813-72557a353a8f', 'ARC Raiders', 'FPS', 'ARC Raiders - seeded for local development', NULL, 4.0, true),
  ('f72572de-81ec-51d5-9714-9714e73792c4', 'Ark: Survival Ascended', 'Survival', 'Ark: Survival Ascended - seeded for local development', NULL, 4.0, true),
  ('d0c0e651-96c6-56de-8951-89d33b90b010', 'Age of Empires II: The Age of Kings', 'Strategy', 'Age of Empires II: The Age of Kings - seeded for local development', NULL, 4.0, true),
  ('6830a4dd-09ad-5daa-86b3-3ca6e294db35', 'Age of Empires IV', 'Strategy', 'Age of Empires IV - seeded for local development', NULL, 4.0, true),
  ('15afdac6-0dd0-5d71-89fb-d30f8ce4ba36', 'Animal Crossing: New Horizons', 'Simulation', 'Animal Crossing: New Horizons - seeded for local development', NULL, 4.0, true),
  ('240b4923-d681-5af9-ac50-023d03c7a358', 'Apex Legends', 'FPS', 'Apex Legends - seeded for local development', NULL, 4.0, true),
  ('374b9676-d53e-53b4-afc5-833e47825c9d', 'Atelier Ryza 3: Alchemist of the End & the Secret Key', 'JRPG', 'Atelier Ryza 3: Alchemist of the End & the Secret Key - seeded for local development', NULL, 4.0, true),
  ('0914ab18-766b-5d00-a3a7-97f51f53704b', 'Baba Is You', 'Puzzle', 'Baba Is You - seeded for local development', NULL, 4.0, true),
  ('f2b949cd-65c4-5570-941d-151e2963739d', 'Balatro', 'Roguelike', 'Balatro - seeded for local development', NULL, 4.0, true),
  ('aea289ab-02a3-566e-b5e1-c8639fe92f61', 'Baldur''s Gate III', 'RPG', 'Baldur''s Gate III - seeded for local development', NULL, 4.0, true),
  ('97e85813-54d0-53b5-b087-c30406052858', 'Battlefield 6', 'FPS', 'Battlefield 6 - seeded for local development', NULL, 4.0, true),
  ('9d6d9af4-8949-5307-9f60-96cdb1bcb761', 'Black Desert', 'MMO', 'Black Desert - seeded for local development', NULL, 4.0, true),
  ('707134d9-9513-5c40-9003-4057e7aa82f6', 'Call of Duty: Warzone', 'FPS', 'Call of Duty: Warzone - seeded for local development', NULL, 4.0, true),
  ('908748fc-f8dd-54d9-98e5-e1bb3b5a87cc', 'Celeste', 'Platformer', 'Celeste - seeded for local development', NULL, 4.0, true),
  ('594f55f6-1c14-52de-bb7d-4262fc19c04a', 'Chrono Trigger', 'JRPG', 'Chrono Trigger - seeded for local development', NULL, 4.0, true),
  ('b95f34d5-5c31-5f3b-b20d-59c938885d11', 'Cities: Skylines II', 'Simulation', 'Cities: Skylines II - seeded for local development', NULL, 4.0, true),
  ('02194d97-e861-5427-8543-2b7cbe2ee152', 'Sid Meier''s Civilization VII', 'Strategy', 'Sid Meier''s Civilization VII - seeded for local development', NULL, 4.0, true),
  ('74cb6ad6-fdaa-5f57-9406-6cde1a7448da', 'Clair Obscur: Expedition 33', 'RPG', 'Clair Obscur: Expedition 33 - seeded for local development', NULL, 4.0, true),
  ('48413cb6-1d47-54ac-a5bc-a4b02162244a', 'Content Warning', 'Horror', 'Content Warning - seeded for local development', NULL, 4.0, true),
  ('64193fed-53ab-596c-9418-37e3acebd0a8', 'Coral Island', 'Simulation', 'Coral Island - seeded for local development', NULL, 4.0, true),
  ('e387d09b-9ea5-5107-a81f-0e55eaba812c', 'Counter-Strike 2', 'FPS', 'Counter-Strike 2 - seeded for local development', NULL, 4.0, false),
  ('cfd505d4-f197-506b-aee0-b085bbec84d3', 'Crusader Kings III', 'Strategy', 'Crusader Kings III - seeded for local development', NULL, 4.0, false),
  ('b0aadb8d-cd54-552a-bd91-80f6158838c1', 'Cyberpunk 2077', 'RPG', 'Cyberpunk 2077 - seeded for local development', NULL, 4.0, false),
  ('0dbf62a2-4f56-566f-9dda-666c72d480ad', 'Dave the Diver', 'Adventure', 'Dave the Diver - seeded for local development', NULL, 4.0, false),
  ('7ed05f3a-b981-5719-b382-dfb828da31e8', 'Dead by Daylight', 'Horror', 'Dead by Daylight - seeded for local development', NULL, 4.0, false),
  ('81c6b58b-e452-5e49-9ab8-20f45eae9fca', 'Disco Elysium', 'RPG', 'Disco Elysium - seeded for local development', NULL, 4.0, false),
  ('5716e40c-d03d-5a9e-92b7-8458f0473c45', 'Doom', 'FPS', 'Doom - seeded for local development', NULL, 4.0, false),
  ('69b6f386-83ff-5607-bd99-141b85a053b4', 'Dota 2', 'MOBA', 'Dota 2 - seeded for local development', NULL, 4.0, false),
  ('776ec5ba-bd6d-58e2-a7af-60944b5831c5', 'Dragon Quest XI S', 'JRPG', 'Dragon Quest XI S - seeded for local development', NULL, 4.0, false),
  ('cedf4702-05ee-53f4-8be2-8ad5d1c7e85f', 'EA Sports FC 26', 'Sports', 'EA Sports FC 26 - seeded for local development', NULL, 4.0, false),
  ('8d1b8dc6-c02f-57cc-98a0-133ac575c122', 'Elden Ring', 'RPG', 'Elden Ring - seeded for local development', NULL, 4.0, false),
  ('5b43da01-8b6c-5733-a7d7-5887fd764ede', 'Enshrouded', 'Survival', 'Enshrouded - seeded for local development', NULL, 4.0, false),
  ('54de5c93-d619-5bf8-ad67-c429f6a6b638', 'Escape from Tarkov', 'FPS', 'Escape from Tarkov - seeded for local development', NULL, 4.0, false),
  ('3b506fc9-6011-51ee-8495-9337cb9cbb23', 'F1 25', 'Racing', 'F1 25 - seeded for local development', NULL, 4.0, false),
  ('d905163a-af24-5ce5-bb13-7032e0af9491', 'Factorio', 'Simulation', 'Factorio - seeded for local development', NULL, 4.0, false),
  ('9a772756-8f8e-5d33-9d8d-65818e54859f', 'Fall Guys', 'Party', 'Fall Guys - seeded for local development', NULL, 4.0, false),
  ('d82daa94-98c1-5876-9507-96dd3e5052e3', 'Final Fantasy XIV Online', 'MMO', 'Final Fantasy XIV Online - seeded for local development', NULL, 4.0, false),
  ('1b2521f1-6b03-5035-9861-3ccd0f9d0e45', 'Football Manager 25', 'Simulation', 'Football Manager 25 - seeded for local development', NULL, 4.0, false),
  ('c585b181-49f8-5911-a6ea-6cf4b832a212', 'Fortnite', 'Battle Royale', 'Fortnite - seeded for local development', NULL, 4.0, false),
  ('d28a7c61-c751-5c13-90b2-36db858c759f', 'Forza Horizon 5', 'Racing', 'Forza Horizon 5 - seeded for local development', NULL, 4.0, false),
  ('c6375cc8-2f53-5d7a-8c0c-851ae812811f', 'Frostpunk 2', 'Strategy', 'Frostpunk 2 - seeded for local development', NULL, 4.0, false),
  ('96d98fcf-99f9-5931-8605-588776ffc76f', 'Genshin Impact', 'JRPG', 'Genshin Impact - seeded for local development', NULL, 4.0, false),
  ('5107b32c-0e32-54d5-9bf4-593b7d03c73f', 'Gran Turismo 7', 'Racing', 'Gran Turismo 7 - seeded for local development', NULL, 4.0, false),
  ('3241b288-3eb8-59ad-8c91-252211da92f4', 'Graveyard Keeper', 'Simulation', 'Graveyard Keeper - seeded for local development', NULL, 4.0, false),
  ('6522f1a2-b1c2-5294-a74d-fca9bb62d6e2', 'Guild Wars 2', 'MMO', 'Guild Wars 2 - seeded for local development', NULL, 4.0, false),
  ('53732c0f-b09f-52e1-838f-e152ba21f5d3', 'Guilty Gear: Strive', 'Fighting', 'Guilty Gear: Strive - seeded for local development', NULL, 4.0, false),
  ('739f4f6f-fe6d-50c0-b173-1affba2306c1', 'Hades II', 'Roguelike', 'Hades II - seeded for local development', NULL, 4.0, false),
  ('eb59c95e-aee6-5693-a795-813130a436be', 'Hollow Knight: Silksong', 'Metroidvania', 'Hollow Knight: Silksong - seeded for local development', NULL, 4.0, false),
  ('f2211891-5abe-5725-9309-1a9c65fda088', 'Honkai: Star Rail', 'JRPG', 'Honkai: Star Rail - seeded for local development', NULL, 4.0, false),
  ('91fd2ef8-702e-5841-80f0-06673aaa761f', 'League of Legends', 'MOBA', 'League of Legends - seeded for local development', NULL, 4.0, false),
  ('fdb12303-48c8-5497-8b0e-75041cde6ba8', 'Lethal Company', 'Horror', 'Lethal Company - seeded for local development', NULL, 4.0, false),
  ('33e9b11e-83e2-5a58-b508-4c1fff8d4a62', 'Mass Effect Legendary Edition', 'RPG', 'Mass Effect Legendary Edition - seeded for local development', NULL, 4.0, false),
  ('3f9993b5-e749-5767-8fce-eaac9de4a15f', 'Metaphor: ReFantazio', 'JRPG', 'Metaphor: ReFantazio - seeded for local development', NULL, 4.0, false),
  ('086fd79b-8df1-528c-94c2-bf2f96dcd7b2', 'Minecraft: Java Edition', 'Sandbox', 'Minecraft: Java Edition - seeded for local development', NULL, 4.0, false),
  ('254a2bca-108e-548d-970d-e12348ce18e1', 'Mortal Kombat 1', 'Fighting', 'Mortal Kombat 1 - seeded for local development', NULL, 4.0, false),
  ('66268fa6-0605-5845-b6d3-fddcdf468e25', 'NBA 2K26', 'Sports', 'NBA 2K26 - seeded for local development', NULL, 4.0, false),
  ('0f1c8c80-83c6-5e94-a40c-3f7203cb7e57', 'Naraka: Bladepoint', 'Battle Royale', 'Naraka: Bladepoint - seeded for local development', NULL, 4.0, false),
  ('1eb898a6-0d5c-5596-bb4e-034e98301076', 'Old School RuneScape', 'MMO', 'Old School RuneScape - seeded for local development', NULL, 4.0, false),
  ('dcba42ff-7aaa-554c-a8b9-7304cc710fa7', 'Outer Wilds', 'Adventure', 'Outer Wilds - seeded for local development', NULL, 4.0, false),
  ('7958fd20-48e0-5948-9e52-d33887ba225e', 'Overwatch', 'FPS', 'Overwatch - seeded for local development', NULL, 4.0, false),
  ('e2fd740d-be76-5f3e-bb3d-6d7a40e36d37', 'PUBG: Battlegrounds', 'Battle Royale', 'PUBG: Battlegrounds - seeded for local development', NULL, 4.0, false),
  ('5eccf6b5-f39f-5bbd-a089-939d8011309b', 'Palia', 'Simulation', 'Palia - seeded for local development', NULL, 4.0, false),
  ('efa7a986-f8a7-54e7-9bf0-e1801eb00661', 'Palworld', 'Survival', 'Palworld - seeded for local development', NULL, 4.0, false),
  ('d1e29e9a-f6c7-5d64-9881-bbd5d1bfe1c3', 'Persona 5 Royal', 'JRPG', 'Persona 5 Royal - seeded for local development', NULL, 4.0, false),
  ('129916fb-8426-558a-92ea-f079aff350ee', 'Phasmophobia', 'Horror', 'Phasmophobia - seeded for local development', NULL, 4.0, false),
  ('a69aad1d-e57d-532b-a565-697326911efd', 'R.E.P.O.', 'Horror', 'R.E.P.O. - seeded for local development', NULL, 4.0, false),
  ('bdb85435-8f56-54db-a6a9-9c870beaa4ec', 'Rainbow Six Siege', 'FPS', 'Rainbow Six Siege - seeded for local development', NULL, 4.0, false),
  ('7b647249-b43b-5b15-83ec-6330e0d082b7', 'Red Dead Redemption 2', 'Adventure', 'Red Dead Redemption 2 - seeded for local development', NULL, 4.0, false),
  ('03d76daf-8cd1-5047-af88-e1adb81d401c', 'Resident Evil 4', 'Horror', 'Resident Evil 4 - seeded for local development', NULL, 4.0, false),
  ('a0a84de4-f6e5-55fa-9010-3b66df50b6ec', 'RimWorld', 'Simulation', 'RimWorld - seeded for local development', NULL, 4.0, false),
  ('40c1ca2f-e87e-5a76-851c-3be9e02c8575', 'Rocket League', 'Sports', 'Rocket League - seeded for local development', NULL, 4.0, false),
  ('17ab6c97-fa94-59c8-be39-1ef1a4785a50', 'Rust', 'Survival', 'Rust - seeded for local development', NULL, 4.0, false),
  ('b25a6e67-95e7-5fc4-a7e2-60a4aa026276', 'Satisfactory', 'Simulation', 'Satisfactory - seeded for local development', NULL, 4.0, false),
  ('c16d02e7-27e6-5f5b-9a46-623fc41820d4', 'Slay the Spire II', 'Roguelike', 'Slay the Spire II - seeded for local development', NULL, 4.0, false),
  ('9999f765-9579-513c-b873-cd8734da64e4', 'Smite 2', 'MOBA', 'Smite 2 - seeded for local development', NULL, 4.0, false),
  ('9a7276ba-8984-579a-b4a3-0be22c024f3a', 'Sonic the Hedgehog 2', 'Platformer', 'Sonic the Hedgehog 2 - seeded for local development', NULL, 4.0, false),
  ('7fef210c-abf7-555c-8020-1e3f12952356', 'StarCraft II: Wings of Liberty', 'Strategy', 'StarCraft II: Wings of Liberty - seeded for local development', NULL, 4.0, false),
  ('d8671895-3f63-53a2-a1b5-661428ea1112', 'Stardew Valley', 'Simulation', 'Stardew Valley - seeded for local development', NULL, 4.0, false),
  ('d05c3d6c-9ddb-5d2e-bb84-1f445ea1ef5e', 'Stellaris', 'Strategy', 'Stellaris - seeded for local development', NULL, 4.0, false),
  ('07987b30-9ca6-58f0-b442-b7b18605a363', 'Street Fighter 6', 'Fighting', 'Street Fighter 6 - seeded for local development', NULL, 4.0, false),
  ('37e34ee8-dd47-5286-825a-b71c4b7bcd7f', 'Street Fighter II', 'Fighting', 'Street Fighter II - seeded for local development', NULL, 4.0, false),
  ('39c86b28-86fd-576d-869f-ef903cdd425a', 'Super Mario 64', 'Platformer', 'Super Mario 64 - seeded for local development', NULL, 4.0, false),
  ('bf978443-1a9f-5b84-9129-263fc07533c1', 'Super Smash Bros. Ultimate', 'Fighting', 'Super Smash Bros. Ultimate - seeded for local development', NULL, 4.0, false),
  ('40ab9965-5da7-53d3-999e-fcaac092edbb', 'Tales of Arise', 'JRPG', 'Tales of Arise - seeded for local development', NULL, 4.0, false),
  ('209807e6-43c7-5778-a5ca-785c6cf657b6', 'Teamfight Tactics', 'Strategy', 'Teamfight Tactics - seeded for local development', NULL, 4.0, false),
  ('3fccaad1-6fd5-589e-ab46-b10e764bab1e', 'Tekken 8', 'Fighting', 'Tekken 8 - seeded for local development', NULL, 4.0, false),
  ('283aef3a-7321-537e-8b33-d1a5803d93d8', 'Terraria', 'Sandbox', 'Terraria - seeded for local development', NULL, 4.0, false),
  ('fc65483d-1425-50cb-af67-df64d4a28a88', 'The Finals', 'FPS', 'The Finals - seeded for local development', NULL, 4.0, false),
  ('98d90875-6c14-53c9-bacb-8fd224712a57', 'The Forest', 'Survival', 'The Forest - seeded for local development', NULL, 4.0, false),
  ('ddfcc9ef-a57d-51a4-b88f-b30243927e99', 'The Legend of Zelda: Ocarina of Time', 'Adventure', 'The Legend of Zelda: Ocarina of Time - seeded for local development', NULL, 4.0, false),
  ('2fa3e690-f872-5133-90ea-71d3a99d0aa9', 'The Sims 4', 'Simulation', 'The Sims 4 - seeded for local development', NULL, 4.0, false),
  ('003693ef-07f5-554b-806c-a08274c855bc', 'The Witcher 3: Wild Hunt', 'RPG', 'The Witcher 3: Wild Hunt - seeded for local development', NULL, 4.0, false),
  ('b58c24dc-861c-52db-aa0c-9ac791c98726', 'Throne and Liberty', 'MMO', 'Throne and Liberty - seeded for local development', NULL, 4.0, false),
  ('88c3ba78-b55e-592e-b49e-d22315e9a77e', 'Tiny Glade', 'Simulation', 'Tiny Glade - seeded for local development', NULL, 4.0, false),
  ('53823a19-2aec-551f-8ef4-06805e389ef6', 'Total War: Warhammer III', 'Strategy', 'Total War: Warhammer III - seeded for local development', NULL, 4.0, false),
  ('ae6b9a5c-891d-59ac-8af2-96685b424ade', 'Valorant', 'FPS', 'Valorant - seeded for local development', NULL, 4.0, false),
  ('ee8ff239-e21f-5d4a-b312-754ac05f0894', 'Valheim', 'Survival', 'Valheim - seeded for local development', NULL, 4.0, false),
  ('4d2a2c1c-d9f3-5b52-98f1-7c31b24247f5', 'Vampire Survivors', 'Roguelike', 'Vampire Survivors - seeded for local development', NULL, 4.0, false),
  ('8d3b51cf-ae53-577b-b7f6-264beeab3057', 'World of Warcraft', 'MMO', 'World of Warcraft - seeded for local development', NULL, 4.0, false),
  ('bbfe57f7-3234-56bc-8769-922c46ebb3fe', 'eFootball 2026', 'Sports', 'eFootball 2026 - seeded for local development', NULL, 4.0, false)
ON CONFLICT (game_id) DO NOTHING;

-- Keywords: the free-text tastes a gamer picks alongside their games.
--
-- The descriptions are the ones from upgrade-2026-16-keyword-descriptions.sql, and they
-- belong here rather than only in that migration. Every one of these used to be the
-- keyword repeated back at the reader — "chill" described as "chill" — which is what the
-- migration exists to fix; leaving the seed as it was meant every fresh environment
-- recreated the bug and then needed the migration run against it to undo it. The two
-- must not disagree: if you edit a description, edit it in both.
--
-- Written in the second person and about *matching* rather than about the word. Somebody
-- picking keywords is answering "who do I want to be put in front of", so the useful
-- sentence is what picking it will get them. Roughly seventy characters — this renders in
-- a small font on the second line of a list row.
--
-- The ON CONFLICT updates the description rather than doing nothing, so a database seeded
-- before this change picks the new copy up on the next run.
INSERT INTO keywords (id, keyword_name, description, created_date) VALUES
  ('449fef62-ece3-5ad4-9de9-868e67573d2b', 'achievement hunter', 'You go for the full list, however long it takes.', NOW()),
  ('a445deca-7c41-53af-800e-c557c023bfd3', 'aim training', 'You warm up before you play, and it shows.', NOW()),
  ('3e9b4de2-b73a-5c67-b9bc-974152216eec', 'anime fan', 'Anime art, anime games, anime everything.', NOW()),
  ('2f89e16a-f7a5-5b11-b6f6-43f1274180b9', 'builder', 'You would rather build the base than defend it.', NOW()),
  ('95df7514-7183-580e-b234-cd8325ee034a', 'casual', 'You play to unwind. No pressure, no schedule.', NOW()),
  ('e873002d-5e1a-5066-ad6e-beb26d9914db', 'chaotic', 'Plans are optional. Something will happen.', NOW()),
  ('9f713f18-8abc-5432-9c7b-e6c2300d600e', 'chill', 'Relaxed sessions, easy company, nobody shouting.', NOW()),
  ('d2ec3a17-fd1e-55a7-b178-c58f42d7185a', 'clutch', 'You are calm when the round is on the line.', NOW()),
  ('a3c5df97-eade-5b9d-9aba-6c68593625b4', 'co-op', 'You would rather beat the game with someone than alone.', NOW()),
  ('520a7c85-a12c-5094-b934-74103e228465', 'collector', 'Every skin, every mount, every card.', NOW()),
  ('c792bd9c-0123-556f-b387-4427960b75fd', 'combo practice', 'You spend real time in the training room.', NOW()),
  ('3dae5b9d-e93d-5e1b-bfe1-2c90e2462bac', 'competitive', 'You are here to win, and you play like it.', NOW()),
  ('49673a22-3f1d-5925-894a-ddc49c9efb8b', 'completionist', 'The map is not done until all of it is done.', NOW()),
  ('0322639c-7e73-5a0a-9bea-1e4d58bf984d', 'couch co-op', 'Two controllers, one sofa, same screen.', NOW()),
  ('72aaad3f-8b44-5dbd-8670-d44b7c3d284b', 'decorator', 'Your base, island or house is the whole hobby.', NOW()),
  ('d066e7b5-6da2-5e75-ae24-c9ffd2880e4b', 'emulator user', 'Older systems, running on whatever you have now.', NOW()),
  ('675b0742-290f-5fc9-8d48-c13126420bb3', 'endgame focused', 'The story is the tutorial. The endgame is the game.', NOW()),
  ('16764886-bc26-5d2d-8343-a0a48b021d7f', 'esports', 'You follow the pro scene and know the rosters.', NOW()),
  ('a7f6511c-63de-595e-813d-20e76b509977', 'explorer', 'You take the side path before the main quest.', NOW()),
  ('50d64a20-4423-5af7-b407-b4b5b61d6b53', 'grinder', 'Long, repetitive, satisfying. You do not mind the hours.', NOW()),
  ('73dc1f84-9884-5b80-bf57-a0752c20ce3f', 'guild leader', 'You organise people, and you are good at it.', NOW()),
  ('ff08e815-226f-50ec-b051-4f360efc5deb', 'jumpscare enjoyer', 'Horror games, lights off, sound up.', NOW()),
  ('6c291244-aa6b-5f8d-b648-c77ba85559e7', 'long sessions', 'When you sit down it is for the evening.', NOW()),
  ('6171944d-2a0f-5941-aa48-950b362c1793', 'loot goblin', 'You open every chest and pick up everything.', NOW()),
  ('5d7074a7-0cc6-5560-b825-1d0f330ad3ae', 'lore nerd', 'You have read the item descriptions. All of them.', NOW()),
  ('98992d9c-0405-59d6-bedb-23991d7d04bc', 'min-maxer', 'Optimal build, optimal route, spreadsheet open.', NOW()),
  ('9627c966-8ad1-5e8d-bc30-16580af8ddb5', 'modder', 'You install mods, and probably write a few.', NOW()),
  ('258104f3-501e-5452-bfe4-508aa7f08f69', 'no mic', 'You play without voice chat, and prefer it that way.', NOW()),
  ('266faaa3-b4ad-5154-99a2-f49e6c67643a', 'no spoilers', 'Do not tell them anything. They are getting there.', NOW()),
  ('7917e8c7-2e2c-53d3-95d1-388d353dbfa0', 'nostalgic', 'The games you grew up with still hit hardest.', NOW()),
  ('770caa11-cdd9-5c38-b2d1-4b2c2ea84508', 'raider', 'Scheduled runs, full team, cleared bosses.', NOW()),
  ('d789fdac-bbc4-56b7-894c-50e3cfc6d046', 'ranked grinder', 'Climbing the ladder is the reason you log in.', NOW()),
  ('9948c803-b749-5bf8-9999-96369cb208cf', 'roleplayer', 'You stay in character, and enjoy people who do too.', NOW()),
  ('6144c245-5c30-512e-ab09-0e788ee3a43f', 'short sessions', 'Half an hour here and there, whenever you can.', NOW()),
  ('f766c128-dd41-52c9-aa8b-f823a8dad522', 'shotcaller', 'You make the calls so the team does not have to.', NOW()),
  ('2252074b-eb12-5a53-be20-a1e62cf45abc', 'sim racer', 'Wheel, pedals, and lap times you actually care about.', NOW()),
  ('e28d20c0-b3dd-506c-9aac-742241148003', 'social', 'The people are the reason you are here.', NOW()),
  ('e3f47f11-4f84-5056-8157-68a5fe76d79f', 'solo player', 'You play alone, and you like it that way.', NOW()),
  ('91094ecf-0f93-5113-88a2-af5606336bfa', 'speedrunner', 'You have a personal best and you are chasing it.', NOW()),
  ('8e4723e9-7517-5b35-b0b5-1ca54874b37b', 'squad player', 'Always in a party, never queuing alone.', NOW()),
  ('48bdbede-2e99-5b61-9014-62ee1de779fa', 'story-driven', 'You are here for the writing and the characters.', NOW()),
  ('4bb6afe0-9b54-5f21-9b19-8463ea20c797', 'streamer', 'You play with an audience watching.', NOW()),
  ('5dcd8466-eed3-5437-8069-3394f909682c', 'theorycrafter', 'You work out why it is good before you use it.', NOW()),
  ('51e21bfa-c532-5548-9845-6ae9c2aac9fd', 'tournament goer', 'You show up in person, or at least sign up.', NOW()),
  ('b8ceb8bc-0413-5ad1-8ffa-d50d4597e199', 'toxic-free', 'No abuse, no blaming. You leave lobbies that go that way.', NOW()),
  ('5391df75-c852-5045-92c2-911542bdf49d', 'tryhard', 'Even the casual mode is played properly.', NOW()),
  ('b1346860-7b70-53b9-83f5-4163863d4f7c', 'voice chat', 'Mic on, talking through it, calls being made.', NOW()),
  ('117b690a-68c4-55a1-9e29-78fe000fbe8b', 'waifu collector', 'Gacha pulls and a roster you are attached to.', NOW())
ON CONFLICT (id) DO UPDATE SET
  keyword_name = EXCLUDED.keyword_name, description = EXCLUDED.description;

-- Default avatars. Object keys, not URLs: which host serves them is a deployment
-- concern that changes, and a stored URL would bake today's answer into twelve rows.
-- AvatarUrls turns these into a URL at read time.
--
-- Nothing here is for sale. Avatars stopped being merchandise when uploads landed; what
-- gets sold now is frames and banners, which decorate an avatar rather than being one.
-- The price and is_special columns went with that decision.
--
-- The art is generated: see default-avatars/generate.py, which also writes the files
-- these keys point at.
INSERT INTO avatars (id, image) VALUES
  ('fe6bf0f4-5a02-543d-a646-a56f058492d0', 'default-avatars/avatar-01.png'),
  ('5e4d0987-2c83-5df1-a31f-9dda325f85f9', 'default-avatars/avatar-02.png'),
  ('c74909e0-afac-5bc4-aecd-9b8ee13107ee', 'default-avatars/avatar-03.png'),
  ('d65a6954-f77e-5052-896c-f6589f4ecd68', 'default-avatars/avatar-04.png'),
  ('b9590ea7-ed4d-5716-9d71-d5c5d0e322ba', 'default-avatars/avatar-05.png'),
  ('8461bc85-b3e8-570c-b309-1bd34148b518', 'default-avatars/avatar-06.png'),
  ('f0778fd2-4149-5205-a334-c0076f289b1b', 'default-avatars/avatar-07.png'),
  ('2ddb464c-a728-58ee-83d8-68a2fb35bd4b', 'default-avatars/avatar-08.png'),
  ('018d4463-c86d-5262-9c0e-25f8dae5c732', 'default-avatars/avatar-09.png'),
  ('023ec115-d964-53a0-86da-52503ff63e0a', 'default-avatars/avatar-10.png'),
  ('d88e7100-40a3-5a5b-97a0-970076740671', 'default-avatars/avatar-11.png'),
  ('1c1218df-f4d8-5e96-af80-1222d1be0eb3', 'default-avatars/avatar-12.png')
ON CONFLICT (id) DO UPDATE SET image = EXCLUDED.image;

-- Badges are not seeded. There is no catalogue table for them: a mission is a rule, and
-- the rules live in the Badge enum. All this database holds is who earned what, which is
-- written as gamers earn things.

COMMIT;

-- Cosmetics: avatar frames and profile banners.
--
-- Object keys, not URLs — AvatarUrls/CosmeticService resolve them at read time. The art
-- is generated: see cosmetics/generate.py, which writes the files these point at.
--
-- Exactly one item is free — the Steel frame — and it is claimed from the store rather
-- than appearing in the inventory on its own; see upgrade-2026-30-claimable-steel.sql for
-- why free stopped meaning "already owned". Everything else is bought, starting at the
-- 100-coin entry tier. Animated frames cost more than static ones because they took more
-- to make and are the thing people actually want.
--
-- The two Gold items are neither free nor for sale: membership_only means they arrive
-- with a subscription and are withdrawn when it lapses, which is why their price is 0 and
-- why the store never lists them. They match upgrade-2026-18-membership-cosmetics.sql row
-- for row, ids included — the seed used to sell the Gold frame for 400 coins, so a
-- database built from it alone put a Gold-exclusive item back on the shelf.
-- membership_only is listed explicitly rather than left to the column default.
-- The migration that added it supplies `DEFAULT false`; the entity does not declare
-- one, so a schema built by Hibernate from the entities has the column NOT NULL with
-- no default and this insert fails on it. Being explicit works on both paths.
INSERT INTO cosmetic (id, kind, name, asset_key, animated, price, sort_order, membership_only, created_date) VALUES
  ('948fe89b-b161-5324-8fe3-d54014739e97', 'FRAME', 'Steel', 'frames/frame-steel.png', false, 0, 0, false, now()),
  ('c65bd3d8-6b59-546a-ac85-8f9bd080b279', 'FRAME', 'Signature', 'frames/frame-brand.png', false, 100, 1, false, now()),
  ('202e2cb8-5e54-577e-8ea1-2a56d4aa6a5d', 'FRAME', 'Reticle', 'frames/frame-reticle.png', false, 150, 2, false, now()),
  ('772595df-1b8a-5c40-b18a-fafa83de3297', 'FRAME', 'Bronze', 'frames/frame-bronze.png', false, 200, 3, false, now()),
  ('7767adda-3c57-5bb8-9c19-66e3668d961c', 'FRAME', 'Gold', 'frames/frame-gold.png', false, 0, 4, true, now()),
  ('5111d8c6-c0e3-5320-8a22-e06fe89883c1', 'FRAME', 'Pulse', 'frames/frame-pulse.webp', true, 600, 5, false, now()),
  ('df6369a3-e370-5c22-9fa5-4e51bab9cdec', 'FRAME', 'Sweep', 'frames/frame-sweep.webp', true, 750, 6, false, now()),
  ('089fed4f-abc2-5193-ac25-5aa437fd52a0', 'FRAME', 'Orbit', 'frames/frame-orbit.webp', true, 900, 7, false, now()),
  ('0473126d-f42e-5971-8e64-d6e2a26bed08', 'FRAME', 'Toxic', 'frames/frame-toxic.webp', true, 900, 8, false, now()),
  ('90f6a11b-fa77-561b-9722-6c5b0ee79d09', 'FRAME', 'Rotor', 'frames/frame-rotor.webp', true, 1200, 9, false, now()),
  ('32fa7c71-9011-59d9-9155-4e5e710e974c', 'FRAME', 'Ember', 'frames/frame-ember.webp', true, 1500, 10, false, now()),
  ('1f0c9f4e-2b6a-4d3e-9c47-5a8b1e7d6c20', 'BANNER', 'Gold', 'banners/banner-gold.jpg', false, 0, 0, true, now()),
  ('5c03c249-15e3-5e97-98e7-e2b659e3c444', 'BANNER', 'Hex', 'banners/banner-hex.jpg', false, 100, 0, false, now()),
  ('c4793a6f-b11f-544e-8fb5-50070bc6e7c3', 'BANNER', 'CRT', 'banners/banner-crt.jpg', false, 100, 1, false, now()),
  ('40fe7f85-d3e0-5def-942e-4be7d6c36fc3', 'BANNER', 'Bokeh', 'banners/banner-bokeh.jpg', false, 200, 2, false, now()),
  ('23a03605-0cbe-5abf-80d3-59a1fcea2397', 'BANNER', 'Circuit', 'banners/banner-circuit.jpg', false, 300, 3, false, now()),
  ('c637135f-3d1c-58b9-84ed-a7d2c3bad9c1', 'BANNER', 'Void', 'banners/banner-void.jpg', false, 400, 4, false, now()),
  ('a217b933-c1e1-5a13-aa29-e36321cd1cbd', 'BANNER', 'Arena', 'banners/banner-arena.jpg', false, 500, 5, false, now()),
  ('0fe0e4e0-9c3b-5526-87af-46b192ca596d', 'BANNER', 'Dusk', 'banners/banner-dusk.jpg', false, 600, 6, false, now()),
  ('10db4e23-1579-5f41-83f9-e4db8f75d93a', 'BANNER', 'Synthwave', 'banners/banner-synthwave.jpg', false, 800, 7, false, now())
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name, asset_key = EXCLUDED.asset_key, animated = EXCLUDED.animated,
  price = EXCLUDED.price, sort_order = EXCLUDED.sort_order,
  membership_only = EXCLUDED.membership_only;
