#!/usr/bin/env node
/**
 * Refuses to build while the legal documents still contain placeholders.
 *
 * The operator and controller details were blank until 2026-08-14 — `_[legal entity name]_`
 * and `_[contact email]_`. Publishing a privacy policy in that state is not a cosmetic
 * problem: Google Play reviews the policy, and the GDPR requires a named controller with a
 * reachable address. A page that says `[contact email]` fails both.
 *
 * They are filled in now, so this check passes today and will keep passing until somebody
 * edits those files. That is exactly when it earns its place — the documents live in
 * `documentation/legal/`, are edited months apart from this site, and the next person to
 * touch them will not remember this conversation.
 *
 * Runs from `prebuild`, so a deploy cannot go out with a placeholder in it.
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const legal = join(here, '..', '..', 'documentation', 'legal');

const DOCUMENTS = ['TERMS.md', 'PRIVACY.md', 'CHILD_SAFETY.md'];

/**
 * What must never reach production.
 *
 * The bracket forms are the placeholders as written. `TODO`/`FIXME` are here because a
 * half-finished amendment is the other way these documents go out wrong, and it costs
 * nothing to catch.
 */
const FORBIDDEN = [
  /\[legal entity name\]/i,
  /\[contact email\]/i,
  /\bTODO\b/,
  /\bFIXME\b/,
  /\bXXX\b/,
];

/** Things a published legal document must contain, rather than must not. */
const REQUIRED = [
  { pattern: /contact@findgamebuddy\.com/, why: 'a contact address for legal and data-protection requests' },
  { pattern: /Can Baturlar/, why: 'the named operator/controller, which the GDPR requires' },
];

const problems = [];

for (const name of DOCUMENTS) {
  const path = join(legal, name);
  let text;
  try {
    text = readFileSync(path, 'utf8');
  } catch {
    problems.push(`${name}: not found at ${path}`);
    continue;
  }

  for (const pattern of FORBIDDEN) {
    const match = pattern.exec(text);
    if (match) {
      const line = text.slice(0, match.index).split('\n').length;
      problems.push(`${name}:${line} still contains a placeholder — "${match[0]}"`);
    }
  }

  for (const { pattern, why } of REQUIRED) {
    if (!pattern.test(text)) {
      problems.push(`${name} is missing ${why} (expected to match ${pattern})`);
    }
  }
}

if (problems.length) {
  console.error('\n[legal] These documents are not publishable:\n');
  for (const problem of problems) console.error(`  ✖ ${problem}`);
  console.error(
    '\nThey are served at /terms, /privacy and /child-safety, and both stores check them.\n' +
      'Fix documentation/legal/ and build again.\n',
  );
  process.exit(1);
}

console.log(`[legal] ${DOCUMENTS.length} documents checked, no placeholders`);
