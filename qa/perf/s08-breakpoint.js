/**
 * S8 — find the ceiling, and see how it fails.
 *
 * The ladder answers "does it carry the target". This answers "how much headroom is there
 * and what happens past it", which is the question that decides which plan to buy: a plan
 * that meets the target with 20% left is a plan that falls over on a good day.
 *
 * Ramps the session arrival rate up in steps and records, for each step, the achieved
 * throughput, the error rate and the p95. The ceiling is the last step where errors stayed
 * under 1% and p95 under 1s.
 *
 * The failure *mode* matters as much as the number. Three shapes, and they mean different
 * things:
 *   - 429s and 503s, latency flat        -> the system is shedding load deliberately. Good.
 *   - latency climbing, errors near zero -> a queue is forming. Survivable, briefly.
 *   - timeouts and dropped iterations    -> nothing is being served. This is the bad one.
 */

import { session, thresholds } from './workload.js';

/** Sessions per second at each step. 2.08/s is 50,000 DAU, so this spans ~50k to ~1.9M. */
const STEPS = (__ENV.GB_STEPS || '2,5,10,20,40,60,80').split(',').map(Number);
const STEP_SECONDS = Number(__ENV.GB_STEP_SECONDS || 45);

const scenarios = {};
STEPS.forEach((rate, i) => {
  scenarios[`step_${String(rate).padStart(3, '0')}`] = {
    executor: 'constant-arrival-rate',
    rate,
    timeUnit: '1s',
    duration: `${STEP_SECONDS}s`,
    preAllocatedVUs: Math.max(20, rate * 8),
    maxVUs: Math.max(50, rate * 25),
    startTime: `${i * (STEP_SECONDS + 10)}s`,
    exec: 'run',
    tags: { step: String(rate) },
  };
});

export const options = {
  scenarios,
  // No thresholds that abort: the whole point is to run past the point where they break.
  thresholds: {},
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  // Beyond the ceiling the server stops answering promptly; without a bound k6 waits and
  // the step blurs into the next one.
  httpDebug: '',
};

export function run() {
  session();
}

export function handleSummary(data) {
  const v = (k) => data.metrics[k]?.values ?? {};
  const d = v('http_req_duration');

  const summary = {
    plan: __ENV.GB_PLAN || 'unknown',
    steps: STEPS,
    stepSeconds: STEP_SECONDS,
    overall: {
      requests: v('http_reqs').count,
      requestsPerSecond: v('http_reqs').rate,
      failedRate: v('http_req_failed').rate,
      dropped: v('dropped_iterations').count ?? 0,
      p95: d['p(95)'],
      p99: d['p(99)'],
      max: d.max,
    },
  };

  return {
    stdout: `\n=== breakpoint ${summary.plan} ===\n` +
      `steps (sessions/s): ${STEPS.join(', ')}\n` +
      `overall ${summary.overall.requestsPerSecond?.toFixed(1)} req/s, ` +
      `errors ${(summary.overall.failedRate * 100).toFixed(2)}%, ` +
      `dropped ${summary.overall.dropped}, p95 ${d['p(95)']?.toFixed(0)}ms\n` +
      'Per-step numbers come from the monitor log and the k6 stderr progress lines.\n\n',
    [`results/breakpoint-${summary.plan}.json`]: JSON.stringify(summary, null, 2),
  };
}
