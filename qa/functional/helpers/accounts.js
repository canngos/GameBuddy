/**
 * Creates fully-onboarded accounts through the real signup flow.
 *
 * Every other suite needs a live account it can act as, and building one is six calls
 * deep: register, read the code out of Postgres, verify, username, details. Doing that in
 * each test file would be five copies of a flow that is itself under test, so it lives
 * here and 01-signup.test.js asserts on the individual steps.
 *
 * Test accounts are marked `@qa.gamebuddy.invalid` — .invalid is reserved by RFC 2606 and
 * can never be a real address, the same trick the seeded fixtures use. Everything the
 * suite creates is removable with one statement:
 *
 *   DELETE FROM gamebuddy.gamer WHERE email LIKE '%@qa.gamebuddy.invalid';
 */

const crypto = require('node:crypto');
const { post, get, P, CODE } = require('./api');
const db = require('./db');

const DOMAIN = 'qa.gamebuddy.invalid';
const PASSWORD = 'Str0ng!Passw0rd';

/**
 * A per-process marker, embedded in every address this file creates.
 *
 * `node --test` runs test *files* in parallel, each in its own process. With a cleanup
 * that deleted every `@qa.gamebuddy.invalid` account, whichever file finished first wiped
 * the accounts the others were still using — which surfaced as 08-security reporting that
 * a password change had failed to revoke a token, when in fact 07-billing had deleted the
 * account out from under it a moment earlier. Two hours of a "security hole" that was a
 * test harness racing itself.
 *
 * Scoping the marker to the process makes each file clean up only its own.
 */
const RUN = `${process.pid.toString(36)}${crypto.randomBytes(2).toString('hex')}`;

/**
 * A unique, letters-only token.
 *
 * Letters, not hex. Usernames are a `TextSurface.PUBLIC` surface, so the filter strips
 * phone numbers from them — and a hex string that happens to come out digit-heavy
 * (`qa4718290135`) is indistinguishable from one. That made account creation fail with
 * CONTENT_BLOCKED on roughly one run in three, in whichever test happened to draw the
 * unlucky value, which is the most annoying possible kind of flake.
 *
 * Worth knowing as a product fact too: a gamer who wants to be `player1234567` cannot be.
 */
const unique = () => Array.from(crypto.randomBytes(8))
  .map((b) => 'abcdefghijklmnopqrstuvwxyz'[b % 26])
  .join('');

/** An adult birth date, ~25 years old. The service is 18+ and refuses anything younger. */
function adultBirthDate(yearsOld = 25) {
  const d = new Date();
  d.setFullYear(d.getFullYear() - yearsOld);
  return d.toISOString().slice(0, 10);
}

/** Three games and five keywords, the documented minimums for /auth/details. */
async function catalogue(token) {
  const games = await get(`${P.profile}/get/games`, { token });
  const keywords = await get(`${P.profile}/get/keywords`, { token });
  return {
    games: (games.data?.games ?? []).slice(0, 3).map((g) => g.gameId),
    keywords: (keywords.data?.keywords ?? []).slice(0, 5).map((k) => k.id ?? k.keywordId ?? k.keyword_id),
  };
}

/**
 * Registers, verifies and onboards one account.
 *
 * @returns {{email,password,username,token,userId}}
 */
async function createAccount(opts = {}) {
  const id = unique();
  const email = opts.email ?? `qa-${RUN}-${id}@${DOMAIN}`;
  const username = opts.username ?? `qa${id}`;
  const password = opts.password ?? PASSWORD;

  // Registration is retried on EMAIL_SEND_FAILED.
  //
  // Dead weight under MAIL_MODE=log, which is what compose sets and what makes this suite
  // fast and deterministic. It is kept for the case where it is pointed at a stack sending
  // real mail: registration rightly rolls its whole transaction back when the relay is slow
  // or throttling, and under a parallel run that made account creation fail intermittently
  // and look like a product bug. Re-registering an unverified address is explicitly
  // supported, so the retry is safe either way.
  let registered;
  for (let attempt = 1; attempt <= 3; attempt++) {
    registered = await post(`${P.auth}/register`, { email, password, acceptedTerms: true });
    if (registered.status === 200 || registered.status === 201) break;
    if (registered.code !== CODE.EMAIL_SEND_FAILED && registered.status !== 503) break;
    await new Promise((r) => setTimeout(r, 400 * attempt));
  }
  if (registered.status !== 200 && registered.status !== 201) {
    throw new Error(`register failed for ${email}: ${registered.status} ${registered.text}`);
  }

  const code = db.verificationCode(email);
  if (!code) throw new Error(`no verification code stored for ${email}`);

  const verified = await post(`${P.auth}/verify`, { email, verificationCode: Number(code) });
  const token = verified.data?.token ?? verified.data?.accessToken ?? verified.data;
  if (verified.status !== 200 || typeof token !== 'string') {
    throw new Error(`verify failed for ${email}: ${verified.status} ${verified.text}`);
  }

  const named = await post(`${P.auth}/username`, { username }, { token });
  if (named.status !== 200) throw new Error(`username failed: ${named.status} ${named.text}`);

  const { games, keywords } = await catalogue(token);
  const details = await post(
    `${P.auth}/details`,
    {
      birthDate: opts.birthDate ?? adultBirthDate(),
      country: opts.country ?? 'Finland',
      gender: opts.gender ?? 'O',
      favoriteGames: games,
      keywords,
      platforms: opts.platforms ?? ['PC'],
    },
    { token },
  );
  if (details.status !== 200) throw new Error(`details failed: ${details.status} ${details.text}`);

  return { email, password, username, token, userId: db.gamerId(email), games, keywords };
}

/** Removes the accounts *this process* created. See RUN above for why it is scoped. */
function cleanup() {
  db.deleteGamers(`qa-${RUN}-%@${DOMAIN}`);
}

/** Removes every QA account, from any run. For the runner's final sweep, not for tests. */
function cleanupAll() {
  db.deleteGamers(`%@${DOMAIN}`);
}

module.exports = { createAccount, cleanup, cleanupAll, adultBirthDate, unique, RUN, DOMAIN, PASSWORD, CODE };
