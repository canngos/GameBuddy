#!/usr/bin/env node
/**
 * Turns the app's palette into Tailwind 4 theme variables.
 *
 * ## Why this script exists rather than a copied hex list
 *
 * `GameBuddy-App/src/theme/tokens.js` is the one place a GameBuddy colour is declared. Its
 * own header explains why: the values used to live in three hand-maintained files that had
 * to agree, "and three hand-maintained copies of one fact is where drift starts". A website
 * that re-typed those hexes would be the fourth copy, and the first one nobody would notice
 * going stale — the app and the site are edited months apart.
 *
 * So the site reads that file directly. It is CommonJS with `module.exports`, deliberately,
 * which is exactly what makes it importable from a plain Node script like this one.
 *
 * ## Why generated CSS rather than a config import
 *
 * Tailwind 4 is configured in CSS through `@theme`, not in `tailwind.config.js` — there is
 * no JavaScript config object left to require a module into. Generating the `@theme` block
 * is the equivalent move, and it runs before every dev and build (see `package.json`), so
 * the two cannot diverge without someone deleting a script.
 *
 * The output is git-ignored. It is a build artefact, not a source file, and checking it in
 * would recreate the duplication this avoids.
 *
 * ## Both themes, because the app has both
 *
 * The site offers the same light/dark choice the app does, so both palettes are emitted:
 * light on `:root`, dark under `[data-theme='dark']`.
 *
 * **Dark is not an inversion of light**, and that is why this reads the values rather than
 * computing them. The app's own notes are worth repeating: every accent was checked at 4.5:1
 * against the canvas *of its own theme* and moved until it passed, so `gold`, `online` and
 * `accent` are materially darker in light mode and `primary` is materially lighter in dark.
 * A site that picked one hex per token and flipped the background would fail contrast in one
 * of the two themes — which is exactly the giveaway the app was built to avoid.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

const TOKENS = '../GameBuddy-App/src/theme/tokens.js';

let tokens;
try {
  tokens = require(join(here, '..', TOKENS));
} catch (error) {
  // Named explicitly, because the failure otherwise reads as a missing node module and
  // sends you looking in the wrong repository entirely.
  console.error(
    `[tokens] Could not read ${TOKENS}.\n` +
      'The website takes its palette from the app, so GameBuddy-App must be a sibling of\n' +
      'GameBuddy-Web. If the app has moved, update TOKENS in this script.',
  );
  throw error;
}

const { brand, semantic } = tokens;

/**
 * The gradient stops, which `tokens.js` does not own.
 *
 * They live in `GameBuddy-App/src/theme/gradients.ts`, which is TypeScript and cannot be
 * required from here. Copied rather than imported, and the copy is narrow and commented —
 * but it is still a copy, so it is the one thing on this page that can drift.
 *
 * **The split between the two is not cosmetic.** White on the cyan end of `primary` measures
 * 1.54:1, so `primary` is for large surfaces only and `action` is the one that goes under a
 * label. Putting white text on `primary` is the bug that comment exists to prevent, in the
 * app and here equally.
 *
 * Per theme, again from the app: the light stops are darker because a colour picked to glow
 * on #0B0B12 disappears on #F7F7FB.
 */
const gradients = {
  light: {
    'primary-from': '#6D3AF0',
    'primary-to': '#00A0C4',
    'action-from': '#6D3AF0',
    'action-to': '#1163B5',
    'accent-from': '#D42540',
    'accent-to': '#8E2FCC',
  },
  dark: {
    'primary-from': '#7C4DFF',
    'primary-to': '#00E5FF',
    'action-from': '#7C4DFF',
    'action-to': '#2E5BE0',
    'accent-from': '#FF4D67',
    'accent-to': '#C04BFF',
  },
};

const lines = [
  '/*',
  ' * GENERATED — do not edit.',
  ` * Written by scripts/generate-tokens-css.mjs from ${TOKENS}.`,
  ' * Change a colour there and it changes in the app and on the site together.',
  ' */',
  '',
  '@theme {',
  '  /*',
  '   * Light is the @theme default so that Tailwind knows every token name. The dark values',
  '   * override the same custom properties below — which is what makes `bg-surface` switch',
  '   * theme without a single `dark:` variant anywhere in the markup.',
  '   */',
];

for (const [name, value] of Object.entries(semantic)) {
  lines.push(`  --color-${name}: ${value.light};`);
}

lines.push('', '  /* Brand, identical in both themes. */');
lines.push(`  --color-brand: ${brand.DEFAULT};`);
lines.push(`  --color-brand-soft: ${brand.soft};`);
lines.push(`  --color-brand-deep: ${brand.deep};`);

lines.push('', '  /* Gradient stops — see the note in the generator. */');
for (const [name, value] of Object.entries(gradients.light)) {
  lines.push(`  --color-${name}: ${value};`);
}

lines.push('', '  /* Two families, doing different jobs — as in the app. */');
lines.push("  --font-display: 'Chakra Petch', system-ui, sans-serif;");
lines.push("  --font-sans: 'Poppins', system-ui, sans-serif;");
lines.push('}', '');

lines.push('/*');
lines.push(' * Dark. Applied by a data attribute rather than a media query, because the toggle has');
lines.push(' * to be able to override the system preference — see the inline script in Base.astro.');
lines.push(' */');
lines.push("[data-theme='dark'] {");
for (const [name, value] of Object.entries(semantic)) {
  lines.push(`  --color-${name}: ${value.dark};`);
}
for (const [name, value] of Object.entries(gradients.dark)) {
  lines.push(`  --color-${name}: ${value};`);
}
lines.push('}', '');

const out = join(here, '..', 'src', 'styles', 'tokens.css');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, lines.join('\n'), 'utf8');

console.log(`[tokens] wrote ${Object.keys(semantic).length} semantic colours to src/styles/tokens.css`);
