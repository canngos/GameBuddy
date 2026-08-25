/**
 * Swiping, matching, and the two limits that sit on top of them.
 *
 * There are two independent caps and they are frequently confused, so both are asserted
 * separately: the *daily allowance* is a monetisation lever measured in days
 * (SwipeQuota), and the *rate limiter* is an abuse control measured in a minute
 * (MatchRateLimitConfig, 120/min). A script hits the second long before the first; a person
 * hits the first and never the second.
 *
 * These limits also set the shape of the load tests — a load script that swipes hard as a
 * handful of identities measures the rate limiter and nothing else.
 */

const { test, describe, after } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { cleanup } = require('./helpers/accounts');
const { grantGold } = require('./helpers/billing');
const fixtures = require('./helpers/fixtures');

/** Untouched seeded fixtures. See helpers/fixtures.js for why they must be untouched. */
const seeded = (n) => fixtures.take(n);

const feed = (token) => get(`${P.match}/get/recommendations`, { token });
const ids = (res) => (res.data?.recommendedGamers ?? []).map((g) => g.userId);
const allowance = (token) => get(`${P.match}/get/accept-allowance`, { token });

describe('swipe and match', () => {
  after(() => cleanup());

  test('the allowance reports a tier, a swipe budget and a reset time', async () => {
    const [a] = seeded(1);
    const res = await allowance(a.token);

    assert.equal(res.status, 200);
    assert.equal(res.data.tier, 'BASIC');
    assert.ok(Number.isInteger(res.data.remainingSwipes), 'remainingSwipes');
    assert.ok(Number.isInteger(res.data.remainingAccepts), 'remainingAccepts');
    assert.equal(res.data.unlimited, false);
    assert.ok(Date.parse(res.data.resetsAt) > Date.now(), 'resetsAt must be in the future');
    assert.ok(
      res.data.remainingAccepts <= res.data.remainingSwipes,
      'accepts cannot exceed swipes — every accept is also a swipe',
    );
  });

  test('a decline spends a swipe but not an accept', async () => {
    const [a] = seeded(1);
    const before = (await allowance(a.token)).data;
    const target = ids(await feed(a.token))[0];

    const res = await post(`${P.match}/decline`, { userId: target }, { token: a.token });
    assert.equal(res.status, 200);

    const after_ = (await allowance(a.token)).data;
    assert.equal(after_.remainingSwipes, before.remainingSwipes - 1, 'a decline costs one swipe');
    assert.equal(after_.remainingAccepts, before.remainingAccepts, 'a decline costs no accept');
  });

  test('an accept spends both', async () => {
    const [a] = seeded(1);
    const before = (await allowance(a.token)).data;
    const target = ids(await feed(a.token))[0];

    const res = await post(`${P.match}/accept`, { userId: target }, { token: a.token });
    assert.equal(res.status, 200);

    const after_ = (await allowance(a.token)).data;
    assert.equal(after_.remainingSwipes, before.remainingSwipes - 1);
    assert.equal(after_.remainingAccepts, before.remainingAccepts - 1);
  });

  test('a one-sided accept is not a match, and shows up in who-liked-you', async () => {
    const [a, b] = seeded(2);

    const accepted = await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
    assert.equal(accepted.status, 200);
    assert.notEqual(accepted.data?.isMatch, true, 'one accept is not a match');

    assert.deepEqual(ids(await get(`${P.match}/get/matches`, { token: a.token })), [],
      'a pending like must not appear as a match');

    const liked = await get(`${P.match}/get/liked-you`, { token: b.token });
    assert.equal(liked.status, 200, 'B must be able to see that somebody liked them');
  });

  test('a mutual accept creates a match on both sides', async () => {
    const [a, b] = seeded(2);

    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
    const mutual = await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
    assert.equal(mutual.status, 200);

    const aMatches = ids(await get(`${P.match}/get/matches`, { token: a.token }));
    const bMatches = ids(await get(`${P.match}/get/matches`, { token: b.token }));
    assert.ok(aMatches.includes(b.userId), 'B must appear in A\'s matches');
    assert.ok(bMatches.includes(a.userId), 'A must appear in B\'s matches');

    // The room is NOT created here. ChatRoomService.findOrCreate is called from
    // ChatMessageService.send, so a room exists only once somebody speaks — matches that
    // never talk cost no rows. Asserted so that moving room creation earlier is a
    // deliberate change rather than an accident; 04-chat covers the creation itself.
    const rooms = db.scalar(
      'select count(*) from gamebuddy.chat_participant p1 ' +
      'join gamebuddy.chat_participant p2 on p1.room_id = p2.room_id ' +
      `where p1.user_id = '${db.esc(a.userId)}' and p2.user_id = '${db.esc(b.userId)}';`,
    );
    assert.equal(rooms, '0', 'chat rooms are created lazily, on the first message');
  });

  test('accepting the same gamer twice does not create a second match', async () => {
    const [a, b] = seeded(2);

    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
    await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });

    const matches = ids(await get(`${P.match}/get/matches`, { token: a.token }));
    assert.equal(matches.filter((id) => id === b.userId).length, 1, 'duplicate match row');
  });

  test('accepting a gamer who does not exist is refused', async () => {
    const [a] = seeded(1);
    const res = await post(`${P.match}/accept`,
      { userId: '00000000-0000-0000-0000-000000000000' }, { token: a.token });
    assert.ok(res.status >= 400 && res.status < 500, `expected 4xx, got ${res.status}`);
  });

  test('accepting yourself is refused', async () => {
    const [a] = seeded(1);
    const res = await post(`${P.match}/accept`, { userId: a.userId }, { token: a.token });
    assert.ok(res.status >= 400 && res.status < 500,
      `a gamer must not be able to match with themselves; got ${res.status}`);
  });

  test('the decision rate limiter fires once its budget is spent', async () => {
    // Deliberately serial: the limiter is a sliding window and a burst of parallel
    // requests would race it, making the test flaky about *where* it trips rather than
    // whether it does.
    //
    // MatchRateLimitConfig.decision allows 120 a minute. The refusal is the call after the
    // last permit, so the loop has to be able to reach BUDGET + 1 or it can never see one.
    // Gold, because the two caps are ordered against each other: BASIC gets 50 swipes a
    // day and the limiter allows 120 a minute, so a free account is stopped by the daily
    // allowance (163) and can never reach the limiter at all. Only an unmetered tier can
    // demonstrate that this control exists.
    //
    // The feed answers a page at a time and declining removes people from it, so one page
    // cannot supply the whole budget — refill from a fresh page as it empties.
    const BUDGET = 120;
    const [a] = seeded(1);
    await grantGold(a.userId);
    const seen = new Set();
    let queue = [];

    const nextTarget = async () => {
      if (queue.length === 0) {
        queue = ids(await feed(a.token)).filter((id) => !seen.has(id));
      }
      return queue.shift();
    };

    const statuses = [];
    for (let i = 0; i < BUDGET + 1; i++) {
      const target = await nextTarget();
      assert.ok(target, `ran out of candidates after ${i} decisions, before the limit fired`);
      seen.add(target);

      const res = await post(`${P.match}/decline`, { userId: target }, { token: a.token });
      statuses.push(res.status);
      if (res.status === 429) {
        assert.equal(res.code, CODE.RATE_LIMITED, 'a rate-limited decision must carry code 146');
        break;
      }
    }

    assert.ok(statuses.includes(429),
      `expected a 429 once the budget of ${BUDGET} was spent; got ${statuses.filter((s) => s !== 200).length} non-200s`);
    assert.ok(statuses.filter((s) => s === 200).length >= BUDGET * 0.8,
      'the limiter fired far too early — a real user swiping quickly would hit it');
  });

  test('swiping requires authentication', async () => {
    const res = await post(`${P.match}/accept`, { userId: 'anything' });
    assert.equal(res.status, 401);
  });

  test('who-liked-you does not leak identities to a free account', async () => {
    const [a, b] = seeded(2);
    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });

    const liked = await get(`${P.match}/get/liked-you`, { token: b.token });
    assert.equal(liked.status, 200);

    // The whole point of the unlock is that the identity is withheld until it is paid for
    // or earned. If the id is in the payload, the paywall is decoration.
    const payload = JSON.stringify(liked.data ?? {});
    assert.ok(!payload.includes(a.userId),
      'a locked admirer\'s user id must not be present in the response body');
  });
});
