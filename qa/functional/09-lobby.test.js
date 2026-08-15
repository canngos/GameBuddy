/**
 * Lobbies: the Gold-gated open-team feature that replaced Community.
 *
 * The properties under test are the ones the product promises:
 *
 * - **Creating is Gold's perk and only creating.** A free account browses and requests
 *   freely, and is refused at create with 159/402 — granted through the RevenueCat
 *   webhook exactly as production grants it, never by writing the tier column.
 * - **Nobody walks in.** Joining is a request the owner answers; a rejection is final
 *   for that lobby; asking twice is impossible.
 * - **Lock is quiet and timerless.** It stops requests, keeps the chat alive, and — the
 *   detail the owner cares about — cannot be cancelled through: cancel needs OPEN.
 * - **Chat is team-only** and read-only once the lobby is over.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const { get, post, request, P, CODE } = require('./helpers/api');
const { createAccount, cleanup } = require('./helpers/accounts');
const { env } = require('./helpers/tokens');

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

function lobbyRequest(overrides = {}) {
  return {
    title: 'ranked grind tonight',
    tone: 'COMPETITIVE',
    maxPlayers: 3,
    startsAt: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    ...overrides,
  };
}

describe('lobbies', () => {
  let owner; // Gold
  let member; // free — will be accepted
  let rejected; // free — will be rejected
  let gameId;

  before(async () => {
    owner = await createAccount();
    member = await createAccount();
    rejected = await createAccount();
    await grantGold(owner.userId);
    gameId = owner.games[0];
  });

  after(() => cleanup());

  let lobbyId;

  test('a free account is answered 402/159 at create — the paywall, not a validation error', async () => {
    const res = await post(`${P.lobby}/create`, lobbyRequest({ gameId }), { token: member.token });
    assert.equal(res.status, 402, res.text);
    assert.equal(res.code, CODE.SUBSCRIPTION_REQUIRED);
  });

  test('the webhook grant is visible as the entitlement the app reads', async () => {
    const res = await get(`${P.billing}/subscription`, { token: owner.token });
    assert.equal(res.status, 200, res.text);
    assert.equal(res.data.tier, 'GOLD');
    assert.equal(res.data.canCreateLobby, true);
  });

  test('a Gold account opens a lobby and is on its own roster as OWNER', async () => {
    const res = await post(`${P.lobby}/create`, lobbyRequest({ gameId }), { token: owner.token });
    assert.equal(res.status, 200, res.text);
    lobbyId = res.data.lobby.id;
    assert.equal(res.data.lobby.status, 'OPEN');
    assert.equal(res.data.lobby.playerCount, 1);
    assert.equal(res.data.members.length, 1);
    assert.equal(res.data.members[0].status, 'OWNER');
  });

  test('one live lobby per owner — a second create is refused with 183', async () => {
    const res = await post(`${P.lobby}/create`, lobbyRequest({ gameId }), { token: owner.token });
    assert.equal(res.status, 409, res.text);
    assert.equal(res.code, CODE.LOBBY_LIMIT_REACHED);
  });

  test('a slur in the title is refused with 170 and stores nothing', async () => {
    // A fresh Gold account: the main owner already has an active lobby, and the
    // one-active check fires before moderation, which would shadow the code under test.
    const second = await createAccount();
    await grantGold(second.userId);
    const res = await post(
      `${P.lobby}/create`,
      lobbyRequest({ gameId, title: 'kys noobs' }),
      { token: second.token },
    );
    assert.equal(res.status, 400, res.text);
    assert.equal(res.code, CODE.CONTENT_BLOCKED);
  });

  test('a planned start in the past is refused as invalid', async () => {
    const second = await createAccount();
    await grantGold(second.userId);
    const res = await post(
      `${P.lobby}/create`,
      lobbyRequest({ gameId, startsAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() }),
      { token: second.token },
    );
    assert.equal(res.status, 400, res.text);
    assert.equal(res.code, CODE.INVALID_REQUEST);
  });

  test('browse shows the open lobby to a free account', async () => {
    const res = await get(`${P.lobby}/browse`, { token: member.token });
    assert.equal(res.status, 200, res.text);
    const found = (res.data.lobbies ?? []).find((l) => l.id === lobbyId);
    assert.ok(found, 'the open lobby should be in the browse feed');
    assert.equal(found.myStatus, null);
  });

  test('the "starting now" filter keeps what is imminent and drops what is not', async () => {
    // The lobby under test plays in two hours, so the filter must exclude it — and
    // include it again the moment the filter comes off. A second lobby starting right
    // now proves the filter is a window rather than an "empty everything" switch.
    const soonOwner = await createAccount();
    await grantGold(soonOwner.userId);
    const soon = await post(
      `${P.lobby}/create`,
      lobbyRequest({ gameId, title: 'starting right now', startsAt: new Date().toISOString() }),
      { token: soonOwner.token },
    );
    assert.equal(soon.status, 200, soon.text);
    const soonId = soon.data.lobby.id;

    const now = await get(`${P.lobby}/browse?startingSoon=true`, { token: member.token });
    assert.equal(now.status, 200, now.text);
    const nowIds = (now.data.lobbies ?? []).map((l) => l.id);
    assert.ok(nowIds.includes(soonId), 'a lobby starting now must be in the filtered feed');
    assert.ok(!nowIds.includes(lobbyId), 'one two hours out must not be');

    const all = await get(`${P.lobby}/browse`, { token: member.token });
    const allIds = (all.data.lobbies ?? []).map((l) => l.id);
    assert.ok(allIds.includes(soonId) && allIds.includes(lobbyId), 'unfiltered shows both');

    await post(`${P.lobby}/${soonId}/cancel`, undefined, { token: soonOwner.token });
  });

  test('the pending queue is the owner\'s alone', async () => {
    const asMember = await get(`${P.lobby}/${lobbyId}`, { token: member.token });
    assert.equal(asMember.status, 200, asMember.text);
    assert.deepEqual(asMember.data.pendingRequests, []);
  });

  test('joining is a request, asking twice is refused by the row that exists', async () => {
    const first = await post(`${P.lobby}/${lobbyId}/join`, undefined, { token: member.token });
    assert.equal(first.status, 200, first.text);

    const again = await post(`${P.lobby}/${lobbyId}/join`, undefined, { token: member.token });
    assert.equal(again.status, 409, again.text);
    assert.equal(again.code, CODE.LOBBY_ALREADY_MEMBER);
  });

  test('the owner sees the request and accepting seats the requester', async () => {
    const detail = await get(`${P.lobby}/${lobbyId}`, { token: owner.token });
    assert.equal(detail.status, 200, detail.text);
    assert.equal(detail.data.pendingRequests.length, 1);
    assert.equal(detail.data.pendingRequests[0].userId, member.userId);

    const accepted = await post(
      `${P.lobby}/${lobbyId}/requests/${member.userId}/accept`,
      undefined,
      { token: owner.token },
    );
    assert.equal(accepted.status, 200, accepted.text);

    const after = await get(`${P.lobby}/${lobbyId}`, { token: member.token });
    assert.equal(after.data.lobby.myStatus, 'ACCEPTED');
    assert.equal(after.data.lobby.playerCount, 2);
  });

  test('only the owner answers requests', async () => {
    await post(`${P.lobby}/${lobbyId}/join`, undefined, { token: rejected.token });
    const res = await post(
      `${P.lobby}/${lobbyId}/requests/${rejected.userId}/accept`,
      undefined,
      { token: member.token },
    );
    assert.equal(res.status, 403, res.text);
    assert.equal(res.code, CODE.FORBIDDEN);
  });

  test('a rejection is final for this lobby — re-requesting is 185, not a new request', async () => {
    const reject = await post(
      `${P.lobby}/${lobbyId}/requests/${rejected.userId}/reject`,
      undefined,
      { token: owner.token },
    );
    assert.equal(reject.status, 200, reject.text);

    const again = await post(`${P.lobby}/${lobbyId}/join`, undefined, { token: rejected.token });
    assert.equal(again.status, 409, again.text);
    assert.equal(again.code, CODE.LOBBY_REJECTED);
  });

  test('lobby chat: team members talk, profanity is masked, slurs are refused', async () => {
    const sent = await post(
      `${P.lobby}/${lobbyId}/messages/send`,
      { message: 'who has a mic? fuck the ranked anxiety' },
      { token: member.token },
    );
    assert.equal(sent.status, 200, sent.text);
    assert.ok(!sent.data.message.message.includes('fuck'), 'profanity should come back masked');

    const slur = await post(
      `${P.lobby}/${lobbyId}/messages/send`,
      { message: 'kys' },
      { token: owner.token },
    );
    assert.equal(slur.status, 400, slur.text);
    assert.equal(slur.code, CODE.CONTENT_BLOCKED);

    const history = await get(`${P.lobby}/${lobbyId}/messages`, { token: owner.token });
    assert.equal(history.status, 200, history.text);
    assert.equal(history.data.messages.length, 1, 'the refused slur must not be stored');
  });

  test('a non-member is refused the chat in both directions', async () => {
    const sendRes = await post(
      `${P.lobby}/${lobbyId}/messages/send`,
      { message: 'let me in' },
      { token: rejected.token },
    );
    assert.equal(sendRes.status, 403, sendRes.text);
    assert.equal(sendRes.code, CODE.LOBBY_NOT_MEMBER);

    const readRes = await get(`${P.lobby}/${lobbyId}/messages`, { token: rejected.token });
    assert.equal(readRes.status, 403, readRes.text);
    assert.equal(readRes.code, CODE.LOBBY_NOT_MEMBER);
  });

  test('locking closes the door but not the chat', async () => {
    const lock = await post(`${P.lobby}/${lobbyId}/lock`, undefined, { token: owner.token });
    assert.equal(lock.status, 200, lock.text);

    // A stranger's request is refused now.
    const stranger = await createAccount();
    const join = await post(`${P.lobby}/${lobbyId}/join`, undefined, { token: stranger.token });
    assert.equal(join.status, 409, join.text);
    assert.equal(join.code, CODE.LOBBY_NOT_OPEN);

    // But the team still talks — lock is "stop asking", not "stop playing".
    const chat = await post(
      `${P.lobby}/${lobbyId}/messages/send`,
      { message: 'see you at seven' },
      { token: member.token },
    );
    assert.equal(chat.status, 200, chat.text);
  });

  test('a LOCKED lobby cannot be cancelled — unlock first, deliberately', async () => {
    const res = await post(`${P.lobby}/${lobbyId}/cancel`, undefined, { token: owner.token });
    assert.equal(res.status, 409, res.text);
    assert.equal(res.code, CODE.LOBBY_NOT_OPEN);
  });

  test('ending from LOCKED leaves the chat readable but closed', async () => {
    const end = await post(`${P.lobby}/${lobbyId}/end`, undefined, { token: owner.token });
    assert.equal(end.status, 200, end.text);

    const history = await get(`${P.lobby}/${lobbyId}/messages`, { token: member.token });
    assert.equal(history.status, 200, history.text);
    assert.ok(history.data.messages.length >= 2);

    const send = await post(
      `${P.lobby}/${lobbyId}/messages/send`,
      { message: 'gg' },
      { token: member.token },
    );
    assert.equal(send.status, 409, send.text);
    assert.equal(send.code, CODE.LOBBY_NOT_OPEN);
  });

  test('with the first lobby finished, the owner may open another — and cancel it from OPEN', async () => {
    const created = await post(`${P.lobby}/create`, lobbyRequest({ gameId }), { token: owner.token });
    assert.equal(created.status, 200, created.text);
    const secondId = created.data.lobby.id;

    const cancel = await post(`${P.lobby}/${secondId}/cancel`, undefined, { token: owner.token });
    assert.equal(cancel.status, 200, cancel.text);

    const detail = await get(`${P.lobby}/${secondId}`, { token: owner.token });
    assert.equal(detail.data.lobby.status, 'CANCELLED');
  });

  test('unknown lobby ids answer 404/178, not 500', async () => {
    const res = await get(`${P.lobby}/${crypto.randomUUID()}`, { token: owner.token });
    assert.equal(res.status, 404, res.text);
    assert.equal(res.code, CODE.LOBBY_NOT_FOUND);
  });
});
