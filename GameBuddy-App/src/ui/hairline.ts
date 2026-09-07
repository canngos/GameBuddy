import type { ViewStyle } from 'react-native';
import { useIsDark } from '../theme';
// Straight from the tokens rather than through `useThemeColors`: the value is needed at
// module scope, and reading it here also drops a second subscription from every consumer.
import { semantic } from '../theme/tokens';

/**
 * The dark theme's hairline border, as a style rather than a `dark:` class.
 *
 * **Do not reintroduce `dark:` variants.** They do not survive a runtime theme change in
 * NativeWind 4.2.6: switching dark → light leaves the old styling attached and, in any
 * subtree that does not re-render for another reason, the whole subtree stops painting.
 * The deck card, the profile card and the settings groups all went blank on a white
 * canvas — the app looked broken rather than light. Everything else on those screens was
 * fine, and the five components carrying a `dark:` class were exactly the five that
 * vanished.
 *
 * Reading the scheme through a hook fixes both halves of that. The style is recomputed
 * from the live value, and subscribing re-renders the component when the theme flips —
 * which is the part `dark:` was silently not doing.
 *
 * This is the same reasoning as `lift()` next door: some things cannot go through a
 * class, and it is better to have one honest exception than a class that works until
 * someone changes the theme.
 */
/*
 * Both keys are always present, and the light value is a zero-width transparent border
 * rather than nothing at all. That is not fussiness: an element whose resolved style
 * *appears* on one theme and is absent on the other stops painting its whole subtree when
 * the theme flips. Same keys every time, only the values move.
 *
 * Precomputed and frozen rather than built per render. There are exactly two possible
 * answers and the theme is the only input, so building a fresh object each time bought
 * nothing and cost every consumer its memoisation — the style goes straight into a `style`
 * array, where a new identity invalidates the array too. Freezing keeps the sharing honest.
 */
const DARK_HAIRLINE: ViewStyle = Object.freeze({
  borderWidth: 1,
  borderColor: semantic.line.dark,
});
const LIGHT_HAIRLINE: ViewStyle = Object.freeze({ borderWidth: 0, borderColor: 'transparent' });

export function useHairline(): ViewStyle {
  return useIsDark() ? DARK_HAIRLINE : LIGHT_HAIRLINE;
}
