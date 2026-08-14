#!/usr/bin/env node
/**
 * Prepares the state the flows need, and prints it as Maestro `-e` arguments.
 *
 *   node qa/maestro/provision.js            # human-readable
 *   node qa/maestro/provision.js --args     # just the -e flags, for the runner
 *
 * Maestro drives a screen; it cannot read a verification code out of Postgres or create a
 * match between two accounts. So everything a flow needs to *start* is built here through
 * the same API the app uses, and handed over as environment variables. What the flows then
 * exercise is the part that matters — the UI.
 *
 * Three accounts, because three flows need different starting states:
 *
 *   PENDING  registered, code issued, not yet verified   -> 01-onboarding finishes the job
 *   MEMBER   fully onboarded, matched with a partner     -> 02-swipe, 03-chat, 04-market, 05-settings
 *   PARTNER  the other half of MEMBER's match            -> so the conversation has two sides
 *
 * Everything is marked `@qa.gamebuddy.invalid` and removed by `--cleanup`.
 */

const { post, get, P } = require('../functional/helpers/api');
const db = require('../functional/helpers/db');
const { createAccount, cleanupAll, unique, adultBirthDate, DOMAIN, PASSWORD } = require('../functional/helpers/accounts');
const { env } = require('../functional/helpers/tokens');

const args = process.argv.slice(2);

async function main() {
  if (args.includes('--cleanup')) {
    cleanupAll();
    console.log('Removed every @qa.gamebuddy.invalid account.');
    return;
  }

  // 1. A registered-but-unverified account, so 01-onboarding can drive verify -> home.
  const pendingEmail = `qa-onb-${unique()}@${DOMAIN}`;
  const registered = await post(`${P.auth}/register`,
    { email: pendingEmail, password: PASSWORD, acceptedTerms: true });
  if (registered.status !== 201) throw new Error(`register failed: ${registered.status} ${registered.text}`);
  const code = db.verificationCode(pendingEmail);
  if (!code) throw new Error('no verification code was stored');

  // 2. A fully onboarded account, and a partner it has already matched with, so the chat
  //    flow has a conversation to open rather than having to manufacture one on screen.
  const member = await createAccount();
  const partner = await createAccount();
  await post(`${P.match}/accept`, { userId: partner.userId }, { token: member.token });
  await post(`${P.match}/accept`, { userId: member.userId }, { token: partner.token });

  // The partner speaks first, so MEMBER opens a conversation that already has a message in
  // it — an empty thread and a broken thread look identical on screen.
  await post('/messages/send',
    { receiver: member.userId, message: 'Hey! Ready for a game?' }, { token: partner.token });

  const matches = await get(`${P.match}/get/matches`, { token: member.token });
  const matched = (matches.data?.recommendedGamers ?? []).some((g) => g.userId === partner.userId);
  if (!matched) throw new Error('the match was not created; chat flow would fail');

  // 3. The moderator, for the admin flow. Bootstrapped from .env on first start.
  const e = env();
  const moderatorEmail = e.MODERATOR_EMAIL || '';
  const moderatorPassword = e.MODERATOR_PASSWORD || '';

  const values = {
    PENDING_EMAIL: pendingEmail,
    PENDING_CODE: String(code),
    // Letters only. Usernames are a PUBLIC text surface and the filter strips phone
    // numbers from them, so a digit-heavy random string comes back CONTENT_BLOCKED.
    NEW_USERNAME: `qa${unique()}`,
    PASSWORD,
    MEMBER_EMAIL: member.email,
    MEMBER_USERNAME: member.username,
    PARTNER_USERNAME: partner.username,
    MODERATOR_EMAIL: moderatorEmail,
    MODERATOR_PASSWORD: moderatorPassword,
  };

  if (args.includes('--args')) {
    // One line the runner can splat straight onto the maestro command.
    console.log(Object.entries(values).map(([k, v]) => `-e ${k}=${v}`).join(' '));
    return;
  }

  console.log('Provisioned:\n');
  for (const [k, v] of Object.entries(values)) {
    const shown = k.includes('PASSWORD') ? `${String(v).slice(0, 3)}…` : v;
    console.log(`  ${k.padEnd(20)} ${shown}`);
  }
  if (!moderatorEmail) {
    console.log('\n  MODERATOR_EMAIL is empty in .env — 06-admin will be skipped.');
  }
  console.log('\nRun the flows with:  qa/maestro/run.ps1');
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
