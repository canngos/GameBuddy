import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import type { Candidate } from '../api/types';
import { useThemeColors } from '../theme';
import { glow } from '../ui/glow';
import { commit as commitHaptic, tapLight } from '../ui/haptics';
import { Text } from '../ui/Text';
import { CandidateCard } from './CandidateCard';
import type { Decision } from './useDeck';

type SwipeCardProps = {
  candidate: Candidate;
  onDecide: (decision: Decision) => void;
  /** Opens the full profile. The card only ever shows a summary — see `CandidateCard`. */
  onOpenProfile?: () => void;
  /** Disables the gesture while a limit sheet or the match celebration is up. */
  frozen?: boolean;
};

/** Fraction of the screen width a card must cross to count as a decision. */
const COMMIT_RATIO = 0.28;
/** A fast flick commits even if it never reached the distance threshold. */
const COMMIT_VELOCITY = 800;
/** Degrees of tilt at the edge of the screen. */
const MAX_TILT = 12;

/**
 * One draggable card.
 *
 * Animated wrappers take `style`, never `className` — NativeWind cannot reliably
 * register Reanimated's lazily created components, so a class there is dropped in
 * silence and an `absolute inset-0` quietly becomes normal flow. Visual classes go on
 * the plain `View`s inside. See {@link MatchOverlay} for the longer version.
 */
export function SwipeCard({
  candidate,
  onDecide,
  onOpenProfile,
  frozen = false,
}: SwipeCardProps) {
  const { width } = useWindowDimensions();
  const colors = useThemeColors();
  const commitDistance = width * COMMIT_RATIO;

  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  /*
   * Whether the drag is currently past the commit threshold.
   *
   * Kept as a shared value rather than recomputed at release, because it is what fires the
   * *crossing* haptic — the tick that tells you the card will go if you let go now. That is
   * the whole point of haptics on a drag: the answer has to arrive while your thumb can
   * still change it, not after.
   */
  const armed = useSharedValue(false);

  const pan = Gesture.Pan()
    .enabled(!frozen)
    .onChange((event) => {
      translateX.value += event.changeX;
      translateY.value += event.changeY;

      // Fires once per crossing, in either direction, because it is edge-triggered off a
      // boolean rather than level-triggered off the distance. Buzzing on every frame past
      // the threshold would be a rattle, not feedback.
      const nowArmed = Math.abs(translateX.value) > commitDistance;
      if (nowArmed !== armed.value) {
        armed.value = nowArmed;
        if (nowArmed) scheduleOnRN(tapLight);
      }
    })
    .onEnd((event) => {
      const committed =
        Math.abs(translateX.value) > commitDistance ||
        Math.abs(event.velocityX) > COMMIT_VELOCITY;

      if (committed) {
        const direction = translateX.value > 0 ? 1 : -1;
        // Past the screen edge, so the card is fully gone before it unmounts.
        translateX.value = withTiming(direction * width * 1.5, { duration: 220 });
        translateY.value = withTiming(translateY.value + 40, { duration: 220 });
        // The weightier one, and only on a real decision — matching the Match button in
        // `DeckActions`, so a swipe and a tap that do the same thing feel the same.
        scheduleOnRN(commitHaptic);
        scheduleOnRN(onDecide, direction > 0 ? 'accept' : 'decline');
        return;
      }

      armed.value = false;
      // Not far enough: spring home. Damped enough to settle rather than wobble, which
      // on a card this large reads as jelly.
      translateX.value = withSpring(0, { damping: 18, stiffness: 180 });
      translateY.value = withSpring(0, { damping: 18, stiffness: 180 });
    });

  /**
   * Tap opens the full profile.
   *
   * `maxDistance` is what keeps this out of the swipe's way: a press that wanders more than
   * a few pixels is a drag and this fails, leaving the pan to it. Raced rather than composed
   * simultaneously, so exactly one of the two can win — a gesture that both opened a sheet
   * and threw the card is the worst outcome available here.
   */
  const tap = Gesture.Tap()
    .enabled(!frozen && !!onOpenProfile)
    .maxDistance(12)
    .onEnd((_event, success) => {
      if (success && onOpenProfile) scheduleOnRN(onOpenProfile);
    });

  const gesture = Gesture.Race(pan, tap);

  const cardStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      {
        rotate: `${interpolate(
          translateX.value,
          [-width, 0, width],
          [-MAX_TILT, 0, MAX_TILT],
          Extrapolation.CLAMP,
        )}deg`,
      },
    ],
  }));

  /*
   * The stamps fade in with the drag, so the pending decision is legible before release —
   * and now scale up to full size at exactly the commit threshold, so the stamp reaching
   * its full size *is* the "let go now" signal, in step with the haptic tick.
   */
  const acceptStampStyle = useAnimatedStyle(() => ({
    opacity: interpolate(translateX.value, [0, commitDistance], [0, 1], Extrapolation.CLAMP),
    transform: [
      { rotate: '-12deg' },
      {
        scale: interpolate(
          translateX.value,
          [0, commitDistance],
          [0.7, 1],
          Extrapolation.CLAMP,
        ),
      },
    ],
  }));
  const declineStampStyle = useAnimatedStyle(() => ({
    opacity: interpolate(translateX.value, [-commitDistance, 0], [1, 0], Extrapolation.CLAMP),
    transform: [
      { rotate: '12deg' },
      {
        scale: interpolate(
          translateX.value,
          [-commitDistance, 0],
          [1, 0.7],
          Extrapolation.CLAMP,
        ),
      },
    ],
  }));

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[StyleSheet.absoluteFill, cardStyle]}>
        <CandidateCard candidate={candidate} />

        {/* Fixed colours rather than theme tokens: these sit on the card's own colour
            block and must not follow `text-content` into black.

            The rotation moved into the animated style — a `transform` in the static
            StyleSheet and a `transform` in the animated one do not merge, the animated one
            replaces it wholesale, so declaring the tilt in both places would silently drop
            it the moment the scale was added. */}
        <Animated.View style={[styles.stamp, styles.stampLeft, acceptStampStyle]} pointerEvents="none">
          {/* The glow is what makes it read as neon rather than as a rubber stamp, and it
              goes on this inner plain View because `glow()` returns a style and the node
              carrying it must not be the one Reanimated is driving. */}
          <View
            className="rounded-xl border-4 border-accent px-4 py-1.5"
            style={glow('strong', colors.accent)}
          >
            <Text variant="numeral" className="text-[24px] leading-[30px] tracking-[2px] text-accent">
              MATCH
            </Text>
          </View>
        </Animated.View>

        <Animated.View style={[styles.stamp, styles.stampRight, declineStampStyle]} pointerEvents="none">
          <View
            className="rounded-xl border-4 border-white/90 px-4 py-1.5"
            style={glow('soft', '#FFFFFF')}
          >
            <Text variant="numeral" className="text-[24px] leading-[30px] tracking-[2px] text-white">
              PASS
            </Text>
          </View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

// No `transform` here — the tilt lives in the animated styles above, because an animated
// `transform` replaces a static one rather than merging with it.
const styles = StyleSheet.create({
  stamp: { position: 'absolute', top: 28 },
  stampLeft: { left: 24 },
  stampRight: { right: 24 },
});
