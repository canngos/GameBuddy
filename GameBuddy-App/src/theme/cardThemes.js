/**
 * Card themes: the colours a bought theme paints somebody's card in.
 *
 * **Plain JS, and not TypeScript, for the same reason `tokens.js` is** — `scripts/
 * check-contrast.js` runs under Node and `require`s this file directly, so the palette the
 * gate checks and the palette the app draws are the same object rather than two copies
 * that can drift.
 *
 * **Why the colours live here and not in the database.** The backend sends a slug; this
 * maps it to stops. That is deliberate: the contrast gate can only test colours it can see
 * at build time, and a pair arriving from a database row would ship past CI unchecked — on
 * the one surface where getting it wrong makes a username unreadable. A row with no entry
 * here renders nothing, which is the failure we want: invisible, not illegible.
 *
 * **Why they are dark.** Each of these is a *surface*: a photo, a white username and a
 * white age line sit on top of it, and on the deck they sit under the same fixed black
 * scrim every card has. They are also deliberately outside the range `avatarGradient`
 * generates from a user id (bright, 55–62% saturation, 44–58% lightness), so a themed card
 * never looks like an untuned one.
 *
 * Light and dark are separate pairs rather than one pair used twice: the app's own tokens
 * do not share accent values between schemes, and a duotone tuned against `#0B0B12` reads
 * flat against `#F7F7FB`.
 */

/**
 * `animated` turns the edge, and it is declared here rather than read from the catalogue
 * row for the same reason the colours are: this file is what actually draws a theme, so
 * everything about how one looks lives in one place. The row's own `animated` flag is the
 * shop's label for it, and the two are kept in step by hand — a mismatch mislabels an item
 * in the shop, it does not misdraw it.
 *
 * @type {Readonly<Record<string, { light: [string, string], dark: [string, string], animated: boolean }>>}
 */
const CARD_THEMES = Object.freeze({
  viridian: Object.freeze({ light: ["#177E6A", "#0E3B33"], dark: ["#1E8F78", "#0A2E28"], animated: false }),
  glacier: Object.freeze({ light: ["#3E6E9E", "#1F3550"], dark: ["#4A7FB5", "#182A40"], animated: false }),
  midnight: Object.freeze({ light: ["#2B3A67", "#151C33"], dark: ["#33456F", "#101730"], animated: false }),
  cinder: Object.freeze({ light: ["#B33A1F", "#5C1A24"], dark: ["#C2451F", "#4A1420"], animated: false }),
  // The two dearest turn, which is what the price is for.
  royal: Object.freeze({ light: ["#6A3BAF", "#3A1E63"], dark: ["#7C4BC9", "#2E1850"], animated: true }),
  nova: Object.freeze({ light: ["#B32086", "#4A1060"], dark: ["#C92E99", "#3A0C4F"], animated: true }),
});

/**
 * The stops for a slug, or null when there is no such theme.
 *
 * Null rather than a fallback pair on purpose: the caller already has a fallback that is
 * correct — the gamer's own identity gradient — and inventing a colour here would paint
 * every unknown slug the same, which is worse than painting none of them.
 */
function cardThemeStops(slug, scheme) {
  const theme = slug ? CARD_THEMES[slug] : null;
  return theme ? theme[scheme] ?? theme.dark : null;
}

/** Whether this theme's edge turns. False for an unknown slug, which draws nothing anyway. */
function cardThemeAnimated(slug) {
  return !!(slug && CARD_THEMES[slug] && CARD_THEMES[slug].animated);
}

module.exports = { CARD_THEMES, cardThemeStops, cardThemeAnimated };
