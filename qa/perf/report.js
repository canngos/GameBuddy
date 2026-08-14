#!/usr/bin/env node
/**
 * Turns qa/perf/results/*.json into the tables that go in the report.
 *
 *   node qa/perf/report.js
 *
 * Two tables. The ladder says whether each plan met the three DAU targets. The sweep says
 * where each plan stopped meeting them, which is the number that decides what to buy —
 * "passes at 50k DAU" is worth little without knowing whether the margin is 20% or 20x.
 */

const fs = require('node:fs');
const path = require('node:path');

const DIR = path.join(__dirname, 'results');
const PASS_P95 = 500;      // ms, the line between immediate and thinking
const PASS_ERRORS = 0.01;  // 1%

const files = fs.existsSync(DIR) ? fs.readdirSync(DIR).filter((f) => f.endsWith('.json')) : [];
if (files.length === 0) {
  console.error(`No results in ${DIR}. Run qa/perf/run-plan.ps1 first.`);
  process.exit(1);
}

const runs = files
  .map((f) => {
    try {
      return { file: f, ...JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8')) };
    } catch (_) {
      return null;
    }
  })
  .filter((r) => r && r.plan && r.plan !== 'warmup' && r.achieved);

const verdict = (r) =>
  (r.achieved.failedRate ?? 1) < PASS_ERRORS &&
  (r.latencyMs.p95 ?? 1e9) < PASS_P95 &&
  (r.achieved.droppedIterations ?? 0) < 10;

const ms = (x) => (x === undefined || x === null ? '-' : `${Math.round(x)}`);
/** Thousands separators, forced to commas — toLocaleString() renders 96000 as "96.000" on
 *  a machine with a European locale, which reads as ninety-six. */
const n = (x) => Math.round(x).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
const pct = (x) => (x === undefined || x === null ? '-' : `${(x * 100).toFixed(2)}%`);

// --- ladder ------------------------------------------------------------------------------
const ladder = runs.filter((r) => r.file.startsWith('ladder-'));
const plans = [...new Set(ladder.map((r) => r.plan))].sort();
const daus = [...new Set(ladder.map((r) => r.dau))].sort((a, b) => a - b);

console.log('\n## Ladder — does the plan carry the target?\n');
console.log('| Plan | DAU | offered req/s | achieved req/s | errors | p95 | p99 | feed p95 | verdict |');
console.log('| ---- | --- | ------------- | -------------- | ------ | --- | --- | -------- | ------- |');
for (const plan of plans) {
  for (const dau of daus) {
    const r = ladder.find((x) => x.plan === plan && x.dau === dau);
    if (!r) continue;
    console.log(
      `| ${plan} | ${n(dau)} | ${r.offered.requestsPerSecond.toFixed(1)} | ` +
      `${r.achieved.requestsPerSecond?.toFixed(1)} | ${pct(r.achieved.failedRate)} | ` +
      `${ms(r.latencyMs.p95)}ms | ${ms(r.latencyMs.p99)}ms | ${ms(r.feedLatencyMs.p95)}ms | ` +
      `${verdict(r) ? 'PASS' : 'FAIL'} |`,
    );
  }
}

// --- sweep -------------------------------------------------------------------------------
const sweep = runs.filter((r) => r.file.startsWith('sweep-'));
if (sweep.length) {
  console.log('\n## Sweep — where does it stop?\n');
  console.log('| Plan | sessions/s | equiv DAU | achieved req/s | errors | dropped | p95 | feed p95 | verdict |');
  console.log('| ---- | ---------- | --------- | -------------- | ------ | ------- | --- | -------- | ------- |');

  const byPlan = {};
  for (const r of sweep.sort((a, b) => a.offered.sessionsPerSecond - b.offered.sessionsPerSecond)) {
    console.log(
      `| ${r.plan} | ${r.offered.sessionsPerSecond} | ${n(r.dau)} | ` +
      `${r.achieved.requestsPerSecond?.toFixed(1)} | ${pct(r.achieved.failedRate)} | ` +
      `${r.achieved.droppedIterations} | ${ms(r.latencyMs.p95)}ms | ${ms(r.feedLatencyMs.p95)}ms | ` +
      `${verdict(r) ? 'pass' : 'FAIL'} |`,
    );
    byPlan[r.plan] ??= [];
    byPlan[r.plan].push(r);
  }

  console.log('\n## Ceiling and headroom\n');
  console.log('| Plan | last passing rate | equiv DAU | headroom over 50k DAU |');
  console.log('| ---- | ----------------- | --------- | --------------------- |');
  for (const [plan, rs] of Object.entries(byPlan)) {
    const passing = rs.filter(verdict);
    if (passing.length === 0) {
      console.log(`| ${plan} | none of the swept rates | - | below target |`);
      continue;
    }
    const best = passing[passing.length - 1];
    console.log(
      `| ${plan} | ${best.offered.sessionsPerSecond} sessions/s | ${n(best.dau)} | ` +
      `${(best.dau / 50000).toFixed(1)}x |`,
    );
  }
}

console.log(`\n(${runs.length} runs; pass = errors <${PASS_ERRORS * 100}%, p95 <${PASS_P95}ms, <10 dropped)\n`);
