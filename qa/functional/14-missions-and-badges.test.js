/**
 * The mission campaign and the hard badge tier, end to end against the running stack.
 *
 * The load-bearing assertion here is that **the campaign ends**. Every other property —
 * the deal being three, the reward rising with the band, a claim paying once — is covered
 * by `MissionServiceTest` over an in-memory table, and covered better, because that test
 * can walk a gamer through all eight sets in a millisecond. What it cannot prove is that
 * the real table, with its real primary key and its real conditional update, behaves the
 * same way; and an escalating reward ladder that does not actually terminate is a money
 * glitch rather than a bug, so it is worth proving against Postgres.
 *
 * Progress is driven by moving the *baseline* rather than by playing the game. A mission
 * asks for fifty lobby messages, and there is no way to send fifty encrypted lobby
 * messages from a test in reasonable time — but "current metric minus baseline" is the
 * whole rule, so lowering the baseline is indistinguishable, from the service's point of
 * view, from having done the work. What that does *not* test is the metric sources, and
 * those have their own unit tests.
 */

const { test, describe, after, before } = require('node:test');
const assert = require('node:assert/strict');

const { get, post, P, CODE } = require('./helpers/api');
const db = require('./helpers/db');
const { createAccount, cleanup } = require('./helpers/accounts');
const fixtures = require('./helpers/fixtures');

const EARN = `${P.coins}/earn`;

/** The campaign's length, mirrored from `Mission.SETS`. Asserted below, not assumed. */
const SETS = 8;

const body = (res) => res.json.body.data;

/**
 * Makes this account measure past every target in the pool.
 *
 * Zeroing the baseline alone does nothing: progress is `metric - baseline` clamped at zero,
 * so an account that has never done anything is still at zero however low the baseline
 * goes. The metrics have to be real, which means rows in the tables the sources count —
 * and none of these tables has a foreign key to a room or a lobby, so the ids can be
 * invented. Nothing here decrypts a message body; the sources only count rows.
 *
 * This is not a substitute for testing the metric sources. Those have unit tests. What it
 * buys is the ability to walk a whole eight-set campaign against the real table, which is
 * the one thing `MissionServiceTest` cannot do.
 */
function seedActivity(userId, partners) {
  const id = db.esc(userId);
  const bytes = "'\\x00'::bytea";

  // No foreign key on `room_id`, and nothing decrypts a body — the source only counts
  // rows by sender. MESSAGES_SENT, past the 200 of talk-200.
  db.query(`
    insert into gamebuddy.chat_message (id, room_id, sender_id, body, nonce, key_version, created_at)
    select gen_random_uuid(), gen_random_uuid(), '${id}', ${bytes}, ${bytes}, 1, now()
      from generate_series(1, 250);`);

  // The lobby tables *do* have foreign keys: a lobby needs a real game, and members and
  // messages need real lobbies. So these three go in order, carrying the ids forward.
  const lobbies = db
    .query(
      `insert into gamebuddy.lobby
         (id, owner_id, game_id, title, tone, max_players, starts_at, status, version, created_at)
       select gen_random_uuid(), '${id}', (select game_id from gamebuddy.games limit 1),
              'QA seed', 'CHILL', 4, now(), 'ENDED', 0, now()
         from generate_series(1, 6)
       returning id;`,
    )
    .map((row) => row[0])
    // psql prints its own "INSERT 0 6" tally as a final row; only uuids are lobby ids.
    .filter((v) => /^[0-9a-f-]{36}$/i.test(v || ''));

  assert.ok(lobbies.length >= 6, 'the lobby seed did not come back with ids');

  const memberRows = lobbies.map((l) => `('${l}', '${id}', 'OWNER', now(), now(), now())`).join(', ');
  db.query(
    `insert into gamebuddy.lobby_member (lobby_id, user_id, status, requested_at, created_at, updated_at)
     values ${memberRows} on conflict do nothing;`,
  );

  db.query(`
    insert into gamebuddy.lobby_message (id, lobby_id, sender_id, body, nonce, key_version, created_at)
    select gen_random_uuid(), '${lobbies[0]}', '${id}', ${bytes}, ${bytes}, 1, now()
      from generate_series(1, 60);`);

  db.query(`
    insert into gamebuddy.rewarded_ad_grant (transaction_id, user_id, coins, created_at)
    select 'qa-' || gen_random_uuid(), '${id}', 15, now() from generate_series(1, 12);`);

  // COINS_SPENT, past the 1000 of spend-1000. A negative delta is a spend.
  db.query(`
    insert into gamebuddy.coin_ledger (id, user_id, delta, reason, created_at)
    values (gen_random_uuid(), '${id}', -1500, 'COSMETIC_PURCHASE', now());`);

  // COSMETICS_OWNED for buy-1, and enough of them to earn Collector further down.
  db.query(`
    insert into gamebuddy.gamer_cosmetic (user_id, cosmetic_id, paid, acquired_at)
    select '${id}', id, 1, now() from gamebuddy.cosmetic
     where unlocked_by_badge is null and membership_only = false and price > 0 limit 20
    on conflict do nothing;`);

  db.query(`update gamebuddy.gamer set daily_claims_total = 120 where user_id = '${id}';`);

  // MATCHES has to be mutual and LIKES_SENT counts this side only, so both directions go
  // in — and against real accounts, because the join table points back at `gamer`.
  const matches = partners
    .map((p) => `('${id}', '${db.esc(p.userId)}', now()), ('${db.esc(p.userId)}', '${id}', now())`)
    .join(', ');
  db.query(
    `insert into gamebuddy.approved_matches (user_id, matched_id, created_at)
     values ${matches} on conflict do nothing;`,
  );

  const supers = partners
    .slice(0, 8)
    .map((p) => `('${id}', '${db.esc(p.userId)}', now())`)
    .join(', ');
  db.query(
    `insert into gamebuddy.super_likes (user_id, target_id, created_at)
     values ${supers} on conflict do nothing;`,
  );
}

/**
 * Finishes every mission in the current set and claims them all.
 *
 * Returns the board as it stood after the final claim — which, if that claim finished the
 * set, already contains the next three.
 */
async function finishSet(token, userId) {
  let board = body(await get(EARN, { token }));

  // The account already measures past every target (see seedActivity); the baseline is
  // what a fresh deal snapshots that away, so this is what makes the set finishable.
  db.query(
    `update gamebuddy.gamer_mission set baseline = 0
      where user_id = '${db.esc(userId)}' and set_index = ${board.missionSet};`,
  );

  const codes = board.missions.map((m) => m.code);
  // Only the ones still outstanding: an earlier test may have taken one of these already,
  // and re-claiming it is refused — correctly — with "nothing to claim yet".
  for (const mission of board.missions.filter((m) => !m.claimed)) {
    const res = await post(`${EARN}/mission/${mission.code}`, undefined, { token });
    assert.equal(
      res.status,
      200,
      `claiming ${mission.code} failed: ${JSON.stringify(res.json.status)}`,
    );
    board = body(res);
  }
  return { board, codes };
}

describe('missions and badges', () => {
  let gamer;

  before(async () => {
    gamer = await createAccount({});
    // 45 partners: LIKES_SENT counts one row per person and like-40 wants forty.
    seedActivity(gamer.userId, fixtures.take(45));
  });

  after(async () => {
    await cleanup();
  });

  // =======================================================================

  describe('the deal', () => {
    test('a new account is dealt three easy missions, none of them claimed', async () => {
      const board = body(await get(EARN, { token: gamer.token }));

      assert.equal(board.missionSet, 1);
      assert.equal(board.missionSetsTotal, SETS);
      assert.equal(board.missionBand, 'EASY');
      assert.equal(board.missionVeteran, false);
      assert.equal(board.missions.length, 3);
      assert.deepEqual(
        board.missions.map((m) => m.slot),
        [0, 1, 2],
        'the three arrive in slot order so the screen does not reshuffle between loads',
      );
      assert.ok(board.missions.every((m) => !m.claimed));
      assert.ok(board.missions.every((m) => m.progress === 0), 'history before the deal must not count');
    });

    test('reading the screen again does not deal a different three', async () => {
      const first = body(await get(EARN, { token: gamer.token }));
      const again = body(await get(EARN, { token: gamer.token }));

      assert.deepEqual(
        first.missions.map((m) => m.code),
        again.missions.map((m) => m.code),
      );
      assert.equal(first.missionSet, again.missionSet);
    });

    test('an unfinished mission pays nothing', async () => {
      const board = body(await get(EARN, { token: gamer.token }));
      const res = await post(`${EARN}/mission/${board.missions[0].code}`, undefined, { token: gamer.token });

      assert.equal(res.json.status.code, CODE.QUEST_UNFINISHED ?? '175');
      assert.equal(body(await get(EARN, { token: gamer.token })).coinBalance, board.coinBalance);
    });

    test('a mission that is not one of the three is refused', async () => {
      const board = body(await get(EARN, { token: gamer.token }));
      const mine = new Set(board.missions.map((m) => m.code));
      // A HARD-band code, which cannot possibly be on a first-set board.
      const notMine = ['talk-200', 'meet-15', 'host-3'].find((c) => !mine.has(c));

      const res = await post(`${EARN}/mission/${notMine}`, undefined, { token: gamer.token });
      assert.equal(res.status, 400);
    });

    test('an unknown code is refused rather than crashing', async () => {
      const res = await post(`${EARN}/mission/no-such-mission`, undefined, { token: gamer.token });
      assert.equal(res.status, 400);
    });

    test('the earn screen needs a token', async () => {
      assert.equal((await get(EARN)).status, 401);
    });
  });

  // =======================================================================

  describe('claiming', () => {
    test('a finished mission pays exactly what the row promised, once', async () => {
      const before = body(await get(EARN, { token: gamer.token }));
      const target = before.missions[0];

      db.query(
        `update gamebuddy.gamer_mission set baseline = 0
          where user_id = '${db.esc(gamer.userId)}'
            and set_index = ${before.missionSet} and slot = ${target.slot};`,
      );

      const paid = body(await post(`${EARN}/mission/${target.code}`, undefined, { token: gamer.token }));
      assert.equal(paid.coinBalance, before.coinBalance + target.reward);
      assert.ok(paid.missions.find((m) => m.code === target.code).claimed);

      // The second tap is the one that matters: the check and the write are not one
      // operation, so this is what the conditional UPDATE exists to stop.
      const again = await post(`${EARN}/mission/${target.code}`, undefined, { token: gamer.token });
      assert.notEqual(again.status, 200);
      assert.equal(body(await get(EARN, { token: gamer.token })).coinBalance, paid.coinBalance);
    });

    test('every credit leaves a ledger row, so the coin flow can explain itself', async () => {
      const rows = db.scalar(
        `select count(*) from gamebuddy.coin_ledger
          where user_id = '${db.esc(gamer.userId)}' and reason = 'WEEKLY_QUEST';`,
      );
      assert.ok(Number(rows) >= 1, 'a mission paid coins without recording why');
    });

    test('finishing the third of three deals the next three in the same response', async () => {
      const { board, codes } = await finishSet(gamer.token, gamer.userId);

      assert.equal(board.missionSet, 2, 'the response to the last claim carries the new set');
      assert.equal(board.missions.length, 3);
      assert.ok(board.missions.every((m) => !m.claimed));

      const now = board.missions.map((m) => m.code);
      assert.ok(
        now.every((c) => !codes.includes(c)),
        `the new three repeat the three just finished: ${codes} then ${now}`,
      );
    });
  });

  // =======================================================================

  describe('the campaign', () => {
    let veteran;
    let campaignPay = 0;
    const dealt = new Set();

    test('eight sets deal twenty-four distinct missions and the band escalates', async () => {
      // Set 1 is finished and set 2 is on screen from the block above.
      const bands = [];
      let board = body(await get(EARN, { token: gamer.token }));

      // Set 1's codes are already spent; collect from the current set onwards.
      while (board.missionSet <= SETS) {
        bands[board.missionSet] = board.missionBand;
        board.missions.forEach((m) => dealt.add(m.code));
        campaignPay += board.missions.reduce((sum, m) => sum + m.reward, 0);
        board = (await finishSet(gamer.token, gamer.userId)).board;
      }
      veteran = board;

      assert.equal(bands[2], 'EASY');
      assert.equal(bands[3], 'EASY');
      assert.equal(bands[4], 'MEDIUM');
      assert.equal(bands[6], 'MEDIUM');
      assert.equal(bands[7], 'HARD');
      assert.equal(bands[8], 'HARD');

      // Sets 2..8 are 21 of the 24; set 1's three were claimed before this block ran.
      assert.equal(dealt.size, 21, 'a mission was dealt twice inside the campaign');
    });

    test('the campaign ends — the ninth set pays the opening rate, not the peak', async () => {
      assert.equal(veteran.missionSet, SETS + 1);
      assert.equal(veteran.missionVeteran, true, 'past the campaign the screen has to say so');
      assert.equal(veteran.missionBand, 'HARD', 'the work stays hard');

      const easyRate = 15;
      const hardRate = 35;
      assert.ok(
        veteran.missions.every((m) => m.reward === easyRate),
        `a veteran set pays ${veteran.missions[0].reward}, not the opening rate — an escalating ladder that never resets is a faucet`,
      );
      assert.ok(easyRate < hardRate);
    });

    test('the veteran loop keeps dealing, and never repeats the set just finished', async () => {
      let previous = veteran.missions.map((m) => m.code);

      for (let i = 0; i < 4; i++) {
        const { board } = await finishSet(gamer.token, gamer.userId);
        const now = board.missions.map((m) => m.code);

        assert.equal(new Set(now).size, 3, 'the three must be distinct');
        assert.ok(
          now.every((c) => !previous.includes(c)),
          `a veteran set repeated the one before it: ${previous} then ${now}`,
        );
        assert.ok(board.missions.every((m) => m.reward === 15));
        previous = now;
      }
    });

    test('the whole campaign is worth what the bands say, and it is paid once', async () => {
      // 3 sets x 45 + 3 x 75 + 2 x 105, minus set 1 which was claimed before the walk.
      // Asserting the shape rather than a single total, because the rates are config.
      assert.equal(campaignPay, 45 * 2 + 75 * 3 + 105 * 2, 'sets 2-8 of the campaign');
    });
  });

  // =======================================================================

  describe('badges', () => {
    test('the hard tier is on the wire, and only it is animated', async () => {
      const board = body(await get(P.badges, { token: gamer.token }));

      assert.equal(board.total, 25);
      const prismatic = board.badges.filter((b) => b.tier === 'PRISMATIC');
      assert.equal(prismatic.length, 7);

      const animated = board.badges.filter((b) => b.animated);
      assert.equal(animated.length, 2, 'animated WebP is the most expensive thing the app decodes');
      assert.ok(animated.every((b) => b.tier === 'PRISMATIC'));
      assert.ok(
        animated.every((b) => b.icon.endsWith('.webp')),
        'an animated badge must point at the animated file',
      );
      assert.ok(
        board.badges.filter((b) => !b.animated).every((b) => b.icon.endsWith('.png')),
        'a still badge must not point at a file that was never generated',
      );
    });

    test('nothing pays more than 125, and a cosmetic badge pays no coins at all', async () => {
      const board = body(await get(P.badges, { token: gamer.token }));

      assert.ok(board.badges.every((b) => b.reward <= 125), 'the owner capped badge coins at 125');

      const trophies = board.badges.filter((b) => b.cosmeticName);
      assert.equal(trophies.length, 4);
      assert.ok(
        trophies.every((b) => b.reward === 0 && b.tier === 'PRISMATIC'),
        'a badge pays coins or a frame, never both',
      );
    });

    test('a trophy frame exists for every badge that promises one, and is not for sale', async () => {
      const board = body(await get(P.badges, { token: gamer.token }));

      for (const badge of board.badges.filter((b) => b.cosmeticName)) {
        const price = db.scalar(
          `select price from gamebuddy.cosmetic where unlocked_by_badge = '${db.esc(badge.code)}';`,
        );
        assert.ok(price !== null && price !== '', `${badge.code} promises a frame that does not exist`);
      }

      // The shop must not be offering them. Zero-priced and hidden is the arrangement;
      // zero-priced and visible would read as "Free", which is the one thing they are not.
      const store = body(await get(`${P.cosmetics}`, { token: gamer.token }));
      const onSale = [...store.frames, ...store.banners, ...store.themes].map((c) => c.name);
      for (const name of ['Magnetic', 'Unbroken', 'Collector', 'Trailblazer']) {
        assert.ok(!onSale.includes(name), `${name} is a trophy but the shop is selling it`);
      }
    });

    test('claiming a cosmetic badge hands over the frame and pays no coins', async () => {
      // Collector wants fifteen bought cosmetics, and seedActivity gave this account
      // twenty. Reading the board is what awards it.
      let board = body(await get(P.badges, { token: gamer.token }));
      const collector = board.badges.find((b) => b.code === 'collector');
      assert.ok(collector.earned, 'fifteen owned cosmetics did not earn Collector');

      const before = collector.reward;
      assert.equal(before, 0);

      const balanceBefore = board.coins;
      const after = body(await post(`${P.badges}/collector/collect`, undefined, { token: gamer.token }));

      assert.equal(after.coins, balanceBefore, 'a trophy badge must not also pay coins');
      const owns = db.scalar(
        `select count(*) from gamebuddy.gamer_cosmetic gc
           join gamebuddy.cosmetic c on c.id = gc.cosmetic_id
          where gc.user_id = '${db.esc(gamer.userId)}' and c.unlocked_by_badge = 'collector';`,
      );
      assert.equal(Number(owns), 1, 'the frame was not granted');

      // And once only.
      const again = await post(`${P.badges}/collector/collect`, undefined, { token: gamer.token });
      assert.notEqual(again.status, 200);
    });
  });
});
