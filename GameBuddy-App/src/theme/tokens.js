/**
 * The palette. One source, three consumers.
 *
 * These values used to be written out by hand in three places that had to agree:
 * `global.css` (as space-separated RGB channels), `src/theme/colors.ts` (as hex, for the
 * React Native props that take a colour *value* rather than a class), and
 * `tailwind.config.js` (as the token names). Three hand-maintained copies of one fact is
 * where drift starts, and the redesign roughly doubled the token count.
 *
 * Now `tailwind.config.js` requires this file and emits the CSS variables itself, and
 * `colors.ts` imports it for the JS side. Nothing else declares a colour.
 *
 * **This file is `.js` with `module.exports`, deliberately.** `tailwind.config.js` is
 * loaded by Tailwind's own Node loader, not by Metro or Babel, so a `.ts` sibling would be
 * a bet on which loader is active when. `tokens.d.ts` next door keeps the TypeScript side
 * honest.
 *
 * Values are hex here because that is what a human reads and what a design tool exports.
 * The conversion to `R G B` channels — which is what makes `bg-surface/60` work, see
 * `tailwind.config.js` — happens at build time.
 */

/**
 * Brand colours, which are the same in both themes.
 *
 * `brand` is `#FF4D67`, carried over from the original Android app's `colors.xml`, and it
 * still means one thing: like, match, admirer. It is deliberately *not* the primary any
 * more — that is the violet/cyan gradient in `gradients.ts`. Splitting the two is what
 * lets a "like" read as a like rather than as just another button.
 */
const brand = {
  DEFAULT: '#FF4D67',
  soft: '#E98090',
  deep: '#D93E56',
};

/**
 * Semantic tokens, per theme.
 *
 * Dark is not an inversion of light. The dark canvas is near-black and surfaces lift
 * *towards* the viewer; `danger` and `success` are both lightened because the light-mode
 * red and green are too dark to read on it.
 *
 * **The two themes do not share accent values, and that is the point.** A colour picked
 * to glow on #0B0B12 is invisible on #F7F7FB. Every accent below was checked at 4.5:1
 * against the canvas of its own theme and moved until it passed — `gold`, `online` and
 * `accent` are all materially darker in light mode, and `primary` is materially lighter
 * in dark mode. A palette that uses one hex for both themes is how a "dark app with a
 * light mode" gives itself away.
 *
 * Note which background binds in each theme: in light mode `canvas` (#F7F7FB) is *darker*
 * than `surface`, so canvas is the harder test; in dark mode `surface` (#14141F) is
 * *lighter* than canvas, so surface is. Check new colours against the right one.
 */
const semantic = {
  // The light canvas is deliberately not pure white. Surfaces are white, so a white
  // canvas gave a card nothing to lift off — the old light theme distinguished cards
  // from their background by shadow alone, which is why it read as flat.
  canvas: { light: '#F7F7FB', dark: '#0B0B12' },
  surface: { light: '#FFFFFF', dark: '#14141F' },
  raised: { light: '#EFEFF6', dark: '#1E1E2D' },
  /*
   * The fourth depth step. Used by exactly one thing: the floating tab bar.
   *
   * That is not an accident of adoption — it is the only element in the app that floats
   * over content with no scrim behind it, and the ramps below explain why nothing else
   * should reach for it.
   *
   * The two ramps do not run the same way. In dark, each step up is lighter, so `elevated`
   * genuinely reads as nearer the viewer. In light, `surface` is already pure white and
   * every step past it gets *darker* — so `raised` and `elevated` are inset fills there
   * (fields, chips, wells), not elevation. Painting a light-mode sheet `elevated` makes it
   * look recessed, which is the opposite of the intent.
   *
   * The sheets that look like candidates (`FilterSheet`, `LimitSheet`, `UpgradePromptSheet`,
   * `ReportSheet`) are not: they sit on a scrim, and a scrim already separates them from the
   * content underneath. They keep `surface`.
   */
  elevated: { light: '#E7E7F2', dark: '#262637' },
  line: { light: '#E2E2EC', dark: '#2A2A3C' },
  content: { light: '#16161F', dark: '#ECECF5' },
  muted: { light: '#62627A', dark: '#8A8AA3' },
  field: { light: '#EFEFF6', dark: '#1A1A28' },
  // Tinted towards the primary violet rather than the old pink, so focus reads as "this
  // is the active control" in the same colour language as everything else interactive.
  'field-focus': { light: '#F0EBFF', dark: '#221B3A' },

  /*
   * The solid primary, for the places a gradient is overkill or impossible: focus rings,
   * hairlines, `ActivityIndicator`, a tinted icon. The gradient endpoints live in
   * `gradients.ts` and the light values there are darker for the same reason as here —
   * the dark-mode cyan is invisible on a near-white canvas.
   */
  primary: { light: '#6D3AF0', dark: '#8F66FF' },

  /*
   * Like, match, admirer. This is the old `brand` pink, and it stays pink on purpose:
   * it is the one colour in the app that carries a feeling, and a heart that is not
   * red or pink stops reading as a heart. It is now a *role* rather than the whole
   * identity — see the note above `brand`.
   */
  accent: { light: '#D42540', dark: '#FF4D67' },

  /** Coins, Gold membership, badge tiers. */
  gold: { light: '#8A5C00', dark: '#FFC53D' },

  /** Presence. Deliberately not `success` — "online" and "that worked" are not the same. */
  online: { light: '#00804A', dark: '#29E88F' },

  /*
   * Both light values are darker than the ones they replace (#D93025 and #1E8E3E). Those
   * were carried over from the original Android app and measured 4.47:1 and 3.94:1
   * against the new canvas — under the 4.5:1 needed for body text, and `ErrorNotice`
   * renders its message in `text-danger` at 15px. Deepening them was the smallest change
   * that makes the existing components legible; the hue is unchanged.
   */
  danger: { light: '#C62A20', dark: '#FF6B61' },
  success: { light: '#137A33', dark: '#4CC470' },
};

/** `#rrggbb` → `"r g b"`, the form `rgb(var(--x) / <alpha-value>)` needs. */
function channels(hex) {
  const value = parseInt(hex.slice(1), 16);
  return `${(value >> 16) & 255} ${(value >> 8) & 255} ${value & 255}`;
}

/** Every semantic token for one theme, as the `--name: r g b` pairs Tailwind's addBase wants. */
function cssVars(scheme) {
  const out = {};
  for (const [name, value] of Object.entries(semantic)) {
    out[`--${name}`] = channels(value[scheme]);
  }
  return out;
}

module.exports = { brand, semantic, channels, cssVars };
