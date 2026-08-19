import { HandFist } from 'lucide-react-native';
import { useEffect } from 'react';
import { View } from 'react-native';
import Animated, {
  Easing,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { Icon } from '../ui/Icon';

/** How long the two fists take to travel in. Fast enough to read as a swing, not a drift. */
const APPROACH_MS = 360;

/**
 * Two fists coming together, played once when a match lands.
 *
 * The same mark as the Match button, which is the point: the gesture you made on the deck
 * is the gesture that answers you. A heart was never going to survive that translation —
 * two hearts approaching each other is a wedding, not a match with somebody to play with.
 *
 * **The impact is the beat everything else hangs off.** `onImpact` fires the moment the
 * fists meet, and the overlay uses it to throw the burst and the haptic then rather than on
 * mount. Light and a thump that arrive before the collision read as a glitch; arriving with
 * it, they read as the collision.
 *
 * Three of this project's documented traps meet in this file:
 * - `className` never goes on an `Animated.View` — NativeWind drops it silently. Styles all
 *   the way down here.
 * - An animated `transform` *replaces* a static one rather than merging, so the right fist's
 *   mirror lives on a plain `View` inside the animated wrapper rather than beside its
 *   `translateX`.
 * - The fists are absolutely positioned inside a fixed box, so the recoil cannot reflow the
 *   celebration around it.
 */
export function FistBump({
  size = 64,
  color = '#FFFFFF',
  onImpact,
}: {
  size?: number;
  color?: string;
  onImpact?: () => void;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withSequence(
      // In. Accelerating, so the two are fastest at the moment they meet.
      withTiming(1, { duration: APPROACH_MS, easing: Easing.in(Easing.quad) }, (finished) => {
        'worklet';
        if (finished && onImpact) scheduleOnRN(onImpact);
      }),
      // The recoil. Small and quick — this is knuckles meeting, not a car crash.
      withTiming(0.9, { duration: 90, easing: Easing.out(Easing.quad) }),
      withSpring(1, { damping: 9, stiffness: 220 }),
    );
    // Mount is the trigger: this only exists while a match is on screen, so there is never
    // a second play to guard against.
  }, [progress, onImpact]);

  /** How far out each fist starts, and how close they finish. */
  const travel = size * 1.6;
  /**
   * Half the gap between the two at rest.
   *
   * Turned on their sides the fists are about as wide as the icon box, so the value that
   * looked right while they were upright buried one inside the other and the pair read as a
   * single blob. They should touch, not merge — knuckles meeting is the whole picture.
   */
  const rest = size * 0.46;

  /*
   * Turned to face each other, which is what makes it a bump rather than two raised fists.
   *
   * Lucide draws the fist knuckles-up. A quarter turn puts the knuckles on the side that is
   * about to be hit: the left fist turns clockwise to +90, the right anticlockwise to -90.
   * Combined with the right one's mirror that makes them a left hand and a right hand
   * meeting, rather than the same hand printed twice.
   *
   * Each overshoots its resting angle by 18 degrees on the way in and squares up on contact,
   * so the turn finishes exactly when the knuckles land.
   */
  const left = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 0.25], [0, 1], 'clamp'),
    transform: [
      { translateX: interpolate(progress.value, [0, 1], [-travel, -rest]) },
      { rotate: `${interpolate(progress.value, [0, 1], [72, 90])}deg` },
    ],
  }));

  const right = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 0.25], [0, 1], 'clamp'),
    transform: [
      { translateX: interpolate(progress.value, [0, 1], [travel, rest]) },
      { rotate: `${interpolate(progress.value, [0, 1], [-72, -90])}deg` },
    ],
  }));

  return (
    <View
      style={{ width: size * 3, height: size * 1.4, alignItems: 'center', justifyContent: 'center' }}
      pointerEvents="none"
    >
      <Animated.View style={[{ position: 'absolute' }, left]}>
        <Icon as={HandFist} size={size} color={color} strokeWidth={2.5} />
      </Animated.View>

      <Animated.View style={[{ position: 'absolute' }, right]}>
        {/* Mirrored so the two face each other. On the plain child, never beside the
            animated `translateX` — see the note above. */}
        <View style={{ transform: [{ scaleX: -1 }] }}>
          <Icon as={HandFist} size={size} color={color} strokeWidth={2.5} />
        </View>
      </Animated.View>
    </View>
  );
}
