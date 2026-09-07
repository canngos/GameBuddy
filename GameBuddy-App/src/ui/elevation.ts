import { Platform, type ViewStyle } from 'react-native';

/**
 * Depth, expressed the way each platform actually supports it.
 *
 * NativeWind's coloured shadow utilities (`shadow-lg shadow-brand/40`) are not usable
 * here. Applying one on a state change — a chip becoming selected — crashed the app on
 * Android 8, and the error it produced was "Couldn't find a navigation context", which
 * points nowhere near the cause and cost an afternoon to trace back to a class name.
 *
 * They were the wrong tool regardless: Android has no coloured shadow before API 28,
 * and the minimum supported version here is API 26. A tinted shadow would silently
 * have been a plain grey one on the oldest devices we claim to support, so the class
 * was describing something the platform could not draw.
 *
 * `elevation` is what Android does have. iOS gets the real shadow, including the tint,
 * because there it means something.
 */
/**
 * `'none'` exists so a component that is lifted in one theme and not the other can keep
 * emitting the same style keys either way. Dropping the keys instead — passing
 * `undefined` on one theme — makes the element stop painting its subtree when the theme
 * flips at runtime. See `useHairline` for the whole story.
 */
/**
 * Resolved styles, keyed by the two inputs — same reasoning as `glow` next door.
 *
 * `lift` is pure and is called inline inside `style` arrays (`Card`, `Button`, the tab
 * bar), so a fresh object every render invalidated the array around it as well. Four
 * levels and, in practice, one colour, so this settles at four entries.
 */
const cache = new Map<string, ViewStyle>();
const MAX_ENTRIES = 64;

export function lift(level: 'none' | 'sm' | 'md' | 'lg', color = '#000000'): ViewStyle {
  const key = `${level}|${color}`;
  const hit = cache.get(key);
  if (hit !== undefined) return hit;

  const style = buildLift(level, color);
  if (cache.size >= MAX_ENTRIES) cache.clear();
  cache.set(key, style);
  return style;
}

function buildLift(level: 'none' | 'sm' | 'md' | 'lg', color: string): ViewStyle {
  const spec = LEVELS[level];

  return Platform.select<ViewStyle>({
    ios: {
      shadowColor: color,
      shadowOpacity: spec.opacity,
      shadowRadius: spec.radius,
      shadowOffset: { width: 0, height: spec.offsetY },
    },
    // `elevation` draws a system shadow whose colour is not ours to choose. Setting
    // shadowColor alongside it does nothing on Android and is left off deliberately.
    android: { elevation: spec.elevation },
    default: {
      // Web, where a real box-shadow works and is worth having.
      boxShadow: `0px ${spec.offsetY}px ${spec.radius * 2}px rgba(0,0,0,${spec.opacity})`,
    } as ViewStyle,
  })!;
}

const LEVELS = {
  none: { opacity: 0, radius: 0, offsetY: 0, elevation: 0 },
  sm: { opacity: 0.08, radius: 4, offsetY: 1, elevation: 2 },
  md: { opacity: 0.16, radius: 8, offsetY: 3, elevation: 5 },
  lg: { opacity: 0.24, radius: 14, offsetY: 6, elevation: 10 },
} as const;
