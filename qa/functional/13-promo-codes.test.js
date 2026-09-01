/**
 * Promotion codes, end to end against the running stack.
 *
 * The load-bearing assertions are the ones about a code being used more times than it was
 * meant to be. Everything else here — the reward arriving, the wrong account being
 * refused — is checked by unit tests too; "once per account" and "n times in total" are
 * enforced by two database statements, and a mocked repository cannot prove either of
 * them, because the thing being asserted is what Postgres does with a conflicting insert
 * and a conditional update.
 *
 * Rewards are read through the API and the ledger rather than only from `gamer.coin`,
 * for the reason 07-billing gives: the column is the balance, not the record of how it got
 * there, and a giveaway that credits coins without a ledger row is invisible in the
 * console's coin flow the following morning.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, put, del, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { mint } = require('./helpers/tokens');
const { cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);

/** Marks every row this file creates, so the sweep at the end takes exactly those. */
const NOTE = 'QA promo';

const PROMO = `${P.admin}/promo-codes`;
const MINE = `${P.billing}/promo-codes`;

/** A staff account, found by role. Same helper as 06-moderation, same reasoning. */
function moderator() {
  const email = db.scalar("select email from gamebuddy.gamer where role = 'ADMIN' limit 1;");
  assert.ok(
    email,
    "no ADMIN account exists — promote one with: update gamebuddy.gamer set role = 'ADMIN' where email = …",
  );
  return { email, token: mint(email) };
}

const coinsOf = (userId) =>
  Number(db.scalar(`select coin from gamebuddy.gamer where user_id = '${db.esc(userId)}';`));

const promoLedgerRows = (userId) =>
  Number(
    db.scalar(
      `select count(*) from gamebuddy.coin_ledger where user_id = '${db.esc(userId)}' ` +
        "and reason = 'PROMO_CODE';",
    ),
  );

async function tierOf(token) {
  const res = await get(`${P.billing}/subscription`, { token });
  assert.equal(res.status, 200, res.text);
  return res.data.tier;
}

describe('promotion codes', () => {
  let admin;
  before(() => {
    admin = moderator();
  });

  after(() => {
    // The child tables cascade, so removing the codes removes their assignments and
    // redemptions with them — which is also the behaviour the delete test asserts.
    db.query(`delete from gamebuddy.promo_code where note = '${db.esc(NOTE)}';`);
    cleanup();
  });

  /** Creates a code as the administrator and hands back the row the API answered with. */
  async function create(body) {
    const res = await post(PROMO, { validDays: 30, note: NOTE, ...body }, { token: admin.token });
    assert.equal(res.status, 200, res.text);
    return res.data;
  }

  describe('only staff can issue one', () => {
    test('an ordinary account cannot list, create or delete codes', async () => {
      const [a] = seeded(1);

      assert.equal((await get(PROMO, { token: a.token })).status, 403);
      assert.equal(
        (await post(PROMO, { kind: 'COIN', coinAmount: 100, validDays: 1 }, { token: a.token }))
          .status,
        403,
      );
      assert.equal(
        (await del(`${PROMO}/00000000-0000-0000-0000-000000000000`, { token: a.token })).status,
        403,
      );
    });

    test('and neither can somebody with no token at all', async () => {
      assert.equal((await get(PROMO)).status, 401);
    });
  });

  describe('redeeming', () => {
    test('a coin code credits the balance and leaves a ledger row saying why', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 250 });
      const before = coinsOf(a.userId);
      const rowsBefore = promoLedgerRows(a.userId);

      const res = await post(`${MINE}/redeem`, { code: code.code }, { token: a.token });
      assert.equal(res.status, 200, res.text);
      assert.equal(res.data.kind, 'COIN');

      assert.equal(coinsOf(a.userId), before + 250, 'the code did not credit its coins');
      assert.equal(res.data.coinBalance, before + 250, 'the answer must carry the new balance');
      assert.equal(
        promoLedgerRows(a.userId),
        rowsBefore + 1,
        'a giveaway with no ledger row is invisible in the coin economy',
      );
    });

    test('a Gold code grants membership, and a second one extends it', async () => {
      const [a] = seeded(1);
      assert.equal(await tierOf(a.token), 'BASIC');

      const first = await create({ kind: 'GOLD', goldDays: 30 });
      const granted = await post(`${MINE}/redeem`, { code: first.code }, { token: a.token });
      assert.equal(granted.status, 200, granted.text);
      assert.equal(await tierOf(a.token), 'GOLD');

      const firstExpiry = new Date(granted.data.goldExpiresAt).getTime();
      const days = (firstExpiry - Date.now()) / 86_400_000;
      assert.ok(days > 29 && days < 31, `expected about 30 days of Gold, got ${days}`);

      const second = await create({ kind: 'GOLD', goldDays: 10 });
      const extended = await post(`${MINE}/redeem`, { code: second.code }, { token: a.token });
      assert.equal(extended.status, 200, extended.text);

      const total = (new Date(extended.data.goldExpiresAt).getTime() - Date.now()) / 86_400_000;
      assert.ok(total > 39 && total < 41, `Gold must be extended, not replaced; got ${total} days`);
    });

    test('punctuation and case do not matter', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 10, code: 'QAWELCOME' });

      const res = await post(`${MINE}/redeem`, { code: ' qa-welcome ' }, { token: a.token });
      assert.equal(res.status, 200, res.text);
      assert.ok(code.code === 'QAWELCOME');
    });

    test('the same account cannot redeem a code twice', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 100 });

      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);
      const balance = coinsOf(a.userId);

      const again = await post(`${MINE}/redeem`, { code: code.code }, { token: a.token });
      assert.equal(again.status, 409);
      assert.equal(again.code, CODE.PROMO_CODE_ALREADY_REDEEMED);
      assert.equal(coinsOf(a.userId), balance, 'a refused redemption must not pay');
    });

    test('a code stops working once its redemptions run out', async () => {
      const [a, b] = seeded(2);
      const code = await create({ kind: 'COIN', coinAmount: 100, maxRedemptions: 1 });

      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);

      const balance = coinsOf(b.userId);
      const refused = await post(`${MINE}/redeem`, { code: code.code }, { token: b.token });
      assert.equal(refused.status, 409);
      assert.equal(refused.code, CODE.PROMO_CODE_EXHAUSTED);
      assert.equal(coinsOf(b.userId), balance);
    });

    test('two people racing for the last redemption: exactly one of them gets it', async () => {
      const [a, b] = seeded(2);
      const code = await create({ kind: 'COIN', coinAmount: 100, maxRedemptions: 1 });

      // The whole reason the counter is a conditional UPDATE rather than a read and a
      // write. Sequentially this passes either way; in parallel a read-then-write pays
      // both.
      const [first, second] = await Promise.all([
        post(`${MINE}/redeem`, { code: code.code }, { token: a.token }),
        post(`${MINE}/redeem`, { code: code.code }, { token: b.token }),
      ]);

      const granted = [first, second].filter((res) => res.status === 200);
      assert.equal(granted.length, 1, 'exactly one redemption must win the last use');

      const count = Number(
        db.scalar(`select redemption_count from gamebuddy.promo_code where id = '${db.esc(code.id)}';`),
      );
      assert.equal(count, 1, 'the counter must match what was actually paid out');
    });

    test('an unknown code, and one that was switched off, read the same', async () => {
      const [a] = seeded(1);
      const missing = await post(`${MINE}/redeem`, { code: 'NOSUCHCODE' }, { token: a.token });
      assert.equal(missing.status, 404);
      assert.equal(missing.code, CODE.PROMO_CODE_INVALID);

      const code = await create({ kind: 'COIN', coinAmount: 100 });
      assert.equal((await post(`${PROMO}/${code.id}/disable`, undefined, { token: admin.token })).status, 200);

      const disabled = await post(`${MINE}/redeem`, { code: code.code }, { token: a.token });
      assert.equal(disabled.status, 404, 'a disabled code must not announce that it exists');
      assert.equal(disabled.code, CODE.PROMO_CODE_INVALID);

      assert.equal((await post(`${PROMO}/${code.id}/enable`, undefined, { token: admin.token })).status, 200);
      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);
    });
  });

  describe('codes addressed to somebody', () => {
    test('the recipient sees it waiting; nobody else does', async () => {
      const [a, b] = seeded(2);
      const code = await create({ kind: 'COIN', coinAmount: 100, assigneeIds: [a.userId] });

      const mine = await get(MINE, { token: a.token });
      assert.equal(mine.status, 200, mine.text);
      assert.ok(
        mine.data.waiting.some((row) => row.code === code.code),
        'the account it was addressed to must see it waiting',
      );

      const theirs = await get(MINE, { token: b.token });
      assert.equal(
        theirs.data.waiting.some((row) => row.code === code.code),
        false,
        'a gift must not appear on somebody else\'s screen',
      );
    });

    test('somebody else holding the code is refused, and told why', async () => {
      const [a, b] = seeded(2);
      const code = await create({ kind: 'COIN', coinAmount: 100, assigneeIds: [a.userId] });

      const res = await post(`${MINE}/redeem`, { code: code.code }, { token: b.token });
      assert.equal(res.status, 403);
      assert.equal(res.code, CODE.PROMO_CODE_NOT_YOURS);
    });

    test('adding somebody by editing makes it theirs too', async () => {
      const [a, b] = seeded(2);
      const code = await create({ kind: 'COIN', coinAmount: 100, assigneeIds: [a.userId] });

      const edited = await put(
        `${PROMO}/${code.id}`,
        { kind: 'COIN', coinAmount: 100, validDays: 30, note: NOTE, assigneeIds: [a.userId, b.userId] },
        { token: admin.token },
      );
      assert.equal(edited.status, 200, edited.text);
      assert.equal(edited.data.assigneeCount, 2);

      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: b.token })).status, 200);
    });

    test('a code with nobody on it is public', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 100 });
      assert.equal(code.assigneeCount, 0);
      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);
    });

    test('sending by email marks the recipient, and says how many went', async () => {
      const [a] = seeded(1);

      // Locally MAIL_MODE is `log`, so this prints rather than posts — but it runs the
      // whole send path, which is the half that unit tests cannot reach. The first version
      // of this feature answered 500 here: marking a recipient as emailed was a
      // @Transactional method called from a sibling method on the same bean, so the proxy
      // was bypassed and the update had no transaction to run in. Nothing but a real send
      // could have shown that.
      const res = await post(
        PROMO,
        {
          kind: 'COIN',
          coinAmount: 100,
          validDays: 30,
          note: NOTE,
          assigneeIds: [a.userId],
          sendEmail: true,
        },
        { token: admin.token },
      );

      assert.equal(res.status, 200, res.text);
      assert.equal(res.data.emailed.sent, 1, 'the message must have gone out');
      assert.equal(res.data.emailed.failed, 0);
      assert.equal(res.data.emailedCount, 1);

      const emailedAt = db.scalar(
        `select emailed_at from gamebuddy.promo_code_assignment ` +
          `where code_id = '${db.esc(res.data.id)}' and user_id = '${db.esc(a.userId)}';`,
      );
      assert.ok(emailedAt, 'a sent message must be recorded, or the next edit sends it again');

      // And a second send skips whoever already has it.
      const again = await put(
        `${PROMO}/${res.data.id}`,
        {
          kind: 'COIN',
          coinAmount: 100,
          validDays: 30,
          note: NOTE,
          assigneeIds: [a.userId],
          sendEmail: true,
        },
        { token: admin.token },
      );
      assert.equal(again.status, 200, again.text);
      assert.equal(again.data.emailed.sent, 0, 'nobody should be mailed the same code twice');
    });

    test('an account that cannot receive anything is refused at creation', async () => {
      const res = await post(
        PROMO,
        { kind: 'COIN', coinAmount: 100, validDays: 30, note: NOTE, assigneeIds: ['not-a-real-user'] },
        { token: admin.token },
      );
      assert.equal(res.status, 404);
      assert.equal(res.code, CODE.PROMO_USER_NOT_FOUND);
    });
  });

  describe('editing and removing', () => {
    test('a code cannot be named after one that exists', async () => {
      await create({ kind: 'COIN', coinAmount: 100, code: 'QATAKEN' });
      const again = await post(
        PROMO,
        { kind: 'COIN', coinAmount: 100, validDays: 30, note: NOTE, code: 'qa-taken' },
        { token: admin.token },
      );
      assert.equal(again.status, 409);
      assert.equal(again.code, CODE.PROMO_CODE_EXISTS);
    });

    test('the limit cannot be dropped below what has already been redeemed', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 100, maxRedemptions: 5 });
      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);

      const res = await put(
        `${PROMO}/${code.id}`,
        { kind: 'COIN', coinAmount: 100, validDays: 30, note: NOTE, maxRedemptions: 0 },
        { token: admin.token },
      );
      assert.equal(res.status, 400, 'a limit below the count would read as a broken counter');
    });

    test('deleting takes the code, its recipients and its redemptions — and nothing else', async () => {
      const [a] = seeded(1);
      const code = await create({ kind: 'COIN', coinAmount: 100, assigneeIds: [a.userId] });
      assert.equal((await post(`${MINE}/redeem`, { code: code.code }, { token: a.token })).status, 200);

      const balance = coinsOf(a.userId);
      const ledgerRows = promoLedgerRows(a.userId);

      assert.equal((await del(`${PROMO}/${code.id}`, { token: admin.token })).status, 200);

      const rows = (table) =>
        Number(
          db.scalar(`select count(*) from gamebuddy.${table} where code_id = '${db.esc(code.id)}';`),
        );
      assert.equal(
        Number(db.scalar(`select count(*) from gamebuddy.promo_code where id = '${db.esc(code.id)}';`)),
        0,
      );
      assert.equal(rows('promo_code_assignment'), 0, 'the recipient list must cascade');
      assert.equal(rows('promo_redemption'), 0, 'the redemption record must cascade');

      // The point of the cascade being acceptable: the money trail is somewhere else.
      assert.equal(coinsOf(a.userId), balance, 'deleting a code must not claw coins back');
      assert.equal(promoLedgerRows(a.userId), ledgerRows, 'the ledger is not the code’s to delete');

      const gone = await post(`${MINE}/redeem`, { code: code.code }, { token: a.token });
      assert.equal(gone.status, 404);
      assert.equal(gone.code, CODE.PROMO_CODE_INVALID);
    });
  });

  describe('the recipient picker', () => {
    test('it is staff-only', async () => {
      const [a] = seeded(1);
      assert.equal((await get(`${P.admin}/users`, { token: a.token })).status, 403);
    });

    test('a username finds the account it belongs to', async () => {
      const [a] = seeded(1);
      const username = db.scalar(
        `select username from gamebuddy.gamer where user_id = '${db.esc(a.userId)}';`,
      );

      // The whole name, not the first few letters. The seeded pool shares its prefixes by
      // the thousand — "survi" alone matches about 1,500 accounts — so a short prefix is a
      // legitimate search that simply does not have this account on its first page, and
      // asserting otherwise tests the fixture generator rather than the query.
      const res = await get(`${P.admin}/users?q=${encodeURIComponent(username)}`, {
        token: admin.token,
      });

      assert.equal(res.status, 200, res.text);
      assert.ok(
        res.data.users.some((row) => row.userId === a.userId),
        'searching a username must find that account',
      );

      // And the prefix is genuinely a prefix match, not an exact one.
      const partial = await get(`${P.admin}/users?q=${encodeURIComponent(username.slice(0, -2))}`, {
        token: admin.token,
      });
      assert.equal(partial.status, 200, partial.text);
      assert.ok(
        partial.data.users.some((row) => row.userId === a.userId),
        'dropping the last characters must still find it',
      );
    });

    test('every cohort answers, and none of them lists staff', async () => {
      for (const filter of ['ALL', 'OFFLINE_14D', 'REPORT_CONTRIBUTORS', 'GOLD', 'FREE', 'NEW_7D']) {
        const res = await get(`${P.admin}/users?filter=${filter}`, { token: admin.token });
        assert.equal(res.status, 200, `${filter}: ${res.text}`);
        assert.ok(Array.isArray(res.data.users), `${filter} must answer with a page of users`);
        assert.equal(
          res.data.users.some((row) => row.email === admin.email),
          false,
          `${filter} must not list the console's own account`,
        );
      }
    });

    test('selecting everybody matching answers with ids and says whether it was cut short', async () => {
      const res = await get(`${P.admin}/users/ids?filter=ALL`, { token: admin.token });
      assert.equal(res.status, 200, res.text);
      assert.ok(Array.isArray(res.data.ids));
      assert.ok(res.data.ids.length <= 200, 'the cap is what one send is allowed');
      assert.equal(typeof res.data.truncated, 'boolean');
    });

    test('an unknown cohort is refused rather than quietly meaning everybody', async () => {
      const res = await get(`${P.admin}/users?filter=NONSENSE`, { token: admin.token });
      assert.equal(res.status, 400, 'a typo must not send a code to the whole user base');
    });
  });
});
