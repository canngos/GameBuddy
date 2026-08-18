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
import { useT } from '../i18n/useT';
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
/**
 * Fraction of the screen *height* an upward drag must cross to Super Like.
 *
 * Smaller than the horizontal ratio because the two are not the same journey: a phone is
 * much taller than it is wide, so the same fraction would ask for a drag roughly twice as
 * long — and this one is made with a thumb travelling towards the top of the screen, which
 * is the least comfortable direction available.
 */
const SUPER_COMMIT_RATIO = 0.16;
/** A fast flick commits even if it never reached the distance threshold. */
const COMMIT_VELOCITY = 800;
/** Degrees of tilt at the edge of the screen. */
const MAX_TILT = 12;

/**
 * Whether a drag is being read as upward rather than sideways.
 *
 * Nobody drags along an axis. Every swipe is a diagonal, so the question is not "is this
 * vertical" but "which of the two is this person doing", and the answer used everywhere is
 * whichever component is larger. Requiring upward travel to beat horizontal travel outright
 * means an ordinary accept — which drifts up or down by a few dozen pixels on the way
 * across — can never be mistaken for a Super Like, while a deliberate flick towards the top
 * of the screen is one from the first few pixels.
 *
 * A worklet: this runs on the UI thread inside the gesture handlers.
 */
function isSuper(x: number, y: number): boolean {
  'worklet';
  return -y > Math.abs(x);
}

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
  const { width, height } = useWindowDimensions();
  const colors = useThemeColors();
  const t = useT();
  const commitDistance = width * COMMIT_RATIO;
  const superCommitDistance = height * SUPER_COMMIT_RATIO;

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
      //
      // One flag for both axes: only one of the three decisions can be armed at a time, so
      // dragging up out of an armed sideways swipe re-ticks, which is the correct answer —
      // what letting go does has just changed.
      const nowArmed = isSuper(translateX.value, translateY.value)
        ? -translateY.value > superCommitDistance
        : Math.abs(translateX.value) > commitDistance;
      if (nowArmed !== armed.value) {
        armed.value = nowArmed;
        if (nowArmed) scheduleOnRN(tapLight);
      }
    })
    .onEnd((event) => {
      // Up beats sideways when the drag is mostly upward — see `isSuper`. Checked first
      // because a Super Like is an accept as well, so a diagonal that qualified as both
      // must not be resolved as the cheaper one.
      const superCommitted =
        isSuper(translateX.value, translateY.value) &&
        (-translateY.value > superCommitDistance || -event.velocityY > COMMIT_VELOCITY);

      if (superCommitted) {
        // Straight up and out of the frame. No tilt: the rotation is driven by horizontal
        // travel, which is near zero here, so the card leaves square — which is what makes
        // this read as a different act rather than a crooked accept.
        translateY.value = withTiming(-height * 1.2, { duration: 240 });
        scheduleOnRN(commitHaptic);
        scheduleOnRN(onDecide, 'super');
        return;
      }

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
  /*
   * Both horizontal stamps drop to zero while the drag reads as upward, so a diagonal never
   * shows MATCH and SUPER arguing about what letting go will do.
   */
  const acceptStampStyle = useAnimatedStyle(() => ({
    opacity: isSuper(translateX.value, translateY.value)
      ? 0
      : interpolate(translateX.value, [0, commitDistance], [0, 1], Extrapolation.CLAMP),
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
    opacity: isSuper(translateX.value, translateY.value)
      ? 0
      : interpolate(translateX.value, [-commitDistance, 0], [1, 0], Extrapolation.CLAMP),
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

  // Centred and upright rather than tilted into a corner like the other two, because it is
  // the one decision made along the card's own axis — and because at the point it appears
  // the thumb is over the middle of the card, not either edge.
  const superStampStyle = useAnimatedStyle(() => ({
    opacity: isSuper(translateX.value, translateY.value)
      ? interpolate(-translateY.value, [0, superCommitDistance], [0, 1], Extrapolation.CLAMP)
      : 0,
    transform: [
      {
        scale: interpolate(
          -translateY.value,
          [0, superCommitDistance],
          [0.7, 1],
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

        <Animated.View style={[styles.superStamp, superStampStyle]} pointerEvents="none">
          <View
            className="rounded-xl border-4 border-gold px-4 py-1.5"
            style={glow('strong', colors.gold)}
          >
            <Text variant="numeral" className="text-[24px] leading-[30px] tracking-[2px] text-gold">
              {t.deck.card.superStamp}
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
  // Centred horizontally by pinning both edges and letting the row centre its content,
  // which needs no measurement of the stamp itself.
  superStamp: { position: 'absolute', top: 28, left: 0, right: 0, alignItems: 'center' },
});
