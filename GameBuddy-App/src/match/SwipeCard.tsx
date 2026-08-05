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
import { Text } from '../ui/Text';
import { CandidateCard } from './CandidateCard';
import type { Decision } from './useDeck';

type SwipeCardProps = {
  candidate: Candidate;
  onDecide: (decision: Decision) => void;
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
export function SwipeCard({ candidate, onDecide, frozen = false }: SwipeCardProps) {
  const { width } = useWindowDimensions();
  const commitDistance = width * COMMIT_RATIO;

  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);

  const pan = Gesture.Pan()
    .enabled(!frozen)
    .onChange((event) => {
      translateX.value += event.changeX;
      translateY.value += event.changeY;
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
        scheduleOnRN(onDecide, direction > 0 ? 'accept' : 'decline');
        return;
      }
      // Not far enough: spring home. Damped enough to settle rather than wobble, which
      // on a card this large reads as jelly.
      translateX.value = withSpring(0, { damping: 18, stiffness: 180 });
      translateY.value = withSpring(0, { damping: 18, stiffness: 180 });
    });

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

  // The stamps fade in with the drag, so the pending decision is legible before release.
  const acceptStampStyle = useAnimatedStyle(() => ({
    opacity: interpolate(translateX.value, [0, commitDistance], [0, 1], Extrapolation.CLAMP),
  }));
  const declineStampStyle = useAnimatedStyle(() => ({
    opacity: interpolate(translateX.value, [-commitDistance, 0], [1, 0], Extrapolation.CLAMP),
  }));

  return (
    <GestureDetector gesture={pan}>
      <Animated.View style={[StyleSheet.absoluteFill, cardStyle]}>
        <CandidateCard candidate={candidate} />

        {/* Fixed colours rather than theme tokens: these sit on the card's own colour
            block and must not follow `text-content` into black. */}
        <Animated.View
          style={[styles.stamp, styles.stampLeft, acceptStampStyle]}
          pointerEvents="none"
        >
          <View className="rounded-xl border-4 border-brand px-4 py-1.5">
            <Text className="font-bold text-[24px] tracking-[2px] text-brand">MATCH</Text>
          </View>
        </Animated.View>

        <Animated.View
          style={[styles.stamp, styles.stampRight, declineStampStyle]}
          pointerEvents="none"
        >
          <View className="rounded-xl border-4 border-white/90 px-4 py-1.5">
            <Text className="font-bold text-[24px] tracking-[2px] text-white">PASS</Text>
          </View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  stamp: { position: 'absolute', top: 28 },
  stampLeft: { left: 24, transform: [{ rotate: '-12deg' }] },
  stampRight: { right: 24, transform: [{ rotate: '12deg' }] },
});
