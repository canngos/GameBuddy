import { useColorScheme } from 'nativewind';

/**
 * The brand palette, from the original Android app's `colors.xml`. Names match so the
 * two can be compared without translation.
 */
export const brand = {
  DEFAULT: '#FF4D67',
  soft: '#E98090',
  deep: '#D93E56',
} as const;

/**
 * JS-side copies of the semantic tokens declared in `global.css`.
 *
 * Styling goes through `className`. This exists for the handful of React Native props
 * that take a colour *value* and cannot be reached by a class at all —
 * `placeholderTextColor`, `ActivityIndicator`'s `color`, the status-bar style,
 * `Stack`'s `contentStyle`. Those must match the CSS or the seams show, so any change
 * here belongs in `global.css` too.
 */
type Palette = {
  canvas: string;
  surface: string;
  raised: string;
  line: string;
  content: string;
  muted: string;
  field: string;
  fieldFocus: string;
  danger: string;
  success: string;
};

const light: Palette = {
  canvas: '#FFFFFF',
  surface: '#FFFFFF',
  raised: '#F6F7F9',
  line: '#E4E5EA',
  content: '#23252F',
  muted: '#6E7180',
  field: '#F0F1F4',
  fieldFocus: '#FFF1F3',
  danger: '#D93025',
  success: '#1E8E3E',
};

const dark: Palette = {
  canvas: '#131419',
  surface: '#1A1B22',
  raised: '#23252F',
  line: '#323440',
  content: '#F0F1F5',
  muted: '#9699A8',
  field: '#23252F',
  fieldFocus: '#30262C',
  danger: '#FF6B61',
  success: '#4CC470',
};

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
