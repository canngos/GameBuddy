import { LinearGradient } from 'expo-linear-gradient';
import { cssInterop } from 'nativewind';
import Animated from 'react-native-reanimated';
import { View, type ViewProps } from 'react-native';
import { useGradient, type GradientName } from '../theme';
import { cn } from './cn';

/**
 * Registers `className` on `LinearGradient`, once, at module scope.
 *
 * **This looks like the thing `MatchOverlay.tsx` forbids, and it is not.** The rule there
 * is that `className` must never go on an `Animated.View`, because Reanimated creates its
 * animated components lazily — registering them does not reliably catch the instance JSX
 * ends up using, and the class is then dropped in silence.
 *
 * `LinearGradient` has the opposite property: it is a plain module-scope component that
 * exists at import time, so css-interop can wrap it, and the registration is guaranteed to
 * be the same object every render. The distinction is lazy-vs-eager component creation,
 * not "animated vs not".
 *
 * `AnimatedGradient` below is the lazily-created one, and it takes `style` only.
 */
cssInterop(LinearGradient, { className: 'style' });

/** The animated gradient. `style` only — see above, and `src/match/MatchOverlay.tsx`. */
export const AnimatedGradient = Animated.createAnimatedComponent(LinearGradient);

type Direction = 'vertical' | 'horizontal' | 'diagonal';

/** `start`/`end` pairs. Kept as constants so the objects are stable across renders. */
const DIRECTIONS: Record<Direction, { start: { x: number; y: number }; end: { x: number; y: number } }> = {
  vertical: { start: { x: 0.5, y: 0 }, end: { x: 0.5, y: 1 } },
  horizontal: { start: { x: 0, y: 0.5 }, end: { x: 1, y: 0.5 } },
  diagonal: { start: { x: 0, y: 0 }, end: { x: 1, y: 1 } },
};

type GradientViewProps = Omit<ViewProps, 'children'> & {
  /** Which gradient from the theme. Resolved per scheme — see `src/theme/tokens.js`. */
  name?: GradientName;
  direction?: Direction;
  /** Explicit stops, for the rare case that is not a theme gradient (per-user avatars). */
  colors?: readonly [string, string, ...string[]];
  className?: string;
  children?: React.ReactNode;
};

/**
 * A gradient as a background.
 *
 * Takes `className` for its own box. Where a gradient needs to sit *behind* content, use
 * it absolutely-positioned and pass the content as siblings rather than children:
 *
 *   <View className="overflow-hidden rounded-full">
 *     <GradientView name="primary" className="absolute inset-0" pointerEvents="none" />
 *     <View className="px-6 py-3">{children}</View>
 *   </View>
 *
 * On Android, `overflow: hidden` clips the node's own elevation and shadow — so when the
 * element also carries a `glow()` or `lift()`, the clip and the depth must live on
 * *different* nodes. Outer node owns the depth, inner node owns the clip. See `Button`.
 */
export function GradientView({
  name = 'primary',
  direction = 'diagonal',
  colors,
  className,
  children,
  ...rest
}: GradientViewProps) {
  const themed = useGradient(name);
  const { start, end } = DIRECTIONS[direction];

  return (
    <LinearGradient
      colors={colors ?? themed}
      start={start}
      end={end}
      className={cn(className)}
      {...rest}
    >
      {children}
    </LinearGradient>
  );
}

/**
 * The two-node clip/depth split, as a component, because getting it wrong is silent and
 * Android-only. `style` carries the depth; the inner node carries the clip.
 */
export function GradientSurface({
  name = 'primary',
  direction = 'diagonal',
  colors,
  className,
  style,
  children,
  ...rest
}: GradientViewProps) {
  return (
    <View style={style} {...rest}>
      <View className={cn('overflow-hidden', className)}>
        <GradientView
          name={name}
          direction={direction}
          colors={colors}
          className="absolute inset-0"
          pointerEvents="none"
        />
        {children}
      </View>
    </View>
  );
}
