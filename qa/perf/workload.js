/**
 * The session model every scenario shares.
 *
 * How "active users" becomes a request rate
 * ----------------------------------------
 * 50,000 DAU is not 50,000 concurrent anything. The chain, stated so the arithmetic can be
 * argued with rather than trusted:
 *
 *     DAU x 15% peak-hour concentration x (6 min session / 60 min) = concurrent sessions
 *
 *       1,000 DAU ->   150 in the peak hour ->  ~15 concurrent
 *      10,000 DAU -> 1,500                  -> ~150 concurrent
 *      50,000 DAU -> 7,500                  -> ~750 concurrent
 *
 * A session is roughly 45 requests over six minutes, so one concurrent session is about
 * 0.125 req/s and 750 of them is ~95 req/s of HTTP plus the sockets. That is the number
 * the ladder targets.
 *
 * 15% is the standard peak-hour share for a consumer social app and is the assumption most
 * worth challenging: a product with one strong evening peak can be 25-30%, which doubles
 * the requirement. It is a constant here so it can be changed in one place.
 *
 * Why arrival rate, not VUs
 * -------------------------
 * A VU-count test hands the system a fixed number of clients that politely slow down when
 * it does, so a saturated system reports lower throughput and no errors and the run looks
 * like a pass. `constant-arrival-rate` holds the offered load whatever happens and reports
 * `dropped_iterations` when k6 cannot keep up — which is the signal that the system, not
 * the test, has run out.
 *
 * Identity spread
 * ---------------
 * Every iteration picks a random token from a pool of 20,000. That is not incidental: a
 * decision is rate limited to 30/min per gamer and the daily allowance is 50 swipes, so a
 * script running as a handful of identities measures the rate limiter and calls it a
 * throughput ceiling.
 */

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const BASE = __ENV.GB_BASE_URL || 'http://localhost:8180';

/** Loaded once per process and shared across VUs; 20,000 tokens is 5 MB per copy. */
export const tokens = new SharedArray('tokens', () => JSON.parse(open('./tokens.json')));

/** Minutes a session lasts, and what one session costs the server. */
export const SESSION_MINUTES = 6;
export const SESSION_SECONDS = SESSION_MINUTES * 60;

/**
 * Requests in one `session()` below: user-info, feed, ~6 swipes, then five tab reads.
 * Measured rather than assumed — the smoke run issues 23 for one session plus the extra
 * calls it makes on its own, and `session()` alone accounts for 13.
 */
export const REQUESTS_PER_SESSION = 13;

/**
 * DAU -> offered load.
 *
 * `sessionsPerSecond` is the number that matters and the one that is easy to get wrong.
 * A session lasts six *minutes*, so the arrival rate that sustains N concurrent sessions
 * is N / 360, not N / 6. The first version of this file divided by 6 and offered sixty
 * times the intended load — the 50,000 tier ran at 125 sessions/s, saturated instantly,
 * and would have been reported as "the system cannot carry 50k DAU" when what it could not
 * carry was three million.
 */
export function targets(dau) {
  const peakHourUsers = dau * 0.15;
  const concurrent = Math.round((peakHourUsers * SESSION_MINUTES) / 60);
  const sessionsPerSecond = concurrent / SESSION_SECONDS;
  return {
    dau,
    peakHourUsers,
    concurrent,
    sessionsPerSecond,
    rps: sessionsPerSecond * REQUESTS_PER_SESSION,
    sockets: concurrent,
  };
}

export function pick() {
  return tokens[randomIntBetween(0, tokens.length - 1)];
}

export function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

/**
 * Thresholds shared by the ladder.
 *
 * 500 ms at p95 is the line between an app that feels immediate and one that feels like it
 * is thinking. `http_req_failed` counts 4xx as failures too, which is wanted here: a 429
 * from the swipe quota is a correct response and still means the offered load is not being
 * served, so it belongs in the failure budget rather than hidden by it.
 */
export const thresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<500', 'p(99)<1500'],
  'http_req_duration{name:feed}': ['p(95)<800'],
  dropped_iterations: ['count<100'],
};

/**
 * One user session.
 *
 * The mix is taken from what the app actually calls on each screen, not from an even
 * spread over the endpoint list. Opening the app is a profile read and a feed; the feed is
 * then swiped through; matches, the inbox and the community feed are checked; a message is
 * occasionally sent. The feed is 1 request in 15 by count and by far the most expensive, so
 * its share is what decides the answer.
 */
export function session() {
  const { token } = pick();
  const opts = authHeaders(token);

  // Open the app.
  http.get(`${BASE}/application/get/user/info`, { ...opts, tags: { name: 'user-info' } });

  // The deck.
  const feed = http.get(`${BASE}/match/get/recommendations`, { ...opts, tags: { name: 'feed' } });
  check(feed, { 'feed 200': (r) => r.status === 200 });

  let candidates = [];
  if (feed.status === 200) {
    try {
      candidates = (feed.json('body.data.recommendedGamers') || []).map((g) => g.userId);
    } catch (_) {
      candidates = [];
    }
  }

  // Swipe. Deliberately below the 30/min limiter: this is one visit by one person, and a
  // person who swipes eight profiles in a session is already a heavy user.
  const swipes = Math.min(candidates.length, randomIntBetween(4, 8));
  for (let i = 0; i < swipes; i++) {
    const body = JSON.stringify({ userId: candidates[i], superLike: false });
    // Three declines to one accept, which is roughly what a swipe deck sees.
    const path = i % 4 === 3 ? 'accept' : 'decline';
    http.post(`${BASE}/match/${path}`, body, { ...opts, tags: { name: `swipe-${path}` } });
  }

  // The other tabs.
  http.get(`${BASE}/match/get/matches`, { ...opts, tags: { name: 'matches' } });
  http.get(`${BASE}/messages/get/inbox`, { ...opts, tags: { name: 'inbox' } });
  http.get(`${BASE}/match/get/liked-you`, { ...opts, tags: { name: 'liked-you' } });
  http.get(`${BASE}/community/get/posts`, { ...opts, tags: { name: 'community' } });
  http.get(`${BASE}/match/get/accept-allowance`, { ...opts, tags: { name: 'allowance' } });
}

/** The feed alone, for the scenario that isolates it. */
export function feedOnly() {
  const { token } = pick();
  const res = http.get(`${BASE}/match/get/recommendations`,
    { ...authHeaders(token), tags: { name: 'feed' } });
  check(res, { 'feed 200': (r) => r.status === 200 });
  return res;
}

/** A trivial authenticated read, for isolating the per-request authentication cost. */
export function authOnly() {
  const { token } = pick();
  http.get(`${BASE}/match/get/accept-allowance`,
    { ...authHeaders(token), tags: { name: 'allowance' } });
}
