/**
 * Registration, verification and onboarding.
 *
 * This is the flow every other suite depends on, and the one with the most ways to leave a
 * half-created account behind. The assertions here are mostly about *refusals*: the states
 * the flow must not allow, because an account that reaches the app without an owned
 * address, an age, or a completed profile is a account that breaks something further in.
 */

const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { post, get, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { createAccount, cleanup, unique, adultBirthDate, DOMAIN, PASSWORD } = require('./helpers/accounts');

describe('signup', () => {
  after(() => cleanup());

  test('register issues a code and returns a user id, without a token', async () => {
    const email = `qa-${unique()}@${DOMAIN}`;
    const res = await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });

    assert.equal(res.status, 201);
    assert.equal(res.code, CODE.SUCCESS);
    assert.ok(res.data.userId, 'register must return the new user id');
    assert.equal(res.data.accessToken, undefined, 'register must not hand out a token before verification');

    const code = db.verificationCode(email);
    assert.ok(code && String(code).length === 6, `a 6-digit code must be stored; got ${code}`);
  });

  test('registering without accepting the terms is refused', async () => {
    const res = await post(`${P.auth}/register`, {
      email: `qa-${unique()}@${DOMAIN}`, password: PASSWORD, acceptedTerms: false,
    });
    assert.notEqual(res.status, 201, 'terms acceptance is recorded for GDPR; it cannot be optional');
    assert.ok(res.status >= 400 && res.status < 500, `expected a 4xx, got ${res.status}`);
  });

  test('a weak password is refused', async () => {
    const res = await post(`${P.auth}/register`, {
      email: `qa-${unique()}@${DOMAIN}`, password: 'short', acceptedTerms: true,
    });
    assert.ok(res.status >= 400 && res.status < 500, `expected a 4xx, got ${res.status}`);
  });

  test('an unverified email may be re-claimed, a verified one may not', async () => {
    const email = `qa-${unique()}@${DOMAIN}`;
    await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });

    // Registering again over an *unverified* account is deliberate: without it, a failed
    // send would leave an address nobody could ever register (DefaultAuthService:174-181).
    const reclaimed = await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
    assert.equal(reclaimed.status, 201, 'an unverified account must be re-claimable');

    // Once verified it belongs to somebody, and the answer changes.
    await post(`${P.auth}/verify`, { email, verificationCode: Number(db.verificationCode(email)) });
    const taken = await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
    assert.equal(taken.status, 409);
    assert.equal(taken.code, CODE.EMAIL_EXISTS);
  });

  test('a wrong verification code is refused and does not verify the account', async () => {
    const email = `qa-${unique()}@${DOMAIN}`;
    await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });

    const real = Number(db.verificationCode(email));
    const wrong = real === 999999 ? 111111 : real + 1;

    const res = await post(`${P.auth}/verify`, { email, verificationCode: wrong });
    assert.equal(res.status, 400);
    assert.equal(res.code, CODE.VERIFICATION_CODE_NOT_FOUND);
    assert.equal(db.scalar(`select is_verified from gamebuddy.gamer where email = '${db.esc(email)}';`), 'f');
  });

  test('login is refused until onboarding completes, then succeeds', async () => {
    const email = `qa-${unique()}@${DOMAIN}`;
    await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
    const verified = await post(`${P.auth}/verify`, {
      email, verificationCode: Number(db.verificationCode(email)),
    });
    assert.equal(verified.status, 200);
    const token = verified.data.accessToken;

    // Verified but not onboarded. The README calls this out as intended behaviour rather
    // than a fault, so it is asserted rather than worked around.
    const early = await post(`${P.auth}/login`, { usernameOrEmail: email, password: PASSWORD });
    assert.equal(early.status, 403);
    assert.equal(early.code, CODE.USER_NOT_COMPLETED, 'expected "Registration not finished"');

    const username = `qa${unique()}`;
    assert.equal((await post(`${P.auth}/username`, { username }, { token })).status, 200);

    const games = (await get(`${P.profile}/get/games`, { token })).data.games.slice(0, 3).map((g) => g.gameId);
    const keywords = (await get(`${P.profile}/get/keywords`, { token })).data.keywords.slice(0, 5).map((k) => k.id);
    const details = await post(`${P.auth}/details`, {
      birthDate: adultBirthDate(), country: 'Finland', gender: 'O',
      favoriteGames: games, keywords, platforms: ['PC'],
    }, { token });
    assert.equal(details.status, 200);

    const now = await post(`${P.auth}/login`, { usernameOrEmail: email, password: PASSWORD });
    assert.equal(now.status, 200, 'login must work once onboarding is complete');
    assert.ok(now.data.accessToken, 'login must return a token');
  });

  test('login with the wrong password is refused', async () => {
    const account = await createAccount();
    const res = await post(`${P.auth}/login`, { usernameOrEmail: account.email, password: 'Wr0ng!Passw0rd' });
    assert.equal(res.status, 401);
    assert.equal(res.code, CODE.WRONG_PASSWORD);
  });

  test('a duplicate username is refused, case-insensitively', async () => {
    const first = await createAccount();

    const email = `qa-${unique()}@${DOMAIN}`;
    await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
    const token = (await post(`${P.auth}/verify`, {
      email, verificationCode: Number(db.verificationCode(email)),
    })).data.accessToken;

    const same = await post(`${P.auth}/username`, { username: first.username }, { token });
    assert.equal(same.status, 409);
    assert.equal(same.code, CODE.USERNAME_EXISTS);

    // upgrade-2026-14 made usernames case-insensitively unique. Two accounts differing
    // only in case would be a straightforward impersonation vector.
    const upper = await post(`${P.auth}/username`, { username: first.username.toUpperCase() }, { token });
    assert.equal(upper.status, 409, 'usernames must be unique case-insensitively');
  });

  describe('adults only', () => {
    // GameBuddy is 18+ by design, not by store rating. The check has to be at the point
    // the age is set, because that is the only place a client cannot route around.
    for (const [label, years] of [['a 12-year-old', 12], ['a 17-year-old', 17]]) {
      test(`${label} is refused at /auth/details`, async () => {
        const email = `qa-${unique()}@${DOMAIN}`;
        await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
        const token = (await post(`${P.auth}/verify`, {
          email, verificationCode: Number(db.verificationCode(email)),
        })).data.accessToken;
        await post(`${P.auth}/username`, { username: `qa${unique()}` }, { token });

        const games = (await get(`${P.profile}/get/games`, { token })).data.games.slice(0, 3).map((g) => g.gameId);
        const keywords = (await get(`${P.profile}/get/keywords`, { token })).data.keywords.slice(0, 5).map((k) => k.id);
        const res = await post(`${P.auth}/details`, {
          birthDate: adultBirthDate(years), country: 'Finland', gender: 'O',
          favoriteGames: games, keywords, platforms: ['PC'],
        }, { token });

        assert.ok(res.status >= 400 && res.status < 500, `a minor must be refused; got ${res.status}`);
      });
    }
  });

  test('the onboarding minimums are enforced (3 games, 5 keywords, 1 platform)', async () => {
    const email = `qa-${unique()}@${DOMAIN}`;
    await post(`${P.auth}/register`, { email, password: PASSWORD, acceptedTerms: true });
    const token = (await post(`${P.auth}/verify`, {
      email, verificationCode: Number(db.verificationCode(email)),
    })).data.accessToken;
    await post(`${P.auth}/username`, { username: `qa${unique()}` }, { token });

    const games = (await get(`${P.profile}/get/games`, { token })).data.games.slice(0, 3).map((g) => g.gameId);
    const keywords = (await get(`${P.profile}/get/keywords`, { token })).data.keywords.slice(0, 5).map((k) => k.id);
    const base = { birthDate: adultBirthDate(), country: 'Finland', gender: 'O' };

    const tooFewGames = await post(`${P.auth}/details`,
      { ...base, favoriteGames: games.slice(0, 2), keywords, platforms: ['PC'] }, { token });
    assert.ok(tooFewGames.status >= 400, 'fewer than 3 games must be refused');

    const tooFewKeywords = await post(`${P.auth}/details`,
      { ...base, favoriteGames: games, keywords: keywords.slice(0, 4), platforms: ['PC'] }, { token });
    assert.ok(tooFewKeywords.status >= 400, 'fewer than 5 keywords must be refused');

    const noPlatform = await post(`${P.auth}/details`,
      { ...base, favoriteGames: games, keywords, platforms: [] }, { token });
    assert.ok(noPlatform.status >= 400, 'no platform must be refused');
  });

  test('/auth/sendCode is rate limited', async () => {
    const account = await createAccount();

    // AuthRateLimitConfig.sendCode allows 6 calls per 15 minutes. The limit exists so the
    // endpoint cannot be used to mail-bomb a registered address. Spend the whole budget and
    // then one more: the refusal is the call after the last permit, so a loop of exactly
    // BUDGET can never see it.
    const BUDGET = 6;
    const codes = [];
    for (let i = 0; i < BUDGET + 1; i++) {
      const res = await post(`${P.auth}/sendCode`, { email: account.email, isRegister: false });
      codes.push(res.status);
    }
    assert.ok(
      codes.includes(429),
      `expected a 429 once the budget of ${BUDGET} was spent; got ${codes.join(',')}`,
    );
  });
});
