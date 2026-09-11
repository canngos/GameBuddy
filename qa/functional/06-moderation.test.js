/**
 * Reporting as cases, the moderator console, and bans.
 *
 * A report is evidence in a case now, not a row a moderator ticks: every report against one
 * person joins the single open case against them, and a moderator resolves the case once
 * with a step from the ladder. The load-bearing assertion is still the last one — a ban, or
 * a suspension, has to take effect on a token that was already issued, or a blocked account
 * keeps using the app for the seven days its existing token has left to run.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { mint } = require('./helpers/tokens');
const { cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);
const suffix = () => Math.random().toString(36).slice(2, 8).replace(/[0-9]/g, 'x');

/**
 * A staff account, found by role rather than named in configuration — there may be more
 * than one, and nothing creates them automatically. See "The moderator console" in the
 * README for how a row gets promoted.
 */
function moderator() {
  const email = db.scalar("select email from gamebuddy.gamer where role = 'ADMIN' limit 1;");
  assert.ok(email, "no ADMIN account exists — promote one with: update gamebuddy.gamer set role = 'ADMIN' where email = …");
  return { email, token: mint(email), userId: db.gamerId(email) };
}

/** The open (or urgent) case against a target, or null. */
function openCaseId(targetId) {
  return db.scalar(
    `select id from gamebuddy.moderation_case where target_id = '${db.esc(targetId)}' and status <> 'CLOSED' limit 1;`,
  );
}

const reportProfile = (target, reporter, body) =>
  post(`${P.community}/report/profile/${target.userId}`, body, { token: reporter.token });

const resolveCase = (caseId, body, token) =>
  post(`${P.community}/admin/cases/${caseId}/resolve`, body, { token });

describe('moderation', () => {
  let admin;
  before(() => { admin = moderator(); });
  after(() => cleanup());

  test("a gamer can report another gamer's profile, opening a case", async () => {
    const [reporter, target] = seeded(2);
    const res = await reportProfile(target, reporter, { reasonCode: 'HARASSMENT' });

    assert.equal(res.status, 200, res.text);
    const stored = db.scalar(
      `select reason_code from gamebuddy.content_report where reporter_id = '${db.esc(reporter.userId)}' ` +
      `and content_id = '${db.esc(target.userId)}' order by created_at desc limit 1;`,
    );
    assert.equal(stored, 'HARASSMENT', 'the report must be persisted with its reason code');
    assert.ok(openCaseId(target.userId), 'a case must open against the target');
  });

  test('reporting the same person twice while the case is open is refused', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'SPAM_SCAM' });
    const again = await reportProfile(target, reporter, { reasonCode: 'SPAM_SCAM' });

    assert.equal(again.status, 409);
    assert.equal(again.code, CODE.ALREADY_REPORTED);
  });

  test('the case queue is visible to the moderator and to nobody else', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'HARASSMENT', note: `QA ${suffix()}` });

    const asAdmin = await get(`${P.community}/admin/cases?limit=500`, { token: admin.token });
    assert.equal(asAdmin.status, 200, asAdmin.text);
    assert.ok(JSON.stringify(asAdmin.data).includes(target.userId), 'the case must reach the queue');

    const asUser = await get(`${P.community}/admin/cases`, { token: reporter.token });
    assert.equal(asUser.status, 403, 'an ordinary gamer must not read the queue');

    const anonymous = await get(`${P.community}/admin/cases`);
    assert.equal(anonymous.status, 401);
  });

  test('dismissing a case closes its reports and leaves the account alone', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'HARASSMENT' });
    const caseId = openCaseId(target.userId);
    assert.ok(caseId);

    const res = await resolveCase(caseId, { action: 'DISMISS' }, admin.token);
    assert.equal(res.status, 200, res.text);

    assert.equal(
      db.scalar(`select status from gamebuddy.moderation_case where id = '${db.esc(caseId)}';`),
      'CLOSED',
    );
    const open = db.scalar(
      `select count(*) from gamebuddy.content_report where case_id = '${db.esc(caseId)}' and status = 'OPEN';`,
    );
    assert.equal(Number(open), 0, 'a dismissed case must leave no report open');
    // The reporter's dismissed count goes up — it is what weighs a serial false reporter down.
    assert.ok(Number(db.scalar(
      `select reports_dismissed from gamebuddy.gamer where user_id = '${db.esc(reporter.userId)}';`,
    )) >= 1);
  });

  test('resolving a case actions every report in it at once', async () => {
    // Two reporters, one target: one decision must close both reports, or a popular target
    // leaves the moderator a queue of duplicates.
    const [a, b, target] = seeded(3);
    await reportProfile(target, a, { reasonCode: 'HARASSMENT' });
    await reportProfile(target, b, { reasonCode: 'HARASSMENT' });
    const caseId = openCaseId(target.userId);

    const res = await resolveCase(caseId, { action: 'WARN', reasonCode: 'HARASSMENT' }, admin.token);
    assert.equal(res.status, 200, res.text);

    const stillOpen = db.scalar(
      `select count(*) from gamebuddy.content_report where case_id = '${db.esc(caseId)}' and status = 'OPEN';`,
    );
    assert.equal(Number(stillOpen), 0, 'resolving must close every report in the case');
    assert.equal(
      db.scalar(`select count(*) from gamebuddy.content_report where case_id = '${db.esc(caseId)}' and status = 'ACTIONED';`) * 1,
      2,
    );
  });

  test('an ordinary gamer cannot resolve a case', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'HARASSMENT' });
    const caseId = openCaseId(target.userId);

    assert.equal((await resolveCase(caseId, { action: 'DISMISS' }, reporter.token)).status, 403);
    assert.equal(
      db.scalar(`select status from gamebuddy.moderation_case where id = '${db.esc(caseId)}';`),
      'OPEN',
      'the case must still be open after the refused call',
    );
  });

  test('a report budget stops one account flooding the queue', async () => {
    // 10 reports a day per reporter; the 11th, against a fresh target so dedup does not
    // fire, is refused.
    const pool = seeded(12);
    const reporter = pool[0];
    for (let i = 1; i <= 10; i++) {
      const res = await reportProfile(pool[i], reporter, { reasonCode: 'SPAM_SCAM' });
      assert.equal(res.status, 200, `report ${i} should be accepted: ${res.text}`);
    }
    const eleventh = await reportProfile(pool[11], reporter, { reasonCode: 'SPAM_SCAM' });
    assert.equal(eleventh.status, 429);
    assert.equal(eleventh.code, CODE.RATE_LIMITED);
  });

  test('an underage report is urgent on its own', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'UNDERAGE' });
    assert.equal(
      db.scalar(`select status from gamebuddy.moderation_case where target_id = '${db.esc(target.userId)}' and status <> 'CLOSED';`),
      'URGENT',
      'one report that someone is a minor must make the case urgent',
    );
  });

  test('enough distinct reporters hide the target from decks pending review', async () => {
    const [a, b, c, target] = seeded(4);
    await reportProfile(target, a, { reasonCode: 'HARASSMENT' });
    await reportProfile(target, b, { reasonCode: 'HARASSMENT' });
    await reportProfile(target, c, { reasonCode: 'HARASSMENT' });

    assert.equal(
      db.scalar(`select hidden_from_discovery from gamebuddy.gamer where user_id = '${db.esc(target.userId)}';`),
      't',
      'three trusted reporters must take the profile out of decks',
    );
    // ...and a dismissal must put them back.
    const caseId = openCaseId(target.userId);
    await resolveCase(caseId, { action: 'DISMISS' }, admin.token);
    assert.equal(
      db.scalar(`select hidden_from_discovery from gamebuddy.gamer where user_id = '${db.esc(target.userId)}';`),
      'f',
      'dismissing must put the account back in the deck',
    );
  });

  test('a ban needs a note, and a suspension carries an end time', async () => {
    const [reporter, target] = seeded(2);
    await reportProfile(target, reporter, { reasonCode: 'HARASSMENT' });
    let caseId = openCaseId(target.userId);

    const noNote = await resolveCase(caseId, { action: 'BAN' }, admin.token);
    assert.equal(noNote.status, 400);
    assert.equal(noNote.code, CODE.MODERATION_NOTE_REQUIRED);

    // A suspension instead: blocked, with an end time.
    const suspend = await resolveCase(caseId, { action: 'SUSPEND_24H', reasonCode: 'HARASSMENT' }, admin.token);
    assert.equal(suspend.status, 200, suspend.text);
    // db.query rows are positional arrays, split on the unit separator.
    const [isBlocked, suspendedUntil] = db.query(
      `select is_blocked, suspended_until from gamebuddy.gamer where user_id = '${db.esc(target.userId)}';`,
    )[0];
    assert.equal(isBlocked, 't');
    assert.ok(suspendedUntil, 'a suspension must record when it ends');

    // Like a ban, a suspension kills the token already in the client's hands.
    const after_ = await get(`${P.profile}/get/user/info`, { token: target.token });
    assert.ok(after_.status === 401 || after_.status === 403, 'a suspended token must stop working');
  });

  describe('bans', () => {
    test('a ban invalidates a token that was already issued', async () => {
      const [victim] = seeded(1);
      // Issued and proven to work *before* the ban — that is the whole point.
      const before = await get(`${P.profile}/get/user/info`, { token: victim.token });
      assert.equal(before.status, 200, 'the victim must be able to use the API before the ban');

      const banned = await post(`${P.admin}/ban/user/${victim.userId}`, undefined, { token: admin.token });
      assert.equal(banned.status, 200, banned.text);

      const after_ = await get(`${P.profile}/get/user/info`, { token: victim.token });
      assert.ok(after_.status === 401 || after_.status === 403,
        `a banned gamer kept API access with their old token (HTTP ${after_.status})`);

      assert.equal((await post(`${P.admin}/unban/user/${victim.userId}`, undefined, { token: admin.token })).status, 200);
    });

    test('an ordinary gamer cannot ban anyone', async () => {
      const [attacker, victim] = seeded(2);
      assert.equal(
        (await post(`${P.admin}/ban/user/${victim.userId}`, undefined, { token: attacker.token })).status,
        403,
      );
    });

    test('the admin endpoints are closed to anonymous callers', async () => {
      assert.equal((await get(`${P.admin}/get/blocked/users`)).status, 401);
      assert.equal((await post(`${P.admin}/ban/user/anyone`)).status, 401);
      assert.equal((await get('/admin/analytics/showall')).status, 401);
    });
  });

  test('a reported message reaches the case with its decrypted context', async () => {
    const [a, b] = seeded(2);
    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
    await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
    await post('/messages/send', { receiver: b.userId, message: 'QA reportable message' }, { token: a.token });

    const messageId = db.scalar(
      `select m.id from gamebuddy.chat_message m join gamebuddy.chat_participant p on p.room_id = m.room_id ` +
      `where p.user_id = '${db.esc(b.userId)}' order by m.created_at desc limit 1;`,
    );
    assert.ok(messageId, 'the message must exist');

    // Only the recipient may report; the sender reporting their own message is refused.
    const bySender = await post(`/messages/report/${messageId}`, { reasonCode: 'HARASSMENT' }, { token: a.token });
    assert.ok(bySender.status >= 400, 'the sender must not be able to report their own message');

    const byRecipient = await post(`/messages/report/${messageId}`, { reasonCode: 'HARASSMENT' }, { token: b.token });
    assert.equal(byRecipient.status, 200, byRecipient.text);

    // The case is against the sender; opening it decrypts the reported message for the moderator.
    const caseId = openCaseId(a.userId);
    assert.ok(caseId, 'a case must open against the message sender');
    const detail = await get(`${P.community}/admin/cases/${caseId}`, { token: admin.token });
    assert.equal(detail.status, 200, detail.text);
    assert.ok(
      JSON.stringify(detail.data).includes('QA reportable message'),
      'the moderator must be able to read the reported message in clear text',
    );
  });
});
