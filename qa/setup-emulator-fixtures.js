#!/usr/bin/env node
/**
 * Arranges the state the emulator account needs to exercise the feedback fixes by hand.
 *
 *   GB_BASE_URL=http://127.0.0.1:8080 node qa/setup-emulator-fixtures.js canngos
 *
 * Throwaway: this is a manual-testing aid, not part of the suite. It creates real accounts
 * through the API (never the database) so every relationship it sets up is one the product
 * could actually produce.
 */

const crypto = require('node:crypto');
const { get, post, request, P } = require('./functional/helpers/api');
const { createAccount } = require('./functional/helpers/accounts');
const { env } = require('./functional/helpers/tokens');
const db = require('./functional/helpers/db');

const target = process.argv[2] || 'canngos';
const WEBHOOK_TOKEN = env().REVENUECAT_WEBHOOK_TOKEN || 'local-development-webhook-token';

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
  if (res.status !== 200) throw new Error(`gold webhook refused: ${res.status} ${res.text}`);
}

/**
 * Completes a match from the other side.
 *
 * The fan's like is a real API call; the reciprocal half would need the emulator account's
 * token, which this script does not have. Both rows are written because the table is keyed
 * on the pair in one direction only.
 */
function matchBothWays(a, b) {
  db.query(`INSERT INTO gamebuddy.approved_matches (user_id, matched_id)
            VALUES ('${db.esc(a)}','${db.esc(b)}'), ('${db.esc(b)}','${db.esc(a)}')
            ON CONFLICT DO NOTHING`);
}

(async () => {
  const userId = db.scalar(
    `SELECT user_id FROM gamebuddy.gamer WHERE username = '${db.esc(target)}'`,
  );
  if (!userId) throw new Error(`no gamer named ${target}`);
  console.log(`${target} = ${userId}`);

  // Gold, so the Liked You faces are revealed and a lobby can be opened.
  await grantGold(userId);
  db.query(`UPDATE gamebuddy.gamer SET coin = 900 WHERE user_id = '${db.esc(userId)}'`);
  console.log('granted Gold + 900 coins');

  // Steel back to unclaimed, so the Market shows the Claim button rather than "Owned".
  // The migration granted it to every account that existed when it ran.
  db.query(`DELETE FROM gamebuddy.gamer_cosmetic gc
            USING gamebuddy.cosmetic c
            WHERE c.id = gc.cosmetic_id
              AND gc.user_id = '${db.esc(userId)}'
              AND c.asset_key = 'frames/frame-steel.png'`);
  db.query(`UPDATE gamebuddy.gamer SET equipped_frame_id = NULL WHERE user_id = '${db.esc(userId)}'`);
  console.log('un-claimed Steel');

  // 1. Two admirers: one-sided likes, so Liked You has faces and the profile can offer Match.
  for (const n of [1, 2]) {
    const fan = await createAccount({ username: `fanofyou${n}${Date.now() % 10000}` });
    const liked = await post(`${P.match}/accept`, { userId }, { token: fan.token });
    console.log(`admirer ${fan.username}: ${liked.status} ${liked.code}`);
  }

  // 2. A matched gamer who then asks to be friends — the Friend Requests section.
  const asker = await createAccount({ username: `wantsfriend${Date.now() % 10000}` });
  await post(`${P.match}/accept`, { userId }, { token: asker.token });
  matchBothWays(userId, asker.userId);
  const asked = await post(`${P.profile}/send/friend`, { userId }, { token: asker.token });
  console.log(`friend request from ${asker.username}: ${asked.status} ${asked.code} ${asked.message ?? ''}`);

  // 3. A matched gamer who sends unread messages — the purple badge.
  const talker = await createAccount({ username: `chatty${Date.now() % 10000}` });
  await post(`${P.match}/accept`, { userId }, { token: talker.token });
  matchBothWays(userId, talker.userId);
  for (const text of ['hey, up for ranked tonight?', 'we need one more', 'ping me when you see this']) {
    const sent = await post('/messages/send', { receiver: userId, message: text }, { token: talker.token });
    if (sent.status !== 200) console.log(`  message refused: ${sent.status} ${sent.text}`);
  }
  console.log(`unread messages from ${talker.username}`);

  console.log('\ndone — restart the app to pick it all up');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
