/**
 * The backend half of the tester-feedback fixes (prod-customer-feedback.md).
 *
 * Four features that did not exist before and one rule that changed, all of which the app
 * now depends on:
 *
 * - withdrawing a friend request (#1) — there was no way to retract one at all
 * - marking a conversation read on its own (#5) — the watermark only moved as a side
 *   effect of loading history, which is what left the unread badge up
 * - claiming a free cosmetic (#7) — free used to mean "already owned, no row"
 * - boosting a lobby (#8) — replaces the retired deck boost
 */

const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { get, post, request, P, CODE } = require('./helpers/api');
const { createAccount, cleanup } = require('./helpers/accounts');
const { env } = require('./helpers/tokens');
const db = require('./helpers/db');

const WEBHOOK_TOKEN = env().REVENUECAT_WEBHOOK_TOKEN || 'local-development-webhook-token';

/** Grants Gold the way production does: through the webhook, never the database. */
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

/**
 * Tops up a balance directly.
 *
 * Unlike Gold above there is no webhook worth driving for this — a coin pack purchase is a
 * store event, and what these tests need is simply "this account can afford one boost".
 */
function grantCoins(userId, coins) {
  db.query(`UPDATE gamebuddy.gamer SET coin = ${Number(coins)} WHERE user_id = '${db.esc(userId)}'`);
}

/** Codes this suite is the first to need. */
const LOBBY_ALREADY_BOOSTED = '173';
const FRIEND_NO_REQUEST = '116';
const COSMETIC_ALREADY_OWNED = '165';
const COSMETIC_NOT_OWNED = '166';

const LOBBY_BOOST_COST = 300;

/** A mutual like, which is what friendship and chat are both gated on. */
async function matchUp(a, b) {
  await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
  const mutual = await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
  assert.equal(mutual.status, 200, 'the second accept should match');
  return mutual;
}

describe('friend requests can be withdrawn', () => {
  let a;
  let b;

  before(async () => {
    a = await createAccount();
    b = await createAccount();
    await matchUp(a, b);
  });

  after(() => cleanup());

  test('a sent request is visible to both sides before it is withdrawn', async () => {
    const sent = await post(`${P.profile}/send/friend`, { userId: b.userId }, { token: a.token });
    assert.equal(sent.status, 200, sent.text);

    const mine = await get(`${P.profile}/get/sent/friends`, { token: a.token });
    assert.ok(
      (mine.data?.friends ?? []).some((f) => f.userId === b.userId),
      "the sender sees it under 'sent'",
    );

    const theirs = await get(`${P.profile}/get/requests/friends`, { token: b.token });
    assert.ok(
      (theirs.data?.friends ?? []).some((f) => f.userId === a.userId),
      'the recipient sees it as pending',
    );
  });

  test('withdrawing clears it from both lists', async () => {
    const withdrawn = await post(
      `${P.profile}/withdraw/friend`,
      { userId: b.userId },
      { token: a.token },
    );
    assert.equal(withdrawn.status, 200, withdrawn.text);
    assert.equal(withdrawn.code, CODE.SUCCESS);

    const mine = await get(`${P.profile}/get/sent/friends`, { token: a.token });
    assert.equal(
      (mine.data?.friends ?? []).filter((f) => f.userId === b.userId).length,
      0,
      "gone from the sender's 'sent'",
    );

    const theirs = await get(`${P.profile}/get/requests/friends`, { token: b.token });
    assert.equal(
      (theirs.data?.friends ?? []).filter((f) => f.userId === a.userId).length,
      0,
      "gone from the recipient's pending queue",
    );
  });

  test('withdrawing nothing is refused rather than silently succeeding', async () => {
    const again = await post(
      `${P.profile}/withdraw/friend`,
      { userId: b.userId },
      { token: a.token },
    );
    assert.equal(again.code, FRIEND_NO_REQUEST, again.text);
  });

  test('a request you received is not yours to withdraw — reject is that door', async () => {
    // b asks a. a must not be able to "withdraw" it; that would be rejecting by the wrong
    // verb, and it is the asymmetry that made this endpoint necessary in the first place.
    await post(`${P.profile}/send/friend`, { userId: a.userId }, { token: b.token });

    const wrongWay = await post(
      `${P.profile}/withdraw/friend`,
      { userId: b.userId },
      { token: a.token },
    );
    assert.equal(wrongWay.code, FRIEND_NO_REQUEST, wrongWay.text);

    const stillThere = await get(`${P.profile}/get/requests/friends`, { token: a.token });
    assert.ok(
      (stillThere.data?.friends ?? []).some((f) => f.userId === b.userId),
      'the request b sent is untouched',
    );
  });

  test('after withdrawing, the same request can be sent again', async () => {
    const resent = await post(`${P.profile}/send/friend`, { userId: b.userId }, { token: a.token });
    assert.equal(resent.status, 200, resent.text);
  });
});

describe('a conversation can be marked read on its own', () => {
  let a;
  let b;

  const inboxRow = async (token, friendId) => {
    const inbox = await get('/messages/get/inbox', { token });
    return (inbox.data?.inboxList ?? []).find((row) => row.userId === friendId) ?? null;
  };

  before(async () => {
    a = await createAccount();
    b = await createAccount();
    await matchUp(a, b);
  });

  after(() => cleanup());

  test('an unopened conversation counts every message as unread', async () => {
    await post('/messages/send', { receiver: a.userId, message: 'first' }, { token: b.token });
    await post('/messages/send', { receiver: a.userId, message: 'second' }, { token: b.token });

    const row = await inboxRow(a.token, b.userId);
    assert.equal(row?.unreadCount, 2, 'both are unread until the thread is opened');
  });

  test('marking read clears the badge without loading the history', async () => {
    // The point of the endpoint: the app caches the conversation, so re-opening a chat
    // sends no GET at all and the watermark used to stay behind.
    const marked = await post(`/messages/read/${b.userId}`, undefined, { token: a.token });
    assert.equal(marked.status, 200, marked.text);

    const row = await inboxRow(a.token, b.userId);
    assert.equal(row?.unreadCount, 0, 'the badge is gone');
  });

  test('a message arriving after that is unread again', async () => {
    await post('/messages/send', { receiver: a.userId, message: 'third' }, { token: b.token });
    const row = await inboxRow(a.token, b.userId);
    assert.equal(row?.unreadCount, 1, 'only the new one');
  });

  test('marking read is idempotent and never negative', async () => {
    await post(`/messages/read/${b.userId}`, undefined, { token: a.token });
    await post(`/messages/read/${b.userId}`, undefined, { token: a.token });
    const row = await inboxRow(a.token, b.userId);
    assert.equal(row?.unreadCount, 0);
  });

  test('marking read touches only the caller — the other side keeps its own count', async () => {
    await post('/messages/send', { receiver: b.userId, message: 'yours', }, { token: a.token });
    await post(`/messages/read/${b.userId}`, undefined, { token: a.token });

    const theirs = await inboxRow(b.token, a.userId);
    assert.ok((theirs?.unreadCount ?? 0) > 0, "a's read receipt must not clear b's badge");
  });

  test('a conversation that does not exist yet is a no-op, not a 500', async () => {
    const stranger = await createAccount();
    const marked = await post(`/messages/read/${stranger.userId}`, undefined, { token: a.token });
    assert.equal(marked.status, 200, marked.text);
  });
});

describe('free cosmetics are claimed, not assumed', () => {
  let gamer;
  let steelId;
  let paidId;

  const store = async (token) => {
    const res = await get(P.cosmetics, { token });
    return [...(res.data?.frames ?? []), ...(res.data?.banners ?? [])];
  };

  before(async () => {
    gamer = await createAccount();
    const items = await store(gamer.token);
    const free = items.filter((i) => i.price === 0 && !i.membershipOnly);
    assert.equal(free.length, 1, 'exactly one free item should remain in the catalogue');
    steelId = free[0].id;
    assert.equal(free[0].name, 'Steel', 'and it should be the Steel frame');
    paidId = items.find((i) => i.price === 100)?.id;
    assert.ok(paidId, 'the repriced entry tier should exist at 100 coins');
  });

  after(() => cleanup());

  test('a new account does not own the free frame yet', async () => {
    const items = await store(gamer.token);
    assert.equal(items.find((i) => i.id === steelId)?.owned, false, 'not owned until claimed');
  });

  test('an unclaimed free frame cannot be worn', async () => {
    const worn = await post(`${P.cosmetics}/${steelId}/equip`, undefined, { token: gamer.token });
    assert.equal(worn.code, COSMETIC_NOT_OWNED, worn.text);
  });

  test('claiming it costs nothing and grants it', async () => {
    const before = await get(P.cosmetics, { token: gamer.token });
    const balanceBefore = before.data?.coins ?? 0;

    const claimed = await post(`${P.cosmetics}/${steelId}/buy`, undefined, { token: gamer.token });
    assert.equal(claimed.status, 200, claimed.text);
    assert.equal(claimed.data?.coins, balanceBefore, 'no coins were spent');

    const items = await store(gamer.token);
    assert.equal(items.find((i) => i.id === steelId)?.owned, true, 'owned now');
  });

  test('and then it can be worn', async () => {
    const worn = await post(`${P.cosmetics}/${steelId}/equip`, undefined, { token: gamer.token });
    assert.equal(worn.status, 200, worn.text);
    const items = await store(gamer.token);
    assert.equal(items.find((i) => i.id === steelId)?.equipped, true);
  });

  test('claiming twice is refused like any other double purchase', async () => {
    const again = await post(`${P.cosmetics}/${steelId}/buy`, undefined, { token: gamer.token });
    assert.equal(again.code, COSMETIC_ALREADY_OWNED, again.text);
  });

  test('the repriced entry tier is a real purchase — no coins, no item', async () => {
    // A fresh account starts poor, which is the case that proves 100 is actually charged
    // rather than treated as free.
    const poor = await createAccount();
    const attempt = await post(`${P.cosmetics}/${paidId}/buy`, undefined, { token: poor.token });
    assert.equal(attempt.code, CODE.COIN_NOT_ENOUGH, attempt.text);

    const items = await store(poor.token);
    assert.equal(items.find((i) => i.id === paidId)?.owned, false);
  });

  test('with coins, the same purchase debits exactly 100', async () => {
    const buyer = await createAccount();
    grantCoins(buyer.userId, 500);

    const bought = await post(`${P.cosmetics}/${paidId}/buy`, undefined, { token: buyer.token });
    assert.equal(bought.status, 200, bought.text);
    assert.equal(bought.data?.coins, 400, 'exactly 100 spent');
  });
});

describe('a lobby can be boosted to the top of the list', () => {
  let owner;
  let stranger;
  let lobbyId;

  const openLobby = async (token, title) => {
    const games = await get(`${P.profile}/get/games`, { token });
    const gameId = games.data?.games?.[0]?.gameId;
    const created = await post(
      `${P.lobby}/create`,
      {
        gameId,
        title,
        tone: 'CHILL',
        maxPlayers: 3,
        startsAt: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
      },
      { token },
    );
    assert.equal(created.status, 200, created.text);
    return created.data?.lobby?.id;
  };

  before(async () => {
    owner = await createAccount();
    stranger = await createAccount();
    // Creating is Gold-only, and boosting is bought with coins.
    await grantGold(owner.userId);
    grantCoins(owner.userId, 1000);
    lobbyId = await openLobby(owner.token, 'boost me');
  });

  after(() => cleanup());

  test('a fresh lobby is not boosted', async () => {
    const detail = await get(`${P.lobby}/${lobbyId}`, { token: owner.token });
    assert.equal(detail.data?.lobby?.boosted, false);
  });

  test('a stranger cannot boost somebody else\'s lobby', async () => {
    const refused = await post(`${P.lobby}/${lobbyId}/boost`, undefined, { token: stranger.token });
    assert.equal(refused.code, CODE.FORBIDDEN, refused.text);
  });

  test('the owner is charged and the lobby comes back boosted', async () => {
    const before = await get(`${P.coins}/earn`, { token: owner.token });
    const balanceBefore = before.data?.coinBalance ?? 0;

    const boosted = await post(`${P.lobby}/${lobbyId}/boost`, undefined, { token: owner.token });
    assert.equal(boosted.status, 200, boosted.text);
    assert.equal(boosted.data?.lobby?.boosted, true, 'the response already says so');

    const after_ = await get(`${P.coins}/earn`, { token: owner.token });
    assert.equal(
      after_.data?.coinBalance,
      balanceBefore - LOBBY_BOOST_COST,
      'exactly the boost price',
    );
  });

  test('a second boost buys nothing and is refused', async () => {
    const before = await get(`${P.coins}/earn`, { token: owner.token });
    const again = await post(`${P.lobby}/${lobbyId}/boost`, undefined, { token: owner.token });
    assert.equal(again.code, LOBBY_ALREADY_BOOSTED, again.text);

    const after_ = await get(`${P.coins}/earn`, { token: owner.token });
    assert.equal(after_.data?.coinBalance, before.data?.coinBalance, 'and charges nothing');
  });

  test('browse puts the boosted lobby ahead of a lobby starting sooner', async () => {
    // The ordering is the whole feature: soonest-first is the default, so a lobby starting
    // later must still come first once it is boosted.
    const other = await createAccount();
    await grantGold(other.userId);
    const soonerId = await openLobby(other.token, 'starting sooner');
    assert.ok(soonerId);

    const browsed = await get(`${P.lobby}/browse?page=0&size=20`, { token: stranger.token });
    const ids = (browsed.data?.lobbies ?? []).map((l) => l.id);
    assert.ok(ids.includes(lobbyId), 'the boosted lobby is in the feed');
    assert.equal(ids[0], lobbyId, 'and it is first');

    const boostedRow = browsed.data.lobbies.find((l) => l.id === lobbyId);
    assert.equal(boostedRow.boosted, true, 'the card knows to draw the frame');
  });

  test('the pin ends when the lobby stops being open', async () => {
    const locked = await post(`${P.lobby}/${lobbyId}/lock`, undefined, { token: owner.token });
    assert.equal(locked.status, 200, locked.text);

    const detail = await get(`${P.lobby}/${lobbyId}`, { token: owner.token });
    assert.equal(detail.data?.lobby?.boosted, false, 'no expiry to sweep — the status ends it');
  });

  test('a poor owner is refused before anything is pinned', async () => {
    const broke = await createAccount();
    await grantGold(broke.userId);
    grantCoins(broke.userId, 10);
    const id = await openLobby(broke.token, 'no coins here');

    const refused = await post(`${P.lobby}/${id}/boost`, undefined, { token: broke.token });
    assert.equal(refused.code, CODE.COIN_NOT_ENOUGH, refused.text);

    const detail = await get(`${P.lobby}/${id}`, { token: broke.token });
    assert.equal(detail.data?.lobby?.boosted, false);
  });
});
