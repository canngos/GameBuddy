import { useColorScheme } from 'nativewind';
import { brand as brandTokens, semantic } from './tokens';

/**
 * The brand palette, from the original Android app's `colors.xml`. Names match so the
 * two can be compared without translation.
 */
export const brand = brandTokens;

/**
 * JS-side view of the semantic tokens.
 *
 * Styling goes through `className`. This exists for the handful of React Native props
 * that take a colour *value* and cannot be reached by a class at all —
 * `placeholderTextColor`, `ActivityIndicator`'s `color`, the status-bar style,
 * `Stack`'s `contentStyle`.
 *
 * These used to be a hand-maintained second copy of the values in `global.css`, with a
 * comment asking whoever changed one to remember the other. Both are now derived from
 * `tokens.js`, so there is nothing left to remember.
 *
 * The one translation still done here is the key name: CSS wants `--field-focus`, JS
 * callers want `colors.fieldFocus`.
 */
type Palette = {
  canvas: string;
  surface: string;
  raised: string;
  elevated: string;
  line: string;
  content: string;
  muted: string;
  field: string;
  fieldFocus: string;
  primary: string;
  accent: string;
  gold: string;
  online: string;
  danger: string;
  success: string;
};

const paletteFor = (scheme: 'light' | 'dark'): Palette => ({
  canvas: semantic.canvas[scheme],
  surface: semantic.surface[scheme],
  raised: semantic.raised[scheme],
  elevated: semantic.elevated[scheme],
  line: semantic.line[scheme],
  content: semantic.content[scheme],
  muted: semantic.muted[scheme],
  field: semantic.field[scheme],
  fieldFocus: semantic['field-focus'][scheme],
  primary: semantic.primary[scheme],
  accent: semantic.accent[scheme],
  gold: semantic.gold[scheme],
  online: semantic.online[scheme],
  danger: semantic.danger[scheme],
  success: semantic.success[scheme],
});

export type ThemeColors = Palette & { brand: string; onBrand: string };

/**
 * The two resolved palettes, built once and frozen.
 *
 * `brand` and `onBrand` are folded in here rather than spread on at the end of
 * `useThemeColors`, and that is a performance decision rather than a tidiness one. The
 * spread allocated a fresh seventeen-key object on **every render of every one of the
 * forty-nine components that call the hook** — including `Icon`, which wraps every glyph in
 * every list row. A new identity every render defeats any `React.memo`, `useMemo` or
 * `useCallback` downstream that takes a colour, which is most of them.
 *
 * Frozen so that the sharing cannot become a bug: two components now hold the same object,
 * and a mutation in one would silently repaint the other.
 */
const light: ThemeColors = Object.freeze({
  ...paletteFor('light'),
  brand: brand.DEFAULT,
  onBrand: '#FFFFFF',
});
const dark: ThemeColors = Object.freeze({
  ...paletteFor('dark'),
  brand: brand.DEFAULT,
  onBrand: '#FFFFFF',
});

/**
 * The resolved palette for whatever theme is currently showing.
 *
 * Returns one of two stable objects, so the identity changes exactly when the scheme
 * changes — which is precisely when every consumer *should* re-render. NativeWind's
 * `useColorScheme` remains the subscription, so the repaint guarantee `useHairline`
 * depends on is untouched.
 */
export function useThemeColors(): ThemeColors {
  const { colorScheme } = useColorScheme();
  return colorScheme === 'dark' ? dark : light;
}

/** True when the dark theme is on screen, whatever the preference that produced it. */
export function useIsDark(): boolean {
  const { colorScheme } = useColorScheme();
  return colorScheme === 'dark';
}
