/**
 * Reporting, the moderator console, and bans.
 *
 * The load-bearing assertion in this file is that a ban takes effect on a token that was
 * already issued. Every other moderation control is decoration if a banned account can
 * keep using the app for the seven days its existing token has left to run.
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

/** The single staff account, bootstrapped from MODERATOR_EMAIL on first start. */
function moderator() {
  const email = db.scalar("select email from gamebuddy.gamer where role = 'ADMIN' limit 1;");
  assert.ok(email, 'no ADMIN account exists — set MODERATOR_EMAIL in .env and restart');
  return { email, token: mint(email), userId: db.gamerId(email) };
}

describe('moderation', () => {
  let admin;
  before(() => { admin = moderator(); });
  after(() => cleanup());

  test('a gamer can report another gamer\'s profile', async () => {
    const [reporter, target] = seeded(2);
    const res = await post(`${P.community}/report/profile/${target.userId}`,
      { reason: 'QA: inappropriate profile' }, { token: reporter.token });

    assert.equal(res.status, 200, res.text);
    const stored = db.scalar(
      `select status from gamebuddy.content_report where reporter_id = '${db.esc(reporter.userId)}' ` +
      `and content_id = '${db.esc(target.userId)}';`,
    );
    assert.ok(stored, 'the report must be persisted');
  });

  test('reporting the same thing twice is refused', async () => {
    const [reporter, target] = seeded(2);
    await post(`${P.community}/report/profile/${target.userId}`, { reason: 'QA once' }, { token: reporter.token });
    const again = await post(`${P.community}/report/profile/${target.userId}`,
      { reason: 'QA twice' }, { token: reporter.token });

    assert.equal(again.status, 409);
    assert.equal(again.code, CODE.ALREADY_REPORTED);
  });

  test('the report queue is visible to the moderator and to nobody else', async () => {
    const [reporter, target] = seeded(2);
    await post(`${P.community}/report/profile/${target.userId}`,
      { reason: `QA queue ${suffix()}` }, { token: reporter.token });

    // Newest first and a large page, because the queue is @PageableDefault(size = 50) and
    // open reports accumulate across runs — a freshly filed one falls off page one and the
    // test starts reporting that reports do not reach the queue at all.
    const asAdmin = await get(`${P.community}/admin/reports?size=200&sort=createdAt,desc`, { token: admin.token });
    assert.equal(asAdmin.status, 200, asAdmin.text);
    assert.ok(JSON.stringify(asAdmin.data).includes(target.userId), 'the report must reach the queue');

    const asUser = await get(`${P.community}/admin/reports`, { token: reporter.token });
    assert.equal(asUser.status, 403, 'an ordinary gamer must not read the moderation queue');

    const anonymous = await get(`${P.community}/admin/reports`);
    assert.equal(anonymous.status, 401);
  });

  test('a report can be dismissed, and leaves the queue', async () => {
    const [reporter, target] = seeded(2);
    await post(`${P.community}/report/profile/${target.userId}`,
      { reason: `QA dismiss ${suffix()}` }, { token: reporter.token });

    const reportId = db.scalar(
      `select id from gamebuddy.content_report where reporter_id = '${db.esc(reporter.userId)}' ` +
      "and status = 'OPEN' order by created_at desc limit 1;",
    );
    assert.ok(reportId, 'the report should be OPEN before it is dismissed');

    const res = await post(`${P.community}/admin/reports/${reportId}/dismiss`, undefined, { token: admin.token });
    assert.equal(res.status, 200, res.text);

    const after_ = db.scalar(`select status from gamebuddy.content_report where id = '${db.esc(reportId)}';`);
    assert.notEqual(after_, 'OPEN', 'a dismissed report must not stay open');
  });

  test('actioning a reported post removes it', async () => {
    const [owner, reporter] = seeded(2);
    const name = `QA Mod ${suffix()}`;
    await post(`${P.community}/create/community`, { name, description: 'mod test' }, { token: owner.token });
    const communityId = (await get(`${P.community}/get/communities`, { token: owner.token }))
      .data.communities.find((c) => c.name === name).communityId;

    const title = `bad-post-${suffix()}`;
    await post(`${P.community}/create/post`, { communityId, title, body: 'QA offending body' }, { token: owner.token });
    const postId = (await get(`${P.community}/get/posts/${communityId}`, { token: owner.token }))
      .data.posts.find((p) => p.title === title).postId;

    // Reporting a post requires membership of the community it is in — a stranger who
    // cannot see the post has no standing to report it.
    await post(`${P.community}/join/community`, { communityId }, { token: reporter.token });
    const reported = await post(`${P.community}/report/post/${postId}`,
      { reason: 'QA: rule breach' }, { token: reporter.token });
    assert.equal(reported.status, 200, reported.text);
    const reportId = db.scalar(
      `select id from gamebuddy.content_report where content_id = '${db.esc(postId)}' ` +
      "and status = 'OPEN' limit 1;",
    );
    assert.ok(reportId);

    const actioned = await post(`${P.community}/admin/reports/${reportId}/action`, undefined, { token: admin.token });
    assert.equal(actioned.status, 200, actioned.text);

    const gone = await get(`${P.community}/get/post/${postId}`, { token: owner.token });
    assert.equal(gone.status, 404, 'an actioned post must be removed');
  });

  test('an ordinary gamer cannot action or dismiss a report', async () => {
    const [reporter, target] = seeded(2);
    await post(`${P.community}/report/profile/${target.userId}`,
      { reason: `QA priv ${suffix()}` }, { token: reporter.token });
    const reportId = db.scalar(
      `select id from gamebuddy.content_report where reporter_id = '${db.esc(reporter.userId)}' ` +
      "and status = 'OPEN' order by created_at desc limit 1;",
    );

    assert.equal((await post(`${P.community}/admin/reports/${reportId}/action`,
      undefined, { token: reporter.token })).status, 403);
    assert.equal((await post(`${P.community}/admin/reports/${reportId}/dismiss`,
      undefined, { token: target.token })).status, 403);

    assert.equal(db.scalar(`select status from gamebuddy.content_report where id = '${db.esc(reportId)}';`),
      'OPEN', 'the report must still be open after the refused calls');
  });

  describe('bans', () => {
    test('a ban invalidates a token that was already issued', async () => {
      const [victim] = seeded(1);

      // Issued and proven to work *before* the ban — that is the whole point. A test that
      // mints the token afterwards proves nothing about revocation.
      const before = await get(`${P.profile}/get/user/info`, { token: victim.token });
      assert.equal(before.status, 200, 'the victim must be able to use the API before the ban');

      const banned = await post(`${P.admin}/ban/user/${victim.userId}`, undefined, { token: admin.token });
      assert.equal(banned.status, 200, banned.text);

      const after_ = await get(`${P.profile}/get/user/info`, { token: victim.token });
      assert.ok(after_.status === 401 || after_.status === 403,
        `a banned gamer kept API access with their old token (HTTP ${after_.status})`);

      // And the ban is reversible.
      assert.equal((await post(`${P.admin}/unban/user/${victim.userId}`,
        undefined, { token: admin.token })).status, 200);
    });

    test('an ordinary gamer cannot ban anyone', async () => {
      const [attacker, victim] = seeded(2);
      const res = await post(`${P.admin}/ban/user/${victim.userId}`, undefined, { token: attacker.token });

      // 403, and specifically not 401. The difference is not cosmetic: the body is empty
      // either way, and GameBuddy-App/src/api/client.ts:105 turns an empty 401 into
      // onSessionExpired() — so while this answered 401, an ordinary gamer who reached an
      // admin route was signed out of the app instead of being told no. SecurityConfig had
      // an authenticationEntryPoint and no accessDeniedHandler, so the AccessDeniedException
      // from the `/admin/**` → hasRole("ADMIN") rule fell through to the entry point.
      //
      // /community/admin/reports always answered 403 — it is not under /admin/**, so its
      // @PreAuthorize denial never reached the entry point. This is the assertion that keeps
      // the two admin surfaces agreeing.
      assert.equal(res.status, 403, `an authenticated non-admin must get 403, got ${res.status}`);

      assert.equal(db.scalar(`select is_blocked from gamebuddy.gamer where user_id = '${db.esc(victim.userId)}';`),
        'f', 'the victim must not be blocked — authorisation itself holds');
    });

    test('the admin endpoints are closed to anonymous callers', async () => {
      assert.equal((await get(`${P.admin}/get/blocked/users`)).status, 401);
      assert.equal((await post(`${P.admin}/ban/user/anyone`)).status, 401);
      assert.equal((await get('/admin/analytics/showall')).status, 401);
    });
  });

  test('a reported chat message reaches the chat moderation queue', async () => {
    const [a, b] = seeded(2);
    await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
    await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });
    await post('/messages/send', { receiver: b.userId, message: 'QA reportable message' }, { token: a.token });

    const messageId = db.scalar(
      `select m.id from gamebuddy.chat_message m join gamebuddy.chat_participant p on p.room_id = m.room_id ` +
      `where p.user_id = '${db.esc(b.userId)}' order by m.created_at desc limit 1;`,
    );
    assert.ok(messageId, 'the message must exist');

    // Only the *recipient* may report; the sender reporting their own message is refused.
    const bySender = await post(`/messages/report/${messageId}`, { reason: 'QA' }, { token: a.token });
    assert.ok(bySender.status >= 400, 'the sender must not be able to report their own message');

    const byRecipient = await post(`/messages/report/${messageId}`, { reason: 'QA abuse' }, { token: b.token });
    assert.equal(byRecipient.status, 200, byRecipient.text);

    const queue = await get('/admin/chat/reported', { token: admin.token });
    assert.equal(queue.status, 200, queue.text);
    assert.ok(JSON.stringify(queue.data).includes('QA reportable message'),
      'the moderator must be able to read the reported message in clear text');
  });
});
