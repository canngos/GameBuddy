/**
 * The recommendation feed.
 *
 * The feed is the product. It is also the most expensive request in the system and the one
 * with the most ways to be quietly wrong: an empty deck, a deck that repeats, a filter
 * that silently did not apply. Each of those looks like "the recommender is broken" and
 * none of them is, so they are pulled apart here.
 */

const { test, describe, after } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { mint } = require('./helpers/tokens');
const { createAccount, cleanup } = require('./helpers/accounts');

/** A seeded fixture, which unlike a fresh account is inside the trained artefact. */
function seededToken() {
  const email = db.scalar(
    "select email from gamebuddy.gamer where email like '%@bot.gamebuddy.invalid' " +
    'and not is_blocked order by user_id limit 1;',
  );
  return { email, token: mint(email), userId: db.gamerId(email) };
}

const feed = (token, query = '') => get(`${P.match}/get/recommendations${query}`, { token });
const ids = (res) => (res.data?.recommendedGamers ?? []).map((g) => g.userId ?? g.id);

describe('recommendation feed', () => {
  after(() => cleanup());

  test('a gamer inside the trained artefact gets a populated deck', async () => {
    const bot = seededToken();
    const res = await feed(bot.token);

    assert.equal(res.status, 200);
    assert.equal(res.code, CODE.SUCCESS);
    assert.ok(ids(res).length > 0, 'the deck must not be empty for a trained gamer');
    assert.ok(!ids(res).includes(bot.userId), 'a gamer must never be recommended to themselves');
  });

  test('every candidate carries what the card needs to render', async () => {
    const res = await feed(seededToken().token);
    const [first] = res.data.recommendedGamers;

    // Field names are the contract with GameBuddy-App/src/api/match.ts. Note it is
    // `gamerUsername`, not `username` — the profile endpoints use the latter, so the two
    // are genuinely different names for the same thing and a test that guesses gets it
    // wrong.
    assert.ok(first.userId, 'userId');
    assert.ok(first.gamerUsername, 'gamerUsername');
    assert.ok(typeof first.age === 'number', 'age');
    assert.ok(Array.isArray(first.favoriteGames), 'favoriteGames');
    assert.ok(Array.isArray(first.selectedKeywords), 'selectedKeywords');
    assert.ok(Array.isArray(first.platforms), 'platforms');
    assert.ok(first.country !== undefined, 'country');
  });

  test('a brand-new account still gets a deck, via the cold-start path', async () => {
    // The model can only rank ids baked into the artefact, and an account created a minute
    // ago is not one of them. Without the cold-start fallback the first thing a new user
    // ever sees would be an empty screen — the worst possible moment to have nothing.
    const fresh = await createAccount();
    const res = await feed(fresh.token);

    assert.equal(res.status, 200);
    assert.ok(ids(res).length > 0, 'a new account must not see an empty deck');
  });

  test('the deck does not repeat a gamer already decided on', async () => {
    const bot = seededToken();
    const first = ids(await feed(bot.token));
    assert.ok(first.length > 0);

    const target = first[0];
    const declined = await post(`${P.match}/decline`, { userId: target }, { token: bot.token });
    assert.equal(declined.status, 200, `decline failed: ${declined.text}`);

    const second = ids(await feed(bot.token));
    assert.ok(!second.includes(target), 'a declined gamer must not come back in the next page');
  });

  test('no candidate appears twice on one page', async () => {
    const seen = ids(await feed(seededToken().token));
    assert.equal(new Set(seen).size, seen.length, 'the page contains a duplicate');
  });

  describe('filters', () => {
    test('a narrowing filter without Gold is refused with 402', async () => {
      const res = await feed(seededToken().token, '?country=Finland');
      assert.equal(res.status, 402, 'advanced filters are a Gold entitlement');
      assert.equal(res.code, CODE.SUBSCRIPTION_REQUIRED);
    });

    test('an unfiltered feed is never charged for', async () => {
      const res = await feed(seededToken().token);
      assert.equal(res.status, 200, 'asking for an unfiltered deck must never cost a 402');
    });

    test('an unknown platform is refused rather than ignored', async () => {
      // Refused on purpose: a filter that silently does not apply hands back a deck the
      // gamer believes is narrowed and is not.
      const res = await feed(seededToken().token, '?platform=NINTENDO_VIRTUAL_BOY');
      assert.ok(res.status === 400 || res.status === 402, `expected 400/402, got ${res.status}`);
      if (res.status === 400) assert.equal(res.code, CODE.INVALID_REQUEST);
    });
  });

  test('the feed requires authentication', async () => {
    const res = await get(`${P.match}/get/recommendations`);
    assert.equal(res.status, 401);
  });

  test('two different gamers do not receive identical decks', async () => {
    // Identical decks across gamers would mean the ranking is not actually personalised —
    // the symptom of an artefact that loaded but is not being indexed by user.
    const emails = db.query(
      "select email from gamebuddy.gamer where email like '%@bot.gamebuddy.invalid' " +
      'order by user_id limit 2;',
    ).map((r) => r[0]);

    const [a, b] = await Promise.all(emails.map((e) => feed(mint(e))));
    const overlap = ids(a).filter((id) => ids(b).includes(id)).length;
    assert.notDeepEqual(ids(a), ids(b), 'two gamers received the same deck in the same order');
    assert.ok(overlap < ids(a).length, `decks overlap completely (${overlap}/${ids(a).length})`);
  });
});
