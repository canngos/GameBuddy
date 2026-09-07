/**
 * The drawing convention the Shop tab's own artwork keeps.
 *
 * The Market is the one screen where an icon is a *product picture* rather than an
 * affordance — it is what somebody looks at while deciding to spend, so a 2px outline of a
 * coin is doing less work than it could. These two helpers are what make the shelf's marks
 * fuller than a Lucide glyph without abandoning the icon language the rest of the app
 * speaks: same 24×24 grid, same 1.5–2px weight, same single colour.
 *
 * **Depth comes from opacity, never from a second colour.** Every role token flips between
 * themes — gold goes `#8A5C00` → `#FFC53D`, accent `#D42540` → `#FF4D67` — so a hand-picked
 * "darker shade" would look right on exactly one of them. A translucent fill under a solid
 * rim reads as volume against any background, and where two shapes overlap the fills
 * compound, which is what makes one coin sit in front of another.
 *
 * Shared by `CoinPackIcon` and `ConsumableIcon` so the weight is one edit, not two.
 */

/** A filled shape: solid rim, translucent body. The higher the opacity, the nearer it reads. */
export const rimmed = (color: string, fillOpacity: number) => ({
  fill: color,
  fillOpacity,
  stroke: color,
  strokeWidth: 1.5,
});

/** An unfilled detail line — a tie, a rim mark, a plus. Lucide's caps and joins exactly. */
export const outline = (color: string) => ({
  stroke: color,
  strokeWidth: 1.5,
  fill: 'none' as const,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
});
