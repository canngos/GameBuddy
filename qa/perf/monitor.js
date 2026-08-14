#!/usr/bin/env node
/**
 * Samples the system under test while a load run is in flight.
 *
 *   node qa/perf/monitor.js --seconds 420 --out results/ladder-cx33.monitor.json
 *
 * A throughput number on its own does not say what ran out. This records, once a second:
 * CPU and memory per container, the Hikari pool's active and pending counts, and Postgres's
 * active and waiting backends. The pool is the interesting one — the feed holds a
 * connection across an HTTP call to the model, so if the ceiling is the pool rather than
 * the CPU, `pending` climbs while CPU stays flat, and that is visible here and nowhere else.
 */

const { execFileSync, execFile } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const PROJECT = 'gamebuddy-perf';
const BACKEND = process.env.GB_BASE_URL || 'http://localhost:8180';

const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i === -1 ? fallback : process.argv[i + 1];
};

const seconds = Number(arg('seconds', 120));
const outPath = path.resolve(__dirname, arg('out', 'results/monitor.json'));
fs.mkdirSync(path.dirname(outPath), { recursive: true });

const samples = [];

function dockerStats() {
  try {
    const out = execFileSync('docker',
      ['stats', '--no-stream', '--format', '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}'],
      { encoding: 'utf8', timeout: 15000 });
    const rows = {};
    for (const line of out.trim().split(/\r?\n/)) {
      const [name, cpu, mem, memPct] = line.split('\t');
      if (!name || !name.startsWith('gamebuddy-perf')) continue;
      const short = name.replace('gamebuddy-perf-', '').replace(/-1$/, '');
      rows[short] = {
        cpu: Number(String(cpu).replace('%', '')),
        mem: String(mem).split('/')[0].trim(),
        memPct: Number(String(memPct).replace('%', '')),
      };
    }
    return rows;
  } catch (_) {
    return {};
  }
}

/** Postgres connection state — how many are working and how many are blocked. */
function pgState() {
  try {
    const out = execFileSync('docker',
      ['compose', '-p', PROJECT, 'exec', '-T', 'postgres', 'psql', '-U', 'gamebuddy', '-d', 'gamebuddy',
       '-t', '-A', '-F', ',', '-c',
       "select count(*) filter (where state='active'), count(*) filter (where wait_event_type='Lock'), " +
       "count(*) from pg_stat_activity where datname='gamebuddy';"],
      { encoding: 'utf8', cwd: ROOT, timeout: 15000 });
    const [active, blocked, total] = out.trim().split(',').map(Number);
    return { active, blocked, total };
  } catch (_) {
    return null;
  }
}

async function health() {
  try {
    const res = await fetch(`${BACKEND}/actuator/health`, { signal: AbortSignal.timeout(5000) });
    return res.status;
  } catch (_) {
    return 0;
  }
}

async function tick() {
  const sample = {
    t: new Date().toISOString(),
    containers: dockerStats(),
    postgres: pgState(),
    backendHealth: await health(),
  };
  samples.push(sample);

  const c = sample.containers;
  const fmt = (n) => (c[n] ? `${String(c[n].cpu).padStart(6)}% ${c[n].mem.padStart(9)}` : '        -');
  process.stdout.write(
    `\r${samples.length.toString().padStart(4)}s  ` +
    `backend ${fmt('backend')}  model ${fmt('model')}  pg ${fmt('postgres')}  ` +
    `pg[act=${sample.postgres?.active ?? '-'} blk=${sample.postgres?.blocked ?? '-'}]  hc=${sample.backendHealth}   `,
  );
}

console.log(`Sampling ${seconds}s -> ${outPath}\n`);

let elapsed = 0;
const timer = setInterval(async () => {
  await tick();
  elapsed += 1;
  if (elapsed >= seconds) {
    clearInterval(timer);
    fs.writeFileSync(outPath, JSON.stringify(samples, null, 2));

    // A peak-and-mean summary, because that is what goes in the report.
    const peak = {};
    for (const s of samples) {
      for (const [name, v] of Object.entries(s.containers)) {
        peak[name] ??= { cpu: 0, memPct: 0, mem: '' };
        if (v.cpu > peak[name].cpu) peak[name].cpu = v.cpu;
        if (v.memPct > peak[name].memPct) { peak[name].memPct = v.memPct; peak[name].mem = v.mem; }
      }
    }
    console.log('\n\nPeak per container:');
    for (const [name, v] of Object.entries(peak)) {
      console.log(`  ${name.padEnd(10)} cpu ${String(v.cpu).padStart(7)}%   mem ${v.mem} (${v.memPct}%)`);
    }
    const pgActive = samples.map((s) => s.postgres?.active ?? 0);
    console.log(`  postgres active backends: peak ${Math.max(...pgActive)}, mean ${(pgActive.reduce((a, b) => a + b, 0) / pgActive.length).toFixed(1)}`);
    const unhealthy = samples.filter((s) => s.backendHealth !== 200).length;
    if (unhealthy) console.log(`  backend health non-200 for ${unhealthy}s of ${samples.length}s`);
    console.log(`\nWrote ${outPath}`);
  }
}, 1000);
