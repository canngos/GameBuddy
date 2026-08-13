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

const light = paletteFor('light');
const dark = paletteFor('dark');

export type ThemeColors = Palette & { brand: string; onBrand: string };

/** The resolved palette for whatever theme is currently showing. */
export function useThemeColors(): ThemeColors {
  const { colorScheme } = useColorScheme();
  const base = colorScheme === 'dark' ? dark : light;
  return { ...base, brand: brand.DEFAULT, onBrand: '#FFFFFF' };
}

/** True when the dark theme is on screen, whatever the preference that produced it. */
export function useIsDark(): boolean {
  const { colorScheme } = useColorScheme();
  return colorScheme === 'dark';
}
