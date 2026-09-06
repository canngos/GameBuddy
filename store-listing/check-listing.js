#!/usr/bin/env node
'use strict';

/*
 * Checks store-listing/listing/{en,fi,sv,de,fr,es,tr}.md against Google Play's
 * limits and the copy rules in GameBuddy-Brief.md.
 *
 *   node store-listing/check-listing.js
 *
 * Prints one line per problem as `lang/field: message` and exits 1 if there
 * are any. When everything passes it prints a table of lengths.
 *
 * Each file must have this exact shape (the parser is strict on purpose so
 * the text can be copied into Play Console without any hand editing):
 *
 *   # Play listing — <language name> (<locale>)
 *   Version: YYYY-MM-DD
 *   ## Title              + one ```text fence
 *   ## Short description  + one ```text fence
 *   ## Full description   + one ```text fence
 *
 * Lengths are counted in Unicode code points ([...s].length) after trimming,
 * which is what Play counts.
 *
 * The "dating" rule. The brief forbids any dating framing, but every listing
 * carries a NOT A DATING APP section whose heading and first sentence must
 * say the word in order to deny it. The rule implemented here: the language's
 * dating words may appear only inside a sentence that also contains the
 * language's negation word (en "not", fi "ei", sv "inte", de "kein/nicht",
 * fr "pas", es "no", tr "değil"). Sentences are split on newlines and on
 * . ! ? ; so the heading line counts as its own sentence. Everything in the
 * `never` list (end-to-end, hookup, coming soon, superlatives) is forbidden
 * in every sentence, negated or not.
 */

const fs = require('fs');
const path = require('path');

// LISTING_DIR overrides the directory, so a scratch copy can be checked.
const DIR = process.env.LISTING_DIR || path.join(__dirname, 'listing');
const TITLE = 'GameBuddy: Find Gamers to Play';
const LIMITS = { title: 30, short: 80, full: 4000 };
// The copy rule for the full description, reported in the table but not fatal.
const FULL_TARGET = { min: 2400, max: 3200 };

// Forbidden in every language, in every sentence.
const NEVER_ALL = [
  { re: /end-to-end|\be2e\b/i, why: 'claims end-to-end encryption (messages are encrypted at rest only)' },
  { re: /coming soon/i, why: 'the app is live in closed testing; do not say "coming soon"' },
  { re: /#1|number one|best app|!!/i, why: 'superlative or hype claim' },
  { re: /!/, why: 'exclamation mark' },
  { re: /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{1F000}-\u{1F2FF}]/u, why: 'emoji' },
];

const LANGS = {
  en: { locale: 'en-US', never: [{ re: /hookup/i, why: 'hookup' }], dating: /\bdating\b/i, negation: /\bnot\b/ },
  fi: { locale: 'fi-FI', never: [], dating: /deitti|treffi/i, negation: /\bei\b/ },
  sv: { locale: 'sv-SE', never: [], dating: /dejting|dejta/i, negation: /\binte\b/ },
  de: { locale: 'de-DE', never: [{ re: /ende-zu-ende/i, why: 'claims end-to-end encryption' }], dating: /\bdating\b/i, negation: /\bkeine?\b|\bnicht\b/ },
  fr: { locale: 'fr-FR', never: [{ re: /bout en bout/i, why: 'claims end-to-end encryption' }], dating: /de rencontre/i, negation: /\bpas\b/ },
  es: { locale: 'es-ES', never: [{ re: /extremo a extremo/i, why: 'claims end-to-end encryption' }], dating: /\bcitas\b/i, negation: /\bno\b/ },
  tr: { locale: 'tr-TR', never: [{ re: /uçtan uca/i, why: 'claims end-to-end encryption' }], dating: /flört/i, negation: /değil/ },
};

const SECTIONS = ['Title', 'Short description', 'Full description'];
const FIELD = { Title: 'title', 'Short description': 'short', 'Full description': 'full' };

const problems = [];
const report = (lang, field, message) => problems.push(`${lang}/${field}: ${message}`);

const length = (s) => [...s.trim()].length;

function parse(lang, text) {
  const lines = text.replace(/\r\n/g, '\n').split('\n');

  const header = lines[0] || '';
  const m = header.match(/^# Play listing — (.+) \(([a-z]{2}-[A-Z]{2})\)$/);
  if (!m) {
    report(lang, 'file', 'first line must be "# Play listing — <language> (<locale>)"');
  } else if (m[2] !== LANGS[lang].locale) {
    report(lang, 'file', `locale is ${m[2]}, expected ${LANGS[lang].locale}`);
  }
  if (!lines.some((l) => /^Version: \d{4}-\d{2}-\d{2}$/.test(l))) {
    report(lang, 'file', 'missing "Version: YYYY-MM-DD" line');
  }

  const headings = [];
  lines.forEach((l, i) => { if (l.startsWith('## ')) headings.push({ name: l.slice(3).trim(), at: i }); });
  const names = headings.map((h) => h.name);
  if (names.join('|') !== SECTIONS.join('|')) {
    report(lang, 'file', `sections must be exactly [${SECTIONS.join(', ')}] in that order; found [${names.join(', ')}]`);
    return null;
  }

  const fields = {};
  headings.forEach((h, idx) => {
    const end = idx + 1 < headings.length ? headings[idx + 1].at : lines.length;
    const body = lines.slice(h.at + 1, end).join('\n');
    const fences = [...body.matchAll(/```(\w*)\n([\s\S]*?)\n```/g)];
    const field = FIELD[h.name];
    if (fences.length !== 1) {
      report(lang, field, `expected exactly one \`\`\`text fence, found ${fences.length}`);
      return;
    }
    if (fences[0][1] !== 'text') {
      report(lang, field, `fence must be \`\`\`text, found \`\`\`${fences[0][1]}`);
    }
    fields[field] = fences[0][2];
  });
  return fields;
}

function sentences(text) {
  return text
    .split(/\n+|(?<=[.!?;])\s+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function check(lang, fields) {
  const cfg = LANGS[lang];

  if (fields.title !== undefined && fields.title.trim() !== TITLE) {
    report(lang, 'title', `must be exactly "${TITLE}" in every language`);
  }

  for (const field of ['title', 'short', 'full']) {
    const value = fields[field];
    if (value === undefined) continue;
    const n = length(value);
    if (n > LIMITS[field]) report(lang, field, `${n} code points, limit ${LIMITS[field]}`);
    if (n === 0) report(lang, field, 'empty');

    for (const rule of [...NEVER_ALL, ...cfg.never]) {
      if (rule.re.test(value)) report(lang, field, `forbidden: ${rule.why} (${rule.re})`);
    }

    // Dating words: allowed only in a sentence that also negates them.
    for (const s of sentences(value)) {
      const lower = s.toLocaleLowerCase(cfg.locale);
      if (cfg.dating.test(lower) && !cfg.negation.test(lower)) {
        report(lang, field, `dating word outside a negated sentence: "${s.slice(0, 60)}"`);
      }
    }
  }

  if (fields.full !== undefined && !fields.full.includes('18')) {
    report(lang, 'full', 'must state the 18+ age limit');
  }
}

const rows = [];
for (const lang of Object.keys(LANGS)) {
  const file = path.join(DIR, `${lang}.md`);
  if (!fs.existsSync(file)) {
    report(lang, 'file', `missing ${file}`);
    continue;
  }
  const fields = parse(lang, fs.readFileSync(file, 'utf8'));
  if (!fields) continue;
  check(lang, fields);
  const full = fields.full === undefined ? 0 : length(fields.full);
  const target = full < FULL_TARGET.min ? 'short' : full > FULL_TARGET.max ? 'long' : 'ok';
  rows.push({
    lang,
    locale: LANGS[lang].locale,
    title: fields.title === undefined ? '-' : `${length(fields.title)}/${LIMITS.title}`,
    short: fields.short === undefined ? '-' : `${length(fields.short)}/${LIMITS.short}`,
    full: fields.full === undefined ? '-' : `${full}/${LIMITS.full}`,
    target,
  });
}

if (problems.length) {
  for (const p of problems) console.log(p);
  console.log(`\n${problems.length} problem${problems.length === 1 ? '' : 's'}.`);
  process.exit(1);
}

const pad = (s, n) => String(s).padEnd(n);
console.log(`${pad('lang', 5)}${pad('locale', 7)}${pad('title', 8)}${pad('short', 8)}${pad('full', 11)}target ${FULL_TARGET.min}-${FULL_TARGET.max}`);
for (const r of rows) {
  console.log(`${pad(r.lang, 5)}${pad(r.locale, 7)}${pad(r.title, 8)}${pad(r.short, 8)}${pad(r.full, 11)}${r.target}`);
}
console.log('\nAll seven listings pass.');
