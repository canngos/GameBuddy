/**
 * Entitlements, the RevenueCat webhook, and the coin economy.
 *
 * Receipt verification is RevenueCat's job now, which makes this webhook the single path
 * that can grant a paid entitlement — and the shared token in its Authorization header the
 * only thing standing between an anonymous POST and free Gold. So the first assertions
 * here are about the ways it must refuse, and the entitlement tests then go through the
 * webhook rather than writing a tier straight into the database, because a test that sets
 * the tier by hand proves nothing about how the tier is actually granted.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const { get, post, request, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { env } = require('./helpers/tokens');
const { cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);

/**
 * The webhook token.
 *
 * Compose defaults this to `local-development-webhook-token` when .env leaves it empty,
 * which is exactly what an unconfigured checkout gives — so the fallback is not a guess,
 * it is the documented default in docker-compose.yml.
 */
const WEBHOOK_TOKEN = env().REVENUECAT_WEBHOOK_TOKEN || 'local-development-webhook-token';

const PRODUCTS = {
  goldMonthly: 'gamebuddy.gold.monthly',
  goldYearly: 'gamebuddy.gold.yearly',
  coinsSmall: 'gamebuddy.coins.500',
  coinsMega: 'gamebuddy.coins.7000',
};

/** A RevenueCat event, shaped exactly as their webhook sends it. */
function event(type, appUserId, overrides = {}) {
  const now = Date.now();
  return {
    api_version: '1.0',
    event: {
      id: crypto.randomUUID(),
      type,
      app_user_id: appUserId,
      product_id: PRODUCTS.goldMonthly,
      purchased_at_ms: now,
      expiration_at_ms: now + 30 * 24 * 60 * 60 * 1000,
      store: 'PLAY_STORE',
      transaction_id: crypto.randomUUID(),
      original_transaction_id: crypto.randomUUID(),
      entitlement_ids: ['gold'],
      period_type: 'NORMAL',
      ...overrides,
    },
  };
}

const webhook = (payload, token = WEBHOOK_TOKEN) =>
  request('POST', `${P.billing}/revenuecat/webhook`, {
    body: payload,
    headers: token === null ? {} : { Authorization: token },
  });

/**
 * The tier as the product sees it.
 *
 * Deliberately read through the API rather than from `gamer.subscription_tier`. That
 * column is not the source of truth: expiry works by moving `subscription_expires_at` into
 * the past and the effective tier is derived from it, so the column still reads GOLD for
 * an account whose Gold ended months ago. Asserting on the column reports a revocation
 * failure that is not happening — and anything counting paying users straight from the
 * database will over-count them for the same reason.
 */
async function tierOf(token) {
  const res = await get(`${P.billing}/subscription`, { token });
  assert.equal(res.status, 200, res.text);
  return res.data.tier;
}

/** The raw column, only for asserting that an unauthorised call changed nothing. */
const rawTier = (userId) =>
  db.scalar(`select subscription_tier from gamebuddy.gamer where user_id = '${db.esc(userId)}';`);

describe('billing', () => {
  after(() => cleanup());

  describe('the webhook is the only way in', () => {
    test('an anonymous POST is refused', async () => {
      const [a] = seeded(1);
      const res = await webhook(event('INITIAL_PURCHASE', a.userId), null);

      assert.equal(res.status, 401, 'the webhook granted an entitlement to an unauthenticated caller');
      assert.equal(rawTier(a.userId), 'BASIC', 'no tier may be granted by an unauthorised call');
    });

    test('a wrong token is refused', async () => {
      const [a] = seeded(1);
      const res = await webhook(event('INITIAL_PURCHASE', a.userId), 'not-the-token');

      assert.equal(res.status, 401);
      assert.equal(rawTier(a.userId), 'BASIC');
    });

    test('a nearly-right token is refused', async () => {
      const [a] = seeded(1);
      // One character short. The comparison is constant-time, and it still has to be
      // *correct* — a prefix match would be the whole security model gone.
      const res = await webhook(event('INITIAL_PURCHASE', a.userId), WEBHOOK_TOKEN.slice(0, -1));

      assert.equal(res.status, 401);
      assert.equal(rawTier(a.userId), 'BASIC');
    });

    test('the correct token is accepted', async () => {
      const [a] = seeded(1);
      const res = await webhook(event('INITIAL_PURCHASE', a.userId));
      assert.equal(res.status, 200, res.text);
    });
  });

  describe('entitlements', () => {
    test('INITIAL_PURCHASE grants Gold, EXPIRATION takes it away', async () => {
      const [a] = seeded(1);
      assert.equal(await tierOf(a.token), 'BASIC');

      assert.equal((await webhook(event('INITIAL_PURCHASE', a.userId))).status, 200);
      assert.equal(await tierOf(a.token), 'GOLD', 'a purchase must grant the tier');

      assert.equal((await webhook(event('EXPIRATION', a.userId))).status, 200);
      assert.equal(await tierOf(a.token), 'BASIC', 'an expiry must revoke the tier');

      // And the entitlement it bought goes with it.
      assert.equal((await get(`${P.match}/get/accept-allowance`, { token: a.token })).data.unlimited, false,
        'unlimited swipes survived the expiry');
    });

    test('CANCELLATION does not revoke until the period ends', async () => {
      const [a] = seeded(1);
      await webhook(event('INITIAL_PURCHASE', a.userId));
      assert.equal(await tierOf(a.token), 'GOLD');

      // Cancelling means "do not renew", not "stop now" — the user paid for the period and
      // revoking here would take away something already bought.
      assert.equal((await webhook(event('CANCELLATION', a.userId))).status, 200);
      assert.equal(await tierOf(a.token), 'GOLD',
        'a cancellation revoked the tier before the period it was paid for had ended');
    });

    test('an unknown product grants nothing', async () => {
      const [a] = seeded(1);
      const res = await webhook(event('INITIAL_PURCHASE', a.userId, { product_id: 'gamebuddy.not.a.product' }));

      // 200 deliberately: a non-200 makes RevenueCat retry an event that will never
      // succeed, and eventually disables the webhook for everyone.
      assert.equal(res.status, 200, 'an unknown product must be acknowledged, not retried forever');
      assert.equal(await tierOf(a.token), 'BASIC', 'an unknown product granted a tier');
    });

    test('an event for an unknown account is acknowledged and grants nothing', async () => {
      const res = await webhook(event('INITIAL_PURCHASE', '00000000-0000-0000-0000-000000000000'));
      assert.equal(res.status, 200, 'an unknown app_user_id must not make RevenueCat retry forever');
    });

    test('a coin pack credits coins and not a tier', async () => {
      const [a] = seeded(1);
      const before = Number(db.scalar(`select coin from gamebuddy.gamer where user_id = '${db.esc(a.userId)}';`));

      const res = await webhook(event('NON_RENEWING_PURCHASE', a.userId, { product_id: PRODUCTS.coinsSmall }));
      assert.equal(res.status, 200, res.text);

      const after_ = Number(db.scalar(`select coin from gamebuddy.gamer where user_id = '${db.esc(a.userId)}';`));
      assert.equal(after_, before + 500, 'the coin pack did not credit 500 coins');
      assert.equal(await tierOf(a.token), 'BASIC', 'a consumable must not grant a subscription tier');
    });

    // The pack added by the 2026-09-07 reprice. Worth its own case rather than trusting the
    // one above to cover it: a coin pack the enum does not know verifies against the store,
    // logs "unknown product", and grants nothing to somebody who has already been charged.
    // That is the failure Product's own Javadoc says it exists to prevent, and GOLD_WEEKLY
    // records it having actually happened once.
    test('the largest coin pack credits its full 7000', async () => {
      const [a] = seeded(1);
      const before = Number(db.scalar(`select coin from gamebuddy.gamer where user_id = '${db.esc(a.userId)}';`));

      const res = await webhook(event('NON_RENEWING_PURCHASE', a.userId, { product_id: PRODUCTS.coinsMega }));
      assert.equal(res.status, 200, res.text);

      const after_ = Number(db.scalar(`select coin from gamebuddy.gamer where user_id = '${db.esc(a.userId)}';`));
      assert.equal(after_, before + 7000, 'the mega pack did not credit 7000 coins');
      assert.equal(await tierOf(a.token), 'BASIC', 'a consumable must not grant a subscription tier');
    });
  });

  describe('what Gold actually buys', () => {
    test('a free account is refused the advanced filters', async () => {
      const [a] = seeded(1);
      const refused = await get(`${P.match}/get/recommendations?country=Finland`, { token: a.token });
      assert.equal(refused.status, 402);
      assert.equal(refused.code, CODE.SUBSCRIPTION_REQUIRED);
    });

    // Was the most serious defect this suite found, fixed 2026-08-14 — QA_FINDINGS.md #1.
    //
    // A narrowing filter used to be implemented by sending the model every gamer the filter
    // ruled *out* as an exclusion. The model caps that list at MAX_EXCLUSIONS = 10_000 and
    // answered 422 above it:
    //
    //   List should have at most 10000 items after validation, not 20001
    //
    // The backend turned that into RECOMMENDER_SERVICE_ERROR, so a paying subscriber was
    // told "Recommendation service unavailable" — the recommender blamed for a request the
    // backend built too large. It was a scaling bug, which is what made it dangerous: below
    // ~10,000 gamers every filter worked and the feature looked finished, while this
    // database holds 20,001 and three of the four were broken. `platform` still passed, and
    // only because enough gamers share a platform to keep the list under the cap.
    //
    // The backend now sends the eligible set instead, so this test is what keeps that
    // direction from being reversed — and running it against a population this size is the
    // point. It passes trivially on a small one.
    test('Gold unlocks the advanced filters', async () => {
      const [a] = seeded(1);
      await webhook(event('INITIAL_PURCHASE', a.userId));

      const gameId = (await get(`${P.profile}/get/games`, { token: a.token })).data.games[0].gameId;
      const broken = [];

      for (const query of ['?country=Finland', '?onlineNow=true', `?gameId=${gameId}`, '?platform=PC']) {
        const res = await get(`${P.match}/get/recommendations${query}`, { token: a.token });
        if (res.status !== 200) broken.push(`${query} -> ${res.status} ${res.code} ${res.message}`);
      }

      assert.deepEqual(broken, [],
        `Gold filters that a paying subscriber cannot use:\n${broken.join('\n')}`);
    });

    test('a filter that does return a page returns a correctly filtered one', async () => {
      // The half of the feature that works. Worth asserting separately so that fixing the
      // exclusion cap does not quietly break the filtering itself.
      const [a] = seeded(1);
      await webhook(event('INITIAL_PURCHASE', a.userId));

      const res = await get(`${P.match}/get/recommendations?platform=PC`, { token: a.token });
      assert.equal(res.status, 200, res.text);
      for (const candidate of res.data.recommendedGamers ?? []) {
        assert.ok((candidate.platforms ?? []).includes('PC'),
          `the platform filter returned somebody on ${JSON.stringify(candidate.platforms)}`);
      }
    });

    test('the swipe allowance becomes unlimited', async () => {
      const [a] = seeded(1);
      assert.equal((await get(`${P.match}/get/accept-allowance`, { token: a.token })).data.unlimited, false);

      await webhook(event('INITIAL_PURCHASE', a.userId));

      const allowance = await get(`${P.match}/get/accept-allowance`, { token: a.token });
      assert.equal(allowance.data.tier, 'GOLD');
      assert.equal(allowance.data.unlimited, true, 'Gold did not lift the daily swipe cap');
    });
  });

  describe('the coin economy', () => {
    test('the daily faucet pays once, then refuses', async () => {
      const [a] = seeded(1);
      const first = await post(`${P.coins}/earn/daily`, undefined, { token: a.token });
      assert.equal(first.status, 200, first.text);

      const second = await post(`${P.coins}/earn/daily`, undefined, { token: a.token });
      assert.ok(second.status >= 400, 'the daily reward paid out twice in one day');
    });

    test('every credit is written to the ledger', async () => {
      const [a] = seeded(1);
      await post(`${P.coins}/earn/daily`, undefined, { token: a.token });

      const entries = db.query(
        `select delta, reason from gamebuddy.coin_ledger where user_id = '${db.esc(a.userId)}';`,
      );
      assert.ok(entries.length > 0, 'a coin credit left no ledger entry — the balance is unauditable');
    });

    test('the earn screen is readable and requires authentication', async () => {
      const [a] = seeded(1);
      assert.equal((await get(`${P.coins}/earn`, { token: a.token })).status, 200);
      assert.equal((await get(`${P.coins}/earn`)).status, 401);
    });
  });

  test('the subscription endpoint reports BASIC for a free account', async () => {
    const [a] = seeded(1);
    const res = await get(`${P.billing}/subscription`, { token: a.token });
    assert.equal(res.status, 200);
    assert.equal(res.data.tier, 'BASIC');
  });
});
