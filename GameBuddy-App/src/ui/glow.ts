import { Platform, type ViewStyle } from 'react-native';

/**
 * Coloured light around an element — the thing that makes the neon language read as neon.
 *
 * Sibling of `lift()` in `elevation.ts`, and it deliberately copies that file's contract
 * rather than inventing a second one. Read that file first; the reasoning there about why
 * NativeWind's `shadow-*` classes are unusable applies here unchanged and is not repeated.
 *
 * **What is genuinely different, and why this file exists at all.** The post-mortem in
 * `elevation.ts` concluded that Android cannot draw a coloured shadow, and at the time
 * that was true: `elevation` takes no colour, and `setOutlineSpotShadowColor` is API 28+
 * against a floor of API 26. But that conclusion was reached about the `elevation` path
 * and about a NativeWind *class*. React Native 0.76 shipped `boxShadow` as a real style
 * prop on the New Architecture, Android included, implemented as a drawable — so it does
 * honour colour, and it is not API-gated. This project is on 0.86, and `elevation.ts`
 * already uses `boxShadow` on web.
 *
 * So Android gets `boxShadow` here. If that turns out to render wrong on the oldest
 * supported devices, `ANDROID_STRATEGY` below is the one line to change, and
 * `src/ui/Halo.tsx` is the fallback.
 */

export type GlowLevel = 'none' | 'soft' | 'strong';

/**
 * How Android draws a glow.
 *
 * `'boxShadow'` is correct on the New Architecture and is what we ship. `'none'` degrades
 * to no glow at all — honest, and better than the alternative that used to be here (a
 * tinted border pretending to be light). Flip this, do not scatter Platform checks at call
 * sites.
 */
const ANDROID_STRATEGY: 'boxShadow' | 'none' = 'boxShadow';

const LEVELS = {
  none: { opacity: 0, radius: 0 },
  soft: { opacity: 0.45, radius: 12 },
  strong: { opacity: 0.75, radius: 22 },
} as const;

/**
 * @param level  How much light. `'none'` is not "skip this" — see below.
 * @param color  The glow colour, as `#rrggbb`. Usually a gradient endpoint or `accent`.
 *
 * `'none'` returns the same style *keys* with zeroed values rather than an empty object,
 * for the same reason `lift('none')` and `useHairline()` do: a resolved style whose key
 * set appears on one theme and is absent on the other stops NativeWind 4.2.6 painting the
 * whole subtree when the theme flips. Same keys every time, only the values move.
 *
 * The key set does vary by *platform*, which is safe — `Platform.OS` cannot change at
 * runtime.
 */
export function glow(level: GlowLevel, color: string): ViewStyle {
  const spec = LEVELS[level];

  return Platform.select<ViewStyle>({
    ios: {
      shadowColor: color,
      shadowOpacity: spec.opacity,
      shadowRadius: spec.radius,
      // Offset zero is what separates a glow from a shadow: light on all sides, not
      // an object sitting above a surface.
      shadowOffset: { width: 0, height: 0 },
    },
    android:
      ANDROID_STRATEGY === 'boxShadow'
        ? { boxShadow: boxShadowFor(spec.radius, color, spec.opacity) }
        : // Keys still present, still zeroed. See above.
          { boxShadow: boxShadowFor(0, color, 0) },
    default: { boxShadow: boxShadowFor(spec.radius, color, spec.opacity) } as ViewStyle,
  })!;
}

/** `0 0 <blur> <color>` — no offset and no spread, which is what makes it read as light. */
function boxShadowFor(radius: number, color: string, opacity: number): string {
  return `0px 0px ${radius}px ${rgba(color, opacity)}`;
}

/**
 * `#rrggbb` → `rgba(r, g, b, a)`.
 *
 * `boxShadow` takes a CSS colour string, so the opacity has to be baked into it rather
 * than passed alongside as it is on iOS.
 *
 * Anything that is not a six-digit hex is handed through untouched, which is a real if
 * narrow limitation rather than a complete conversion: the per-identity `hsl()` seeds from
 * `avatarColor` are valid CSS and will render, but at **full opacity**, so a `'soft'` glow
 * in that colour comes out looking like a `'strong'` one. Every token in `tokens.js` is
 * hex, so nothing in the design system hits this path — only a caller that glows an avatar
 * would, and none does yet. Widen the parse before writing the first one.
 */
function rgba(color: string, opacity: number): string {
  const match = /^#([0-9a-f]{6})$/i.exec(color);
  if (!match) return color;

  const value = parseInt(match[1], 16);
  const r = (value >> 16) & 255;
  const g = (value >> 8) & 255;
  const b = value & 255;
  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}
