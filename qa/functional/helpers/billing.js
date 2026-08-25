/**
 * Entitlements, granted the way production grants them.
 *
 * Gold is never written into the database by a test. It arrives through the RevenueCat
 * webhook, because that is the only path that exists in production — a test that sets the
 * column directly proves the feature gate works and says nothing about whether a real
 * purchase would ever reach it.
 *
 * Lives here because three suites need Gold before they can test anything else, and three
 * copies of the same payload drift apart the first time the webhook contract changes.
 * `07-billing.test.js` deliberately keeps its own: that suite is testing the webhook
 * itself, with variations this helper has no business knowing about.
 */

const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const { request, P } = require('./api');
const { env } = require('./tokens');

const WEBHOOK_TOKEN = env().REVENUECAT_WEBHOOK_TOKEN || 'local-development-webhook-token';

/** Grants Gold for 30 days. */
async function grantGold(userId) {
  const now = Date.now();
  const res = await request('POST', `${P.billing}/revenuecat/webhook`, {
    body: {
      api_version: '1.0',
      event: {
        id: crypto.randomUUID(),
        type: 'INITIAL_PURCHASE',
        app_user_id: userId,
        product_id: 'gamebuddy.gold.monthly',
        purchased_at_ms: now,
        expiration_at_ms: now + 30 * 24 * 60 * 60 * 1000,
        store: 'PLAY_STORE',
        transaction_id: crypto.randomUUID(),
        original_transaction_id: crypto.randomUUID(),
        entitlement_ids: ['gold'],
        period_type: 'NORMAL',
      },
    },
    headers: { Authorization: WEBHOOK_TOKEN },
  });
  assert.equal(res.status, 200, `webhook refused: ${res.status} ${res.text}`);
}

module.exports = { grantGold, WEBHOOK_TOKEN };
