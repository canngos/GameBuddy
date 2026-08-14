/**
 * Communities, posts, comments and likes.
 *
 * The interesting assertions here are the ownership ones. Community content is the only
 * place in GameBuddy where one gamer's action lands on another gamer's property, so
 * "can a non-owner delete this" is a question with a real answer rather than a formality.
 */

const { test, describe, after } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, del, request, P, CODE } = require('./helpers/api');
const { cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const seeded = (n) => fixtures.take(n);

/**
 * A unique, *alphabetic* suffix.
 *
 * Not a timestamp. `TextSurface.PUBLIC` strips phone numbers from community names, and a
 * 13-digit epoch is indistinguishable from one — every create came back 400 CONTENT_BLOCKED
 * until this changed. The filter was right; the test was wrong.
 */
const suffix = () => Math.random().toString(36).slice(2, 8).replace(/[0-9]/g, 'x');

async function makeCommunity(token, name) {
  const res = await post(`${P.community}/create/community`,
    { name, description: 'created by the QA suite' }, { token });
  assert.equal(res.status, 200, `create community failed: ${res.text}`);

  const list = await get(`${P.community}/get/communities`, { token });
  const found = (list.data?.communities ?? []).find((c) => c.name === name);
  assert.ok(found, 'the new community must appear in the list');
  return found.communityId ?? found.id;
}

async function makePost(token, communityId, title = 'QA post') {
  const res = await post(`${P.community}/create/post`,
    { communityId, title, body: 'body text' }, { token });
  assert.equal(res.status, 200, `create post failed: ${res.text}`);

  const posts = await get(`${P.community}/get/posts/${communityId}`, { token });
  const found = (posts.data?.posts ?? []).find((p) => p.title === title);
  assert.ok(found, 'the new post must appear in the community');
  return found.postId ?? found.id;
}

describe('community', () => {
  after(() => cleanup());

  test('a community can be created, listed and joined', async () => {
    const [owner, member] = seeded(2);
    const name = `QA Guild ${suffix()}`;
    const communityId = await makeCommunity(owner.token, name);

    const joined = await post(`${P.community}/join/community`, { communityId }, { token: member.token });
    assert.equal(joined.status, 200, joined.text);

    const members = await get(`${P.community}/get/members/${communityId}`, { token: owner.token });
    assert.ok(JSON.stringify(members.data).includes(member.userId), 'the joiner must appear as a member');
  });

  test('joining twice is refused', async () => {
    const [owner, member] = seeded(2);
    const communityId = await makeCommunity(owner.token, `QA Twice ${suffix()}`);

    await post(`${P.community}/join/community`, { communityId }, { token: member.token });
    const again = await post(`${P.community}/join/community`, { communityId }, { token: member.token });
    assert.equal(again.status, 409);
    assert.equal(again.code, CODE.ALREADY_MEMBER ?? '136');
  });

  describe('an owner leaving', () => {
    // TransactionCode.USER_OWNER ("Owner cannot leave their own community") suggests this
    // is refused. It is not — and the real behaviour is better: ownership passes to the
    // longest-standing remaining member, or the community closes if there is nobody left
    // (DefaultCommunityService:387-404). That enum constant is dead code; the contract
    // asserted here is the one the service actually implements.
    test('passes ownership to a remaining member', async () => {
      const [owner, member] = seeded(2);
      const communityId = await makeCommunity(owner.token, `QA Owner ${suffix()}`);
      await post(`${P.community}/join/community`, { communityId }, { token: member.token });

      const res = await post(`${P.community}/leave/community`, { communityId }, { token: owner.token });
      assert.equal(res.status, 200, res.text);

      const survives = (await get(`${P.community}/get/communities`, { token: member.token }))
        .data.communities.find((c) => c.communityId === communityId);
      assert.ok(survives, 'a community with members left must not be closed');

      // The successor is the owner now, so they inherit the close-on-last-leave rule.
      const closed = await post(`${P.community}/leave/community`, { communityId }, { token: member.token });
      assert.equal(closed.status, 200, closed.text);
      const gone = (await get(`${P.community}/get/communities`, { token: member.token }))
        .data.communities.find((c) => c.communityId === communityId);
      assert.equal(gone, undefined, 'the last member leaving must close the community');
    });

    test('closes the community when nobody is left', async () => {
      const [owner] = seeded(1);
      const communityId = await makeCommunity(owner.token, `QA Solo ${suffix()}`);

      const res = await post(`${P.community}/leave/community`, { communityId }, { token: owner.token });
      assert.equal(res.status, 200, res.text);
      assert.match(res.data?.message ?? res.text, /closed/i, 'the response should say it was closed');

      const gone = (await get(`${P.community}/get/communities`, { token: owner.token }))
        .data.communities.find((c) => c.communityId === communityId);
      assert.equal(gone, undefined, 'an empty community must not survive its owner');
    });
  });

  test('posts and comments round-trip, and likes are counted once', async () => {
    const [owner, reader] = seeded(2);
    const communityId = await makeCommunity(owner.token, `QA Posts ${suffix()}`);
    await post(`${P.community}/join/community`, { communityId }, { token: reader.token });

    const postId = await makePost(owner.token, communityId);

    const commented = await post(`${P.community}/create/comment`,
      { postId, message: 'first comment' }, { token: reader.token });
    assert.equal(commented.status, 200, commented.text);

    const comments = await get(`${P.community}/get/comments/${postId}`, { token: owner.token });
    assert.ok(JSON.stringify(comments.data).includes('first comment'), 'the comment must be readable');

    const liked = await post(`${P.community}/like/post/${postId}`, undefined, { token: reader.token });
    assert.equal(liked.status, 200, liked.text);

    // A like is a set membership, not a counter — liking twice must not count twice.
    const twice = await post(`${P.community}/like/post/${postId}`, undefined, { token: reader.token });
    assert.equal(twice.status, 409, 'a second like from the same gamer must be refused');
    assert.equal(twice.code, CODE.ALREADY_LIKED);

    const likes = await get(`${P.community}/get/post/likes/${postId}`, { token: owner.token });
    const payload = JSON.stringify(likes.data);
    assert.equal((payload.match(new RegExp(reader.userId, 'g')) ?? []).length, 1,
      'the liker appears more than once');

    const unliked = await post(`${P.community}/unlike/post/${postId}`, undefined, { token: reader.token });
    assert.equal(unliked.status, 200, unliked.text);
  });

  describe('ownership', () => {
    test('a stranger cannot delete somebody else\'s post', async () => {
      const [owner, stranger] = seeded(2);
      const communityId = await makeCommunity(owner.token, `QA Del ${suffix()}`);
      const postId = await makePost(owner.token, communityId);

      const res = await del(`${P.community}/delete/post/${postId}`, { token: stranger.token });
      assert.equal(res.status, 403, 'a non-owner deleted a post');
      assert.equal(res.code, CODE.NOT_OWNER);

      const still = await get(`${P.community}/get/post/${postId}`, { token: owner.token });
      assert.equal(still.status, 200, 'the post must survive the refused delete');
    });

    test('a stranger cannot delete somebody else\'s community', async () => {
      const [owner, stranger] = seeded(2);
      const communityId = await makeCommunity(owner.token, `QA DelC ${suffix()}`);

      const res = await request('DELETE', `${P.community}/delete/community`,
        { token: stranger.token, body: { communityId } });
      assert.ok(res.status === 403 || res.status === 409, `a non-owner deleted a community (${res.status})`);
    });

    test('the owner can delete their own post', async () => {
      const [owner] = seeded(1);
      const communityId = await makeCommunity(owner.token, `QA Own ${suffix()}`);
      const postId = await makePost(owner.token, communityId);

      const res = await del(`${P.community}/delete/post/${postId}`, { token: owner.token });
      assert.equal(res.status, 200, res.text);
    });

    test('ownership transfer moves control', async () => {
      const [owner, successor] = seeded(2);
      const communityId = await makeCommunity(owner.token, `QA Xfer ${suffix()}`);
      await post(`${P.community}/join/community`, { communityId }, { token: successor.token });

      const res = await post(
        `${P.community}/transfer/community/${communityId}/to/${successor.userId}`,
        undefined, { token: owner.token },
      );
      assert.equal(res.status, 200, res.text);

      // The successor now owns it, so the original owner is an ordinary member who can
      // leave without the community following them out.
      const left = await post(`${P.community}/leave/community`, { communityId }, { token: owner.token });
      assert.equal(left.status, 200, left.text);

      const survives = (await get(`${P.community}/get/communities`, { token: successor.token }))
        .data.communities.find((c) => c.communityId === communityId);
      assert.ok(survives, 'the community must outlive the former owner leaving');
    });
  });

  describe('the public text filter', () => {
    // README: "Why the text filter treats chat and posts differently". PUBLIC surfaces
    // strip contact details; PRIVATE ones deliberately do not, because two matched adults
    // swapping Discord tags is the app working. Both halves are asserted — a filter that
    // only ever refuses is easy, and wrong.
    test('contact details are stripped from a public post, not refused', async () => {
      const [owner] = seeded(1);
      const communityId = await makeCommunity(owner.token, `QA Filter ${suffix()}`);

      // Removed rather than rejected, which is the kinder failure: the post still
      // publishes and only the broadcast contact detail goes.
      const cases = [
        ['reach me at qa@example.com', 'qa@example.com'],
        ['call 5551234567', '5551234567'],
        ['https://example.com/join', 'example.com'],
      ];

      for (const [body, secret] of cases) {
        const title = `t-${suffix()}`;
        const res = await post(`${P.community}/create/post`, { communityId, title, body }, { token: owner.token });
        assert.equal(res.status, 200, `the post should publish: ${res.text}`);

        const posts = (await get(`${P.community}/get/posts/${communityId}`, { token: owner.token })).data.posts;
        const stored = posts.find((p) => p.title === title);
        assert.ok(stored, `the post ${title} was not stored`);
        assert.ok(!stored.body.includes(secret),
          `a public post still carries ${secret}: ${JSON.stringify(stored.body)}`);
        assert.match(stored.body, /\[removed\]/, 'the redaction should be visible to the reader');
      }
    });

    test('the same contact details are allowed in private chat', async () => {
      const [a, b] = seeded(2);
      await post(`${P.match}/accept`, { userId: b.userId }, { token: a.token });
      await post(`${P.match}/accept`, { userId: a.userId }, { token: b.token });

      const res = await post('/messages/send',
        { receiver: b.userId, message: 'my discord is qa#1234, or qa@example.com' }, { token: a.token });
      assert.equal(res.status, 200,
        'private chat must not strip contact details — that is the point of the product');
    });
  });

  test('a community requires a name', async () => {
    const [a] = seeded(1);
    const res = await post(`${P.community}/create/community`, { description: 'nameless' }, { token: a.token });
    assert.ok(res.status >= 400 && res.status < 500, `expected 4xx, got ${res.status}`);
  });

  test('community endpoints require authentication', async () => {
    assert.equal((await get(`${P.community}/get/posts`)).status, 401);
    assert.equal((await post(`${P.community}/create/community`, { name: 'x' })).status, 401);
  });
});
