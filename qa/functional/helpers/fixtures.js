/**
 * Hands out seeded gamers that have never been used.
 *
 * The first version of this took fixtures at fixed offsets — `seeded(2, 500)` — and it
 * passed exactly once. On the second run those accounts already had matches, chat rooms
 * and a spent swipe allowance, so "no room should exist yet" failed and a decline that
 * should have hit the 30/minute rate limiter hit the 50/day quota instead. Both looked
 * like product defects and neither was.
 *
 * So a fixture is only handed out if it is genuinely untouched: no swipes spent, no
 * matches, no declines, no chat. There are 20,000 seeded accounts and a full run consumes
 * a few dozen, which is enough for a long time — and `npm run qa:reset` (see qa/README.md)
 * puts them back.
 */

const db = require('./db');

let pool = null;
let handedOut = 0;

/**
 * Which slice of the population this process may draw from.
 *
 * `node --test` runs each file in its own process, and two processes drawing at random
 * from the same 19,000 rows will eventually pick the same gamer — at which point one
 * file's swipes spend another file's allowance and a test that asserts "one accept costs
 * exactly one swipe" fails for reasons entirely outside itself. It happened about one run
 * in three.
 *
 * The file's numeric prefix (01-signup, 02-feed, …) gives each process a disjoint bucket,
 * so the slices cannot overlap at all rather than merely rarely. Anything without a prefix
 * gets a random bucket, which is the old behaviour and fine for a single ad-hoc process.
 */
const BUCKETS = 16;
const BUCKET = (() => {
  const file = process.argv.find((a) => a.includes('.test.js')) ?? '';
  const prefix = /(\d+)-/.exec(file.split(/[\\/]/).pop() ?? '');
  return prefix ? Number(prefix[1]) % BUCKETS : Math.floor(Math.random() * BUCKETS);
})();

/** Loads a batch of untouched fixtures from this process's bucket. */
function refill(n) {
  const rows = db.query(
    `select g.email, g.user_id from gamebuddy.gamer g
     where g.email like '%@bot.gamebuddy.invalid'
       and not g.is_blocked
       and abs(hashtext(g.user_id)) % ${BUCKETS} = ${BUCKET}
       and g.swipes_used = 0
       and g.accepts_used = 0
       -- Subscription state counts as "touched" too. A fixture the billing suite granted
       -- Gold to on a previous run has no swipes and no matches, so it looked pristine and
       -- was handed straight back out — and every test that starts by asserting BASIC
       -- failed, in a different place each run depending on who drew it.
       and g.subscription_tier = 'BASIC'
       and g.subscription_expires_at is null
       and g.coin = 0
       and not exists (select 1 from gamebuddy.approved_matches m
                        where m.user_id = g.user_id or m.matched_id = g.user_id)
       and not exists (select 1 from gamebuddy.declined_matches d
                        where d.user_id = g.user_id or d.declined_id = g.user_id)
       and not exists (select 1 from gamebuddy.chat_participant p where p.user_id = g.user_id)
     order by random() limit ${Number(n)};`,
  );

  if (rows.length < n) {
    throw new Error(
      `Only ${rows.length} untouched fixtures left in bucket ${BUCKET} (needed ${n}). ` +
      'Reset with: node qa/reset-fixtures.js --apply',
    );
  }
  return rows.map(([email, userId]) => ({ email, userId }));
}

/**
 * @param {number} n how many fixtures this test needs
 * @returns {{email:string,userId:string,token:string}[]}
 */
function take(n = 1) {
  const { mint } = require('./tokens');
  if (!pool || pool.length < n) pool = refill(Math.max(n, 60));
  const chosen = pool.splice(0, n);
  handedOut += n;
  return chosen.map((f) => ({ ...f, token: mint(f.email) }));
}

/** How many untouched fixtures remain. Reported by the runner so exhaustion is visible. */
function remaining() {
  return Number(db.scalar(
    `select count(*) from gamebuddy.gamer g
     where g.email like '%@bot.gamebuddy.invalid' and not g.is_blocked
       and g.swipes_used = 0 and g.accepts_used = 0
       and not exists (select 1 from gamebuddy.approved_matches m
                        where m.user_id = g.user_id or m.matched_id = g.user_id)
       and not exists (select 1 from gamebuddy.chat_participant p where p.user_id = g.user_id);`,
  ));
}

module.exports = { take, remaining, used: () => handedOut };
