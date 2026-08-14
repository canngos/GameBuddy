#!/usr/bin/env node
/**
 * Probes offline token minting, then writes a token pool for the load tests.
 *
 *   node qa/perf/mint-tokens.js --probe            verify one token works, print the result
 *   node qa/perf/mint-tokens.js --count 2000       write qa/perf/tokens.json
 *
 * The probe runs first and on its own for a reason: if minting does not authenticate,
 * every load number produced afterwards would be a measurement of 401 handling. Better to
 * find that out in two seconds than after a twenty-minute soak.
 */

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { mint } = require('../functional/helpers/tokens');

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i === -1 ? fallback : args[i + 1];
};

const BASE = flag('base', process.env.GB_BASE_URL || 'http://localhost:8080');
const COMPOSE_PROJECT = flag('project', 'gamebuddy');

/** Pulls gamer emails straight out of Postgres. psql via compose so no driver is needed. */
function botEmails(limit) {
  const sql =
    `select email from gamebuddy.gamer ` +
    `where email like '%@bot.gamebuddy.invalid' and is_registered and is_verified ` +
    `and not is_blocked order by user_id limit ${Number(limit)};`;
  const out = execFileSync(
    'docker',
    ['compose', '-p', COMPOSE_PROJECT, 'exec', '-T', 'postgres',
     'psql', '-U', 'gamebuddy', '-d', 'gamebuddy', '-t', '-A', '-c', sql],
    { encoding: 'utf8', cwd: path.resolve(__dirname, '..', '..') },
  );
  return out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
}

async function check(email) {
  const token = mint(email);
  const res = await fetch(`${BASE}/application/get/user/info`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.text();
  return { status: res.status, body: body.slice(0, 400), token };
}

async function main() {
  if (args.includes('--probe')) {
    const [email] = botEmails(1);
    if (!email) throw new Error('No seeded bot accounts found — has the population been seeded?');

    console.log(`Probing with ${email}`);
    const ok = await check(email);
    console.log(`  minted token       -> HTTP ${ok.status}`);
    console.log(`  ${ok.body}`);

    // Negative controls. A 200 here would mean the token is not actually being verified,
    // which is a far more serious finding than the harness not working.
    const wrongSecret = await fetch(`${BASE}/application/get/user/info`, {
      headers: { Authorization: `Bearer ${mint(email, { secret: 'x'.repeat(48) })}` },
    });
    console.log(`  wrong signing key  -> HTTP ${wrongSecret.status} (expect 401/403)`);

    const expired = await fetch(`${BASE}/application/get/user/info`, {
      headers: { Authorization: `Bearer ${mint(email, { iatOffset: -60 * 60 * 24 * 30, ttlSeconds: 60 })}` },
    });
    console.log(`  expired token      -> HTTP ${expired.status} (expect 401/403)`);

    const unknown = await fetch(`${BASE}/application/get/user/info`, {
      headers: { Authorization: `Bearer ${mint('nobody@bot.gamebuddy.invalid')}` },
    });
    console.log(`  unknown subject    -> HTTP ${unknown.status} (expect 401/403)`);

    const none = await fetch(`${BASE}/application/get/user/info`);
    console.log(`  no token           -> HTTP ${none.status} (expect 401/403)`);

    if (ok.status !== 200) process.exitCode = 1;
    return;
  }

  const count = Number(flag('count', 2000));
  const emails = botEmails(count);
  const pool = emails.map((email) => ({ email, token: mint(email, { ttlSeconds: 60 * 60 * 24 }) }));
  const out = path.join(__dirname, 'tokens.json');
  fs.writeFileSync(out, JSON.stringify(pool));
  console.log(`Wrote ${pool.length} tokens to ${out}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
