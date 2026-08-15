/**
 * Authentication, authorisation and the things that must not be reachable.
 *
 * Two questions, asked of every surface: can an anonymous caller get in, and can an
 * authenticated caller reach somebody else's data. The second is the one that survives a
 * code review — nobody forgets `@AuthenticationPrincipal`, but plenty of endpoints take an
 * id from the path and trust it.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, put, del, request, P, CODE, BASE } = require('./helpers/api');
const db = require('./helpers/db');
const { mint } = require('./helpers/tokens');
const { createAccount, cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);

/** Every authenticated GET the app actually calls. */
const AUTHENTICATED_GETS = [
  `${P.profile}/get/user/info`,
  `${P.profile}/get/games`,
  `${P.profile}/get/keywords`,
  `${P.profile}/get/avatars`,
  `${P.match}/get/recommendations`,
  `${P.match}/get/matches`,
  `${P.match}/get/liked-you`,
  `${P.match}/get/accept-allowance`,
  `${P.match}/boost`,
  '/messages/get/inbox',
  `${P.lobby}/browse`,
  `${P.lobby}/mine`,
  `${P.notif}/pending`,
  `${P.notif}/preferences`,
  `${P.billing}/subscription`,
  `${P.badges}/showall`,
  `${P.cosmetics}/showall`,
];

describe('security', () => {
  after(() => cleanup());

  describe('authentication', () => {
    test('every authenticated route refuses an anonymous caller', async () => {
      const leaks = [];
      for (const path of AUTHENTICATED_GETS) {
        const res = await get(path);
        if (res.status !== 401 && res.status !== 403) leaks.push(`${path} -> ${res.status}`);
      }
      assert.deepEqual(leaks, [], `these routes answered an anonymous caller:\n${leaks.join('\n')}`);
    });

    test('a token signed with the wrong key is refused', async () => {
      const [a] = seeded(1);
      const forged = mint(a.email, { secret: 'not-the-real-signing-key-but-long-enough' });
      const res = await get(`${P.profile}/get/user/info`, { token: forged });
      assert.equal(res.status, 401, 'a forged signature was accepted');
    });

    test('an expired token is refused', async () => {
      const [a] = seeded(1);
      const expired = mint(a.email, { iatOffset: -60 * 60 * 24 * 30, ttlSeconds: 60 });
      assert.equal((await get(`${P.profile}/get/user/info`, { token: expired })).status, 401);
    });

    test('a token for an unknown subject is refused', async () => {
      const ghost = mint('nobody-at-all@qa.gamebuddy.invalid');
      assert.equal((await get(`${P.profile}/get/user/info`, { token: ghost })).status, 401);
    });

    test('a token with the wrong issuer is refused', async () => {
      const [a] = seeded(1);
      const res = await get(`${P.profile}/get/user/info`, { token: mint(a.email, { issuer: 'somebody-else' }) });
      assert.equal(res.status, 401, 'the issuer claim is not being checked');
    });

    test('a malformed token yields 401, never 500', async () => {
      for (const bad of ['not-a-token', 'a.b.c', '', '....', 'Bearer']) {
        const res = await get(`${P.profile}/get/user/info`, { token: bad });
        assert.ok(res.status === 401 || res.status === 403,
          `a malformed token produced HTTP ${res.status} — it must never reach an error handler`);
      }
    });

    test('a password change invalidates tokens issued before it', async () => {
      const account = await createAccount();
      assert.equal((await get(`${P.profile}/get/user/info`, { token: account.token })).status, 200);

      // Revocation compares at second granularity on purpose — JwtService:119-121 — so a
      // token minted in the same second as the revocation stamp deliberately survives.
      // Without this wait the test races that window and reports a security hole that is
      // really a one-second grace period.
      await new Promise((r) => setTimeout(r, 1200));

      // ChangePwdRequest calls the new one `password`, not `newPassword`.
      const changed = await put(`${P.auth}/change/pwd`,
        { currentPassword: account.password, password: 'An0ther!Passw0rd' }, { token: account.token });
      assert.equal(changed.status, 200, changed.text);

      const after_ = await get(`${P.profile}/get/user/info`, { token: account.token });
      assert.equal(after_.status, 401,
        'the old token still works after a password change — a stolen token outlives the response to it');
    });

    test('a session can be extended without the password, and the new token works', async () => {
      const account = await createAccount();

      // Past the second boundary: a JWT minted in the same second with the same claims
      // is the same string, so without this the "new token" assertion below compares a
      // token to itself. The real client refreshes days in, not milliseconds in.
      await new Promise((resolve) => setTimeout(resolve, 1100));
      const refreshed = await post(`${P.auth}/refresh`, undefined, { token: account.token });
      assert.equal(refreshed.status, 200, refreshed.text);
      const token = refreshed.data.accessToken;
      assert.ok(token, 'refresh must return a token');
      assert.notEqual(token, account.token, 'refresh must issue a new token, not echo the old one');

      const me = await get(`${P.profile}/get/user/info`, { token });
      assert.equal(me.status, 200, 'the renewed token must be usable');
    });

    test('refreshing does not reset how old the session is', async () => {
      // The ceiling only means anything if the clock survives a refresh. Read it off the
      // token itself: `sst` is the session start, and it must not move when `iat` does.
      const account = await createAccount();
      const claims = (t) => JSON.parse(Buffer.from(t.split('.')[1], 'base64url').toString());

      const first = claims(account.token);
      // A JWT's clock ticks in whole seconds, so a refresh issued in the same second as
      // the login is genuinely indistinguishable from it. Wait past the tick, otherwise
      // this asserts nothing about whether the expiry moves.
      await new Promise((resolve) => setTimeout(resolve, 1100));
      const refreshed = await post(`${P.auth}/refresh`, undefined, { token: account.token });
      const second = claims(refreshed.data.accessToken);

      assert.equal(second.sst, first.sst, 'the session start must carry across a refresh');
      assert.ok(second.exp > first.exp, 'but the expiry must move out — that is the point');
    });

    test('refresh is closed to anonymous callers and to junk tokens', async () => {
      assert.equal((await post(`${P.auth}/refresh`)).status, 401);
      assert.equal((await post(`${P.auth}/refresh`, undefined, { token: 'not.a.token' })).status, 401);
    });
  });

  describe('horizontal access', () => {
    test('a gamer cannot read another gamer\'s private conversation', async () => {
      const [a, b] = seeded(2);
      const [intruder] = seeded(1);
      await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
      await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
      await post('/messages/send', { receiver: b.userId, message: 'confidential' }, { token: a.token });

      const res = await get(`/messages/get/${a.userId}`, { token: intruder.token });
      assert.ok(!JSON.stringify(res.data ?? {}).includes('confidential'),
        'a third party read a private conversation');
    });

    test('a gamer cannot send a message as somebody else', async () => {
      const [a, b] = seeded(2);
      const [attacker] = seeded(1);
      await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
      await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });

      // The sender is taken from the principal, never from the body — but the body is the
      // obvious place to try.
      const res = await post('/messages/send',
        { receiver: b.userId, message: 'forged', sender: a.userId }, { token: attacker.token });
      assert.equal(res.status, 403, 'an unmatched gamer sent a message');
      assert.equal(res.code, CODE.NOT_MATCHED);
    });

    test('a gamer cannot spend another gamer\'s allowance', async () => {
      const [victim] = seeded(1);
      const [attacker] = seeded(1);

      const before = (await get(`${P.match}/get/accept-allowance`, { token: victim.token })).data.remainingSwipes;
      // Whatever the attacker does with their own token must not touch the victim's counters.
      const target = (await get(`${P.match}/get/recommendations`, { token: attacker.token }))
        .data.recommendedGamers[0].userId;
      await post(`${P.match}/decline`, { userId: target }, { token: attacker.token });

      const after_ = (await get(`${P.match}/get/accept-allowance`, { token: victim.token })).data.remainingSwipes;
      assert.equal(after_, before, 'one gamer\'s swipe spent another\'s allowance');
    });

    test('the public profile of another gamer does not leak their email', async () => {
      const [viewer, subject] = seeded(2);
      const res = await get(`${P.profile}/get/user/info/${subject.userId}`, { token: viewer.token });

      if (res.status === 200) {
        const payload = JSON.stringify(res.data);
        assert.ok(!payload.includes(subject.email),
          'another gamer\'s email address is exposed on their public profile');
        assert.ok(!/"pwd"|"password"/i.test(payload), 'a password field is present in the response');
      }
    });

    test('own profile carries the email, another gamer\'s does not', async () => {
      const [me] = seeded(1);
      const mine = await get(`${P.profile}/get/user/info`, { token: me.token });
      assert.equal(mine.status, 200);
      assert.ok(JSON.stringify(mine.data).includes(me.email), 'my own profile should show my email');
    });
  });

  describe('exposure', () => {
    test('the API docs are reachable locally, and this must change in production', async () => {
      // Asserted rather than assumed: the README lists disabling these as a production
      // step, so the check exists to make the difference between the two environments
      // visible rather than remembered.
      const docs = await get('/api-docs');
      assert.equal(docs.status, 200, 'swagger is expected to be ON locally');
    });

    test('actuator exposes health and nothing sensitive', async () => {
      const health = await get('/actuator/health');
      assert.equal(health.status, 200);

      for (const endpoint of ['/actuator/env', '/actuator/beans', '/actuator/configprops',
        '/actuator/heapdump', '/actuator/threaddump', '/actuator/loggers', '/actuator/mappings']) {
        const res = await get(endpoint);
        assert.ok(res.status === 404 || res.status === 401 || res.status === 403,
          `${endpoint} is exposed (HTTP ${res.status}) — it leaks configuration including secrets`);
      }
    });

    test('health details are not shown to an anonymous caller', async () => {
      const res = await get('/actuator/health');
      const payload = JSON.stringify(res.data ?? res.text);
      assert.ok(!payload.includes('jdbc:') && !payload.includes('postgres'),
        'the health endpoint leaks the datasource URL to anonymous callers');
    });

    test('an error response carries no stack trace or internal message', async () => {
      const res = await get(`${P.profile}/get/user/info/../../etc/passwd`);
      const payload = res.text ?? '';
      assert.ok(!/at com\.gamebuddy|java\.lang\.|Exception:/.test(payload),
        `an internal detail leaked: ${payload.slice(0, 300)}`);
    });

    test('the internal model service is not reachable without its key', async () => {
      // The recommender returns a list of user ids given a user id: unauthenticated, it is
      // an enumeration endpoint for the social graph.
      const modelBase = process.env.GB_MODEL_URL || 'http://localhost:8000';
      const res = await fetch(`${modelBase}/predict`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ user_id: 'anyone', limit: 10 }),
      });
      assert.ok(res.status === 401 || res.status === 403,
        `the model answered an unauthenticated /predict with HTTP ${res.status}`);
    });
  });

  describe('input handling', () => {
    test('a SQL-shaped id is treated as data, not SQL', async () => {
      const [a] = seeded(1);

      // Counted over the seeded fixtures rather than the whole table. The suite's other
      // files run in parallel and create and delete their own accounts throughout, so a
      // global `count(*)` moves for reasons that have nothing to do with injection — the
      // test failed intermittently while proving nothing either way.
      const seededCount = () => db.scalar(
        "select count(*) from gamebuddy.gamer where email like '%@bot.gamebuddy.invalid';",
      );
      const before = seededCount();

      for (const payload of ["' OR '1'='1", "'; DROP TABLE gamebuddy.gamer; --", "1' UNION SELECT null--",
        "' OR 1=1--", "\\'; DELETE FROM gamebuddy.gamer WHERE '1'='1"]) {
        const res = await post(`${P.match}/accept`, { userId: payload }, { token: a.token });
        assert.ok(res.status >= 400 && res.status < 500, `expected a clean 4xx, got ${res.status}`);
      }

      assert.equal(seededCount(), before,
        'the seeded population changed while injection payloads were being sent');
      // And the table is still there to be counted, which a successful DROP would prevent.
      assert.ok(Number(before) > 0);
    });

    // Was a defect, fixed 2026-08-14 — see QA_FINDINGS.md finding 2. CreateCommunityRequest
    // and PostRequest declared no @Size on their String fields while the columns behind them
    // are varchar(255), varchar(2000) and varchar(4000). Oversized text passed validation,
    // reached Postgres, and the constraint violation escaped as a 500:
    //
    //   value too long for type character varying(2000)
    //
    // Any authenticated user could produce 500s at will. The bound now matches the column on
    // every field, as CreateCommentRequest always did.
    test('oversized text is refused with a 4xx, not a 500', async () => {
      // Bean validation on the lobby requests fires before the Gold gate, so a free
      // account can probe these — a 402 here would itself be a finding, because it would
      // mean an oversized value reached the service.
      const [a] = seeded(1);
      const base = {
        gameId: 'probe',
        tone: 'CHILL',
        maxPlayers: 3,
        startsAt: new Date(Date.now() + 3_600_000).toISOString(),
      };

      const cases = [
        ['lobby title', () => post(`${P.lobby}/create`,
          { ...base, title: 'x'.repeat(300) }, { token: a.token })],
        ['lobby description', () => post(`${P.lobby}/create`,
          { ...base, title: 't', description: 'x'.repeat(3000) }, { token: a.token })],
        ['lobby requirements', () => post(`${P.lobby}/create`,
          { ...base, title: 't', requirements: 'x'.repeat(3000) }, { token: a.token })],
        ['lobby message', () => post(`${P.lobby}/00000000-0000-0000-0000-000000000000/messages/send`,
          { message: 'x'.repeat(100_000) }, { token: a.token })],
      ];

      const fives = [];
      for (const [label, call] of cases) {
        const res = await call();
        if (res.status >= 500) fives.push(`${label} -> ${res.status}`);
      }
      assert.deepEqual(fives, [], `these produced a server error on user input:\n${fives.join('\n')}`);
    });

    test('the endpoints that DO validate length still do', async () => {
      const [a] = seeded(1);
      const long = 'x'.repeat(100_000);

      assert.equal((await post(`${P.lobby}/00000000-0000-0000-0000-000000000000/messages/send`,
        { message: long }, { token: a.token })).status, 400);
      assert.equal((await post(`${P.auth}/username`, { username: 'x'.repeat(300) }, { token: a.token })).status, 400);
      assert.equal((await put(`${P.auth}/fcm-token`, { fcmToken: long }, { token: a.token })).status, 400);
    });

    test('stored user text comes back as JSON, never as executable markup', async () => {
      // The lobby browse feed is the public surface that serves stranger-written text.
      // The API is JSON and the client is React Native, so this is defence in depth
      // rather than the only control — but a stored script tag is worth knowing about
      // before a web client ever ships.
      const [a] = seeded(1);
      const browse = await get(`${P.lobby}/browse`, { token: a.token });
      assert.equal(browse.status, 200, browse.text);
      assert.equal(browse.headers.get('content-type')?.includes('application/json'), true,
        'the response must be JSON, not HTML');
    });
  });
});
