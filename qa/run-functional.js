#!/usr/bin/env node
/**
 * Runs the functional suite and reports the state of the fixture pool around it.
 *
 *   node qa/run-functional.js                 all suites
 *   node qa/run-functional.js 03 04           only those
 *
 * Exists mainly so the fixture count is printed before and after. The suite consumes
 * untouched seeded gamers and the failure mode when it runs out is a confusing error deep
 * inside an unrelated test, so it is worth seeing the number go down.
 */

const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const DIR = path.join(__dirname, 'functional');
const filters = process.argv.slice(2);

const files = fs.readdirSync(DIR)
  .filter((f) => f.endsWith('.test.js'))
  .filter((f) => filters.length === 0 || filters.some((p) => f.startsWith(p)))
  .sort()
  .map((f) => path.join(DIR, f));

if (files.length === 0) {
  console.error('No matching suites.');
  process.exit(1);
}

const fixtures = require('./functional/helpers/fixtures');
const before = fixtures.remaining();
console.log(`Fixture pool before: ${before} untouched seeded gamers\n`);

const run = spawnSync(process.execPath, ['--test', ...files], { stdio: 'inherit' });

const after = fixtures.remaining();
console.log(`\nFixture pool after:  ${after} (${before - after} consumed)`);
if (after < 200) {
  console.log('Running low — reset with: node qa/reset-fixtures.js --apply');
}

// Sweep any QA accounts left behind by a process that died before its own cleanup.
require('./functional/helpers/accounts').cleanupAll();

process.exit(run.status ?? 1);
