/**
 * S1 — smoke.
 *
 * One user, one session, every endpoint the workload touches. Its job is to fail fast when
 * the harness is wrong rather than after a twenty-minute soak: a bad token pool, a wrong
 * port, an empty deck. Every later scenario assumes this one passed.
 */

import { check } from 'k6';
import http from 'k6/http';
import { BASE, tokens, pick, authHeaders, session } from './workload.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
    http_req_failed: ['rate==0'],
  },
};

export default function () {
  check(tokens, { 'token pool loaded': (t) => t.length > 1000 });

  const { token } = pick();
  const opts = authHeaders(token);

  const health = http.get(`${BASE}/actuator/health`, { tags: { name: 'health' } });
  check(health, { 'health UP': (r) => r.status === 200 });

  const info = http.get(`${BASE}/application/get/user/info`, { ...opts, tags: { name: 'user-info' } });
  check(info, {
    'user-info 200': (r) => r.status === 200,
    'minted token authenticates': (r) => r.status !== 401,
  });

  const feed = http.get(`${BASE}/match/get/recommendations`, { ...opts, tags: { name: 'feed' } });
  check(feed, {
    'feed 200': (r) => r.status === 200,
    // The single most likely harness fault: a population that does not intersect the
    // trained artefact returns an empty deck, and every later run then measures a cheap
    // request that does no ranking at all.
    'feed is populated': (r) => {
      try {
        return (r.json('body.data.recommendedGamers') || []).length > 10;
      } catch (_) {
        return false;
      }
    },
  });

  for (const [name, path] of [
    ['matches', '/match/get/matches'],
    ['inbox', '/messages/get/inbox'],
    ['liked-you', '/match/get/liked-you'],
    ['community', '/community/get/posts'],
    ['allowance', '/match/get/accept-allowance'],
  ]) {
    const res = http.get(`${BASE}${path}`, { ...opts, tags: { name } });
    check(res, { [`${name} 200`]: (r) => r.status === 200 });
  }

  // And the whole session once, so a fault in it surfaces here.
  session();
}
