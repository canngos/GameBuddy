/**
 * Chat: the REST surface, real STOMP delivery, and encryption at rest.
 *
 * The socket half matters because it is the half no unit test reaches. `ChatMessageService`
 * can be tested in isolation all day and still not tell you whether a message posted by A
 * arrives on B's socket — that answer involves the broker, the STOMP interceptor, the user
 * destination prefix and the Redis fan-out, and the only honest way to get it is to open
 * two sockets.
 *
 * Encryption at rest is asserted by reading the column, not by trusting the class name.
 */

const { test, describe, after } = require('node:test');
const assert = require('node:assert/strict');
const { get, post, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { cleanup } = require('./helpers/accounts');
const stomp = require('./helpers/stomp');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);

/** Two untouched fixtures, matched. Chat requires a match and refuses without one. */
async function matchedPair() {
  const [a, b] = seeded(2);
  await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
  await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
  return [a, b];
}

const roomIdFor = (a, b) => db.scalar(
  'select p1.room_id from gamebuddy.chat_participant p1 ' +
  'join gamebuddy.chat_participant p2 on p1.room_id = p2.room_id ' +
  `where p1.user_id = '${db.esc(a)}' and p2.user_id = '${db.esc(b)}';`,
);

describe('chat', () => {
  after(() => cleanup());

  test('an unmatched gamer cannot send a message', async () => {
    const [a, b] = seeded(2);
    const res = await post('/messages/send',
      { receiver: b.userId, message: 'hello' }, { token: a.token });

    assert.equal(res.status, 403);
    assert.equal(res.code, CODE.NOT_MATCHED, 'chat must be gated on a mutual match');
  });

  test('a matched gamer can send, and the room is created on that first message', async () => {
    const [a, b] = await matchedPair();
    assert.equal(roomIdFor(a.userId, b.userId), null, 'no room should exist before the first message');

    const res = await post('/messages/send',
      { receiver: b.userId, message: 'first contact' }, { token: a.token });
    assert.equal(res.status, 200, res.text);

    assert.ok(roomIdFor(a.userId, b.userId), 'the first message must create the room');
  });

  test('message bodies are encrypted at rest', async () => {
    const [a, b] = await matchedPair();
    const secret = `plaintext-canary-${Date.now()}`;

    await post('/messages/send', { receiver: b.userId, message: secret }, { token: a.token });

    const room = roomIdFor(a.userId, b.userId);
    const stored = db.storedMessages(room);
    assert.equal(stored.length, 1);

    // Compared as hex against the hex of the plaintext, so a column that is merely
    // base64-wrapped rather than encrypted cannot pass on an encoding technicality.
    const plainHex = Buffer.from(secret, 'utf8').toString('hex');
    assert.ok(!stored[0].includes(plainHex),
      'the message body is readable in the database — it is not encrypted at rest');

    // And it must still come back readable through the API, or "encrypted" would just
    // mean "lost".
    // As B, keyed on A — the path parameter is the *other* participant.
    const conversation = await get(`/messages/get/${a.userId}`, { token: b.token });
    assert.equal(conversation.status, 200);
    const bodies = (conversation.data?.conversations ?? []).map((c) => c.message);
    assert.ok(bodies.includes(secret), 'the recipient must be able to read the message back');
  });

  test('a non-participant cannot read the conversation', async () => {
    const [a, b] = await matchedPair();
    const [intruder] = seeded(1);
    await post('/messages/send', { receiver: b.userId, message: 'private' }, { token: a.token });

    const res = await get(`/messages/get/${a.userId}`, { token: intruder.token });
    const leaked = JSON.stringify(res.data ?? {});
    assert.ok(!leaked.includes('private'),
      'a gamer who is not in the room read its messages');
  });

  test('the inbox lists the conversation for both sides', async () => {
    const [a, b] = await matchedPair();
    await post('/messages/send', { receiver: b.userId, message: 'inbox check' }, { token: a.token });

    for (const [who, them] of [[a, b], [b, a]]) {
      const inbox = await get('/messages/get/inbox', { token: who.token });
      assert.equal(inbox.status, 200);
      const entries = inbox.data?.inboxList ?? [];
      const entry = entries.find((e) => e.userId === them.userId);
      assert.ok(entry, `the conversation is missing from ${who.userId}'s inbox`);
      assert.equal(entry.lastMessage, 'inbox check', 'the inbox must show the decrypted last message');
      assert.ok(entry.username, 'the inbox entry must carry the other gamer\'s username');
    }
  });

  test('an empty message is refused', async () => {
    const [a, b] = await matchedPair();
    const res = await post('/messages/send', { receiver: b.userId, message: '' }, { token: a.token });
    assert.ok(res.status >= 400, `an empty message must be refused; got ${res.status}`);
  });

  test('presence answers for a matched gamer', async () => {
    const [a, b] = await matchedPair();
    const res = await get(`/presence/${b.userId}`, { token: a.token });
    assert.equal(res.status, 200);
    assert.ok(typeof res.data?.online === 'boolean' || res.data?.online !== undefined,
      'presence must report an online flag');
  });

  describe('over the socket', () => {
    test('a STOMP CONNECT without a token is rejected', async () => {
      await assert.rejects(
        () => stomp.connect({ token: null }),
        'an unauthenticated CONNECT must not be accepted',
      );
    });

    test('a message posted by A arrives on B\'s socket', async () => {
      const [a, b] = await matchedPair();

      const receiver = await stomp.connect({ token: b.token });
      const arrival = receiver.expectMessage('/user/queue/messages', 10_000);

      // Posted over REST, delivered over the socket: that is the real path the app uses,
      // and the seam where a broker misconfiguration hides.
      const sent = await post('/messages/send',
        { receiver: b.userId, message: 'over the wire' }, { token: a.token });
      assert.equal(sent.status, 200, sent.text);

      const frame = await arrival;
      assert.ok(frame, 'B\'s socket received nothing within 10s');
      assert.ok(JSON.stringify(frame).includes('over the wire'),
        `delivered frame did not contain the message: ${JSON.stringify(frame).slice(0, 300)}`);

      await receiver.close();
    });

    test('a gamer does not receive messages addressed to somebody else', async () => {
      const [a, b] = await matchedPair();
      const [eavesdropper] = seeded(1);

      const listener = await stomp.connect({ token: eavesdropper.token });
      const anything = listener.expectMessage('/user/queue/messages', 3_000);

      await post('/messages/send', { receiver: b.userId, message: 'not for you' }, { token: a.token });

      assert.equal(await anything, null, 'a third party received a message from another conversation');
      await listener.close();
    });
  });
});
