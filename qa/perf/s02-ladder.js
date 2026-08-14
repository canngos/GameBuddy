/**
 * S2 — one tier of the load ladder.
 *
 *   -e GB_DAU=50000 -e GB_STAGE_SECONDS=180
 *
 * One tier per run, on purpose. Running all three in one k6 process aggregates their
 * metrics into a single summary, so a tier that saturates drags the percentiles of the two
 * that did not and the result cannot be read. Separate runs also give each tier a cold-ish
 * start rather than the tail of the previous stage.
 */

import { targets, session, thresholds, REQUESTS_PER_SESSION } from './workload.js';

const DAU = Number(__ENV.GB_DAU || 10000);
const DURATION = Number(__ENV.GB_STAGE_SECONDS || 180);

/**
 * GB_SESSIONS_PER_SEC overrides the DAU-derived rate, which is how the breakpoint sweep
 * reuses this file: each step is an ordinary steady run at a stated rate, so every step
 * produces the same summary shape as a ladder tier and the two are directly comparable.
 * The equivalent DAU is reported back, since that is the unit the answer is given in.
 */
const override = __ENV.GB_SESSIONS_PER_SEC ? Number(__ENV.GB_SESSIONS_PER_SEC) : null;
const t = override
  ? (() => {
    const base = targets(DAU);
    const concurrent = override * 360;
    return {
      ...base,
      concurrent,
      sessionsPerSecond: override,
      rps: override * 13,
      // Invert the model: concurrent = DAU x 0.15 x (6/60), so DAU = concurrent / 0.015.
      dau: Math.round(concurrent / 0.015),
    };
  })()
  : targets(DAU);

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      // Sessions per second. Fractional rates are not allowed, so the rate is expressed
      // per minute for the small tiers — 1,000 DAU is 0.04 sessions/s, which as an integer
      // per second would round to zero and run nothing at all.
      rate: Math.max(1, Math.round(t.sessionsPerSecond * 60)),
      timeUnit: '1m',
      duration: `${DURATION}s`,
      // One VU can only run one session at a time, and a session takes about a second of
      // wall time here (no think time — the server does not care about the gaps). Headroom
      // so that dropped iterations mean the system fell behind, never the generator.
      preAllocatedVUs: Math.max(10, Math.ceil(t.sessionsPerSecond * 20)),
      maxVUs: Math.max(50, Math.ceil(t.sessionsPerSecond * 60)),
      exec: 'run',
    },
  },
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  console.log(
    `\n${t.dau} DAU -> ${Math.round(t.dau * 0.15)} in the peak hour -> ${t.concurrent} concurrent sessions\n` +
    `  offered: ${t.sessionsPerSecond.toFixed(3)} sessions/s = ${t.rps.toFixed(1)} req/s ` +
    `(${REQUESTS_PER_SESSION} req/session)\n`,
  );
  return t;
}

export function run() {
  session();
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (k) => m[k]?.values ?? {};
  const d = v('http_req_duration');
  const feed = v('http_req_duration{name:feed}');

  const summary = {
    plan: __ENV.GB_PLAN || 'unknown',
    dau: t.dau,
    durationSeconds: DURATION,
    offered: { sessionsPerSecond: t.sessionsPerSecond, requestsPerSecond: t.rps, concurrent: t.concurrent },
    achieved: {
      requests: v('http_reqs').count,
      requestsPerSecond: v('http_reqs').rate,
      iterations: v('iterations').count,
      droppedIterations: v('dropped_iterations').count ?? 0,
      failedRate: v('http_req_failed').rate,
    },
    latencyMs: {
      avg: d.avg, med: d.med, p95: d['p(95)'], p99: d['p(99)'], max: d.max,
    },
    feedLatencyMs: {
      avg: feed.avg, med: feed.med, p95: feed['p(95)'], p99: feed['p(99)'], max: feed.max,
    },
  };

  const pct = (x) => (x === undefined ? '-' : `${(x * 100).toFixed(2)}%`);
  const ms = (x) => (x === undefined ? '-' : `${x.toFixed(0)}ms`);

  const text = [
    '',
    `=== ${summary.plan} @ ${t.dau} DAU (${t.sessionsPerSecond.toFixed(2)} sessions/s) ===`,
    `offered   ${t.sessionsPerSecond.toFixed(3)} sessions/s  (~${t.rps.toFixed(1)} req/s, ${t.concurrent} concurrent)`,
    `achieved  ${summary.achieved.requestsPerSecond?.toFixed(1)} req/s over ${summary.achieved.requests} requests`,
    `dropped   ${summary.achieved.droppedIterations} iterations`,
    `errors    ${pct(summary.achieved.failedRate)}`,
    `latency   med ${ms(d.med)}  p95 ${ms(d['p(95)'])}  p99 ${ms(d['p(99)'])}  max ${ms(d.max)}`,
    `feed      med ${ms(feed.med)}  p95 ${ms(feed['p(95)'])}  p99 ${ms(feed['p(99)'])}  max ${ms(feed.max)}`,
    '',
  ].join('\n');

  return {
    stdout: text,
    [`results/${__ENV.GB_LABEL || `ladder-${summary.plan}-${t.dau}`}.json`]: JSON.stringify(summary, null, 2),
  };
}
