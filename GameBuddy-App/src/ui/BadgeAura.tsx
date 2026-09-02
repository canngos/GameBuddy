import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useThemeColors } from '../theme';
import { AnimatedGradient } from './Gradient';

type BadgeAuraProps = {
  /** The badge plate's width and height in dp. The aura is a square ring around it. */
  size: number;
  /**
   * False on every badge below the hard tier, and on a locked one.
   *
   * <p>Passed rather than checked inside, because whether a badge is *earned* is the
   * caller's business — a prismatic badge nobody has yet is still just a dimmed picture,
   * and animating one somebody has not earned would advertise it as theirs.
   */
  active: boolean;
};

/**
 * The turning light behind a hard badge.
 *
 * <p>Drawn by the app rather than baked into the artwork, and that is the whole point:
 * seven badges share one component and cost nothing to download, where seven animated WebPs
 * would be up to 370KB each, decoded three-across in a scrolling grid — and
 * `app/(main)/market.tsx` already calls animated WebP "the most expensive thing to decode
 * in the app". Two badges do get moving art as well; this sits over those too.
 *
 * <p><strong>A rotating gradient, not a highlight running the perimeter.</strong> The same
 * choice {@link ThemedCardFrame} made and for the same reason: a travelling highlight means
 * tracking four sides and two corners in one continuous parameter, and rotating a fill
 * under a fixed window reads identically without being able to desynchronise at a corner.
 * The square is sized by the diagonal so no bare wedge sweeps through the corners.
 *
 * <p><strong>Two ways it stops.</strong> `useReducedMotion`, because something that never
 * stops moving is precisely what that setting exists for. And `useFocusEffect`, because the
 * badges screen stays mounted behind the tab bar: `freezeOnBlur` freezes React renders but
 * not Reanimated worklets, so without this every prismatic badge would keep a UI-thread
 * animation ticking on a screen nobody is looking at. `LobbyCard`'s boosted frame learned
 * that one the hard way.
 */
export function BadgeAura({ size, active }: BadgeAuraProps) {
  const colors = useThemeColors();
  const reduceMotion = useReducedMotion();
  const spin = useSharedValue(0);

  const moving = active && !reduceMotion;

  useFocusEffect(
    useCallback(() => {
      if (!moving) {
        cancelAnimation(spin);
        spin.value = 0;
        return;
      }
      // Slow. This sits in a grid of up to twenty-five tiles and something turning quickly
      // in a list reads as a warning rather than as a prize.
      spin.value = withRepeat(withTiming(1, { duration: 5200, easing: Easing.linear }), -1, false);
      return () => cancelAnimation(spin);
    }, [moving, spin]),
  );

  const spinStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${spin.value * 360}deg` }],
  }));

  if (!active) return null;

  // The diagonal of the square the aura covers, so every angle still reaches the corners.
  const side = Math.ceil(size * Math.SQRT2);
  // Tight to the plate. At -8% the disc stood well clear of the hexagon's flats and read
  // as a coloured sticker behind it rather than as light coming off it; -4% leaves the
  // points glowing and the flats fringed, which is what a halo looks like.
  const inset = -Math.round(size * 0.04);

  return (
    <View
      pointerEvents="none"
      style={{
        position: 'absolute',
        top: inset,
        left: inset,
        right: inset,
        bottom: inset,
        // A radius, not a square: on Android a glow follows the node's box, so a square
        // wrapper would put a square halo behind a hexagonal plate (UI_NOTE §4, trap 4).
        borderRadius: size,
        overflow: 'hidden',
        opacity: 0.42,
      }}
    >
      {/* Static when motion is reduced: the badge still looks different from a silver one,
          it just does not move. Turning the aura off entirely would take the tier's whole
          visual signal away from the people who asked for less animation, not less
          information. */}
      <Animated.View
        style={[
          {
            position: 'absolute',
            width: side,
            height: side,
            left: (size - side) / 2 - inset,
            top: (size - side) / 2 - inset,
          },
          spinStyle,
        ]}
      >
        {/* The raw animated LinearGradient takes start/end rather than a direction — see
            `Gradient.tsx`, where only the plain `GradientView` wraps them. */}
        <AnimatedGradient
          colors={[colors.gold, colors.accent, colors.primary, colors.gold]}
          start={DIAGONAL.start}
          end={DIAGONAL.end}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>
    </View>
  );
}

/** The same pair `GradientView`'s `diagonal` uses; the animated node needs them directly. */
const DIAGONAL = { start: { x: 0, y: 0 }, end: { x: 1, y: 1 } } as const;
