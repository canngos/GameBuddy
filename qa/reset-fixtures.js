#!/usr/bin/env node
/**
 * Returns the seeded population to an untouched state.
 *
 *   node qa/reset-fixtures.js            report how many fixtures are usable
 *   node qa/reset-fixtures.js --apply    reset them
 *
 * The functional suite only hands out seeded gamers with no swipes, matches, declines or
 * chat, so a few dozen are consumed per run and the pool eventually runs down. This puts
 * it back: it deletes the *interaction* rows the tests created and zeroes the quota
 * counters, leaving the profiles and the catalogue alone so the trained artefact still
 * matches the database.
 *
 * It deliberately does not touch the two real accounts, and it never deletes a gamer.
 */

const { execFileSync } = require('node:child_process');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const PROJECT = process.env.GB_COMPOSE_PROJECT || 'gamebuddy';
const BOTS = "email like '%@bot.gamebuddy.invalid'";

function psql(sql) {
  return execFileSync(
    'docker',
    ['compose', '-p', PROJECT, 'exec', '-T', 'postgres',
     'psql', '-U', 'gamebuddy', '-d', 'gamebuddy', '-t', '-A', '-c', sql],
    { encoding: 'utf8', cwd: ROOT, maxBuffer: 64 * 1024 * 1024 },
  ).trim();
}

const botIds = `(select user_id from gamebuddy.gamer where ${BOTS})`;

function report() {
  const usable = psql(
    `select count(*) from gamebuddy.gamer g where ${BOTS.replace(/email/g, 'g.email')}
       and not g.is_blocked and g.swipes_used = 0 and g.accepts_used = 0
       and g.subscription_tier = 'BASIC' and g.subscription_expires_at is null and g.coin = 0
       and not exists (select 1 from gamebuddy.approved_matches m
                        where m.user_id = g.user_id or m.matched_id = g.user_id)
       and not exists (select 1 from gamebuddy.chat_participant p where p.user_id = g.user_id);`,
  );
  const total = psql(`select count(*) from gamebuddy.gamer where ${BOTS};`);
  console.log(`${usable} of ${total} seeded fixtures are untouched and usable.`);
  return Number(usable);
}

function reset() {
  // Order matters: messages before participants, participants before rooms.
  const statements = [
    'begin',
    `delete from gamebuddy.chat_message where room_id in
       (select room_id from gamebuddy.chat_participant where user_id in ${botIds})`,
    `delete from gamebuddy.chat_participant where user_id in ${botIds}`,
    'delete from gamebuddy.chat_room where id not in (select room_id from gamebuddy.chat_participant)',
    `delete from gamebuddy.approved_matches where user_id in ${botIds} or matched_id in ${botIds}`,
    `delete from gamebuddy.declined_matches where user_id in ${botIds} or declined_id in ${botIds}`,
    `delete from gamebuddy.unlocked_admirer where user_id in ${botIds} or admirer_id in ${botIds}`,
    `delete from gamebuddy.recommendation_impression where user_id in ${botIds}`,
    `delete from gamebuddy.content_report where reporter_id in ${botIds} or author_id in ${botIds}`,
    `delete from gamebuddy.coin_ledger where user_id in ${botIds}`,
    `delete from gamebuddy.funnel_event where user_id in ${botIds}`,
    `delete from gamebuddy.purchase where user_id in ${botIds}`,
    // The quota counters, the ban flag and the subscription state. Subscription matters as
    // much as the counters: the billing suite grants Gold through the webhook, and a
    // fixture left GOLD has no swipes and no matches, so it reads as untouched and gets
    // handed to a test that begins by asserting BASIC.
    `update gamebuddy.gamer set swipes_used = 0, accepts_used = 0, is_blocked = false,
       tokens_valid_from = null, subscription_tier = 'BASIC', subscription_expires_at = null,
       coin = 0 where ${BOTS}`,
    'commit',
  ];
  psql(statements.join(';\n'));
  console.log('Reset complete.');
}

if (process.argv.includes('--apply')) {
  reset();
  report();
} else {
  const usable = report();
  if (usable < 60) console.log('Low. Run with --apply to reset.');
}
