#!/usr/bin/env node
/**
 * Checks the palette against WCAG AA.
 *
 * This exists because the redesign's palette was chosen *by* these numbers, not checked
 * against them afterwards, and several of the values look arbitrary without them —
 * `gold` is `#8A5C00` in light mode rather than something that looks like gold because
 * anything brighter fails on a near-white canvas. Without this script the next person to
 * "fix" that colour has no way to know they broke it.
 *
 * Run: npm run check:contrast
 */

const { semantic } = require('../src/theme/tokens');

/** Relative luminance, per WCAG 2.1. */
function luminance(hex) {
  const value = parseInt(hex.slice(1), 16);
  const channel = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return (
    0.2126 * channel((value >> 16) & 255) +
    0.7152 * channel((value >> 8) & 255) +
    0.0722 * channel(value & 255)
  );
}

function ratio(a, b) {
  const x = luminance(a);
  const y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

/*
 * Which background is the hard test differs by theme, and getting this backwards makes the
 * check pass while the app fails: in light mode `canvas` (#F7F7FB) is darker than
 * `surface`, so dark text has less contrast on canvas; in dark mode `surface` is lighter
 * than `canvas`, so light text has less contrast on surface.
 */
const BINDING_BACKGROUND = {
  light: () => semantic.canvas.light,
  dark: () => semantic.surface.dark,
};

/** Tokens that end up as text somewhere. 4.5:1 is the AA threshold for body copy. */
const TEXT_TOKENS = [
  'content',
  'muted',
  'primary',
  'accent',
  'gold',
  'online',
  'danger',
  'success',
];

/**
 * Gradient stops, and what sits on them.
 *
 * `action` carries white labels, so every stop needs 4.5:1. `accent` carries a white
 * *icon* only — WCAG allows 3:1 for graphical elements — which is exactly why the match
 * button has a heart on it and not the word "Match". `primary` is deliberately absent:
 * it runs to `#00E5FF`, where white measures 1.54:1, and it is for surfaces that carry no
 * small white text at all.
 */
const GRADIENTS = {
  action: {
    minimum: 4.5,
    on: '#FFFFFF',
    stops: { light: ['#6D3AF0', '#1163B5'], dark: ['#7C4DFF', '#2E5BE0'] },
  },
  accent: {
    minimum: 3,
    on: '#FFFFFF',
    stops: { light: ['#D42540', '#8E2FCC'], dark: ['#FF4D67', '#C04BFF'] },
  },
};

let failures = 0;

function report(label, value, minimum) {
  const ok = value >= minimum;
  if (!ok) failures += 1;
  const status = ok ? 'ok  ' : 'FAIL';
  console.log(`  ${status} ${label.padEnd(28)} ${value.toFixed(2).padStart(6)}  (min ${minimum})`);
}

console.log('\nText tokens, against the binding background of their own theme');
for (const scheme of ['light', 'dark']) {
  const background = BINDING_BACKGROUND[scheme]();
  console.log(`\n ${scheme} on ${background}`);
  for (const token of TEXT_TOKENS) {
    report(token, ratio(semantic[token][scheme], background), 4.5);
  }
}

console.log('\nGradient stops, against what sits on them');
for (const [name, spec] of Object.entries(GRADIENTS)) {
  for (const scheme of ['light', 'dark']) {
    console.log(`\n ${name} (${scheme}), ${spec.on} on each stop`);
    for (const stop of spec.stops[scheme]) {
      report(stop, ratio(spec.on, stop), spec.minimum);
    }
  }
}

/*
 * Card themes, against the white username that sits on them.
 *
 * Read from `src/theme/cardThemes.js` rather than restated here — these are *sold*, and a
 * palette copied into this file would let a bought theme ship with colours the gate never
 * saw. That is the whole argument for the app owning the palette instead of the database.
 *
 * Measured through the scrim, not against the raw stop. Every surface that draws a theme
 * puts the same fixed 0 -> 0.55 black gradient over it before any text lands (see
 * `CandidateCard`), so the honest question is what white measures against the *composited*
 * colour. That is stricter than checking the bare stop, and it is what is actually drawn.
 */
const { CARD_THEMES } = require('../src/theme/cardThemes');

/** The scrim at its darkest, where the username sits. */
const SCRIM_ALPHA = 0.55;

function underScrim(hex) {
  const value = parseInt(hex.slice(1), 16);
  // Composited over black at SCRIM_ALPHA: every channel simply loses that fraction.
  const mix = (c) => Math.round(c * (1 - SCRIM_ALPHA));
  const rgb = [(value >> 16) & 255, (value >> 8) & 255, value & 255].map(mix);
  return `#${rgb.map((c) => c.toString(16).padStart(2, '0')).join('')}`;
}

console.log('\nCard themes, #FFFFFF on each stop under the card scrim');
for (const [slug, stops] of Object.entries(CARD_THEMES)) {
  for (const scheme of ['light', 'dark']) {
    for (const stop of stops[scheme]) {
      report(`${slug} ${scheme} ${stop}`, ratio('#FFFFFF', underScrim(stop)), 4.5);
    }
  }
}

if (failures > 0) {
  console.error(`\n${failures} contrast failure(s).\n`);
  process.exit(1);
}
console.log('\nAll contrast checks passed.\n');
