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
import { GradientView } from '../ui/Gradient';
import { glow } from '../ui/glow';
import { commit as commitHaptic, tapLight } from '../ui/haptics';
import { Text } from '../ui/Text';
import { ThemedCardFrame } from '../ui/ThemedCardFrame';
import { CandidateCard } from './CandidateCard';
import { useDeckLayout } from './deckLayout';
import type { Decision } from './useDeck';

/** `rounded-card` in dp — the frame has to match it to stay concentric. */
const CARD_RADIUS = 24;

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
  const layout = useDeckLayout();
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
  /*
   * Which decision the release committed: 0 none, 1 accept, -1 decline, 2 super.
   *
   * A fast flick commits on velocity alone (see onEnd), often at 60-90px of travel -
   * where the drag-driven stamp is still at a quarter opacity. The card then exits in
   * 220ms, so on a real thumb-flick the stamp was gone before it ever became legible;
   * that is the "stamps never appear" report from devices, which a slow mouse-drag on an
   * emulator can never reproduce. `commitFlash` ramps to 1 the moment the release
   * commits, and each stamp shows whichever is stronger: the drag or the flash. No reset
   * needed - the card is keyed per candidate, so every card starts with fresh values.
   * Named `decided` because onEnd already has a local boolean called `committed`.
   */
  const decided = useSharedValue(0);
  const commitFlash = useSharedValue(0);

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
        decided.value = 2;
        commitFlash.value = withTiming(1, { duration: 90 });
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
        decided.value = direction;
        commitFlash.value = withTiming(1, { duration: 90 });
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

  /*
   * The card itself grows very slightly on the way up, which is most of why a Super Like
   * reads as a bigger act than a like. A stamp in a corner asks somebody to look away from
   * the thing they are dragging; scale is felt without being looked at.
   *
   * Deliberately small — 4% — because this is a 300dp card and anything more reads as a
   * bug rather than as emphasis.
   */
  const cardStyle = useAnimatedStyle(() => {
    const lifting = isSuper(translateX.value, translateY.value)
      ? interpolate(-translateY.value, [0, superCommitDistance], [0, 1], Extrapolation.CLAMP)
      : 0;

    return {
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
        { scale: 1 + lifting * 0.04 },
      ],
    };
  });

  /*
   * The gold that washes over the card as it is lifted.
   *
   * This is the answer to the tester's complaint that a "SUPER" tag was not exciting: while
   * you drag upward the whole card turns gold and lights up, so the state change is wherever
   * you happen to be looking. Opacity only — the glow underneath is a static style on a
   * plain child, because a node Reanimated drives must not also carry `glow()`.
   */
  const superWashStyle = useAnimatedStyle(() => ({
    opacity: isSuper(translateX.value, translateY.value)
      ? interpolate(-translateY.value, [0, superCommitDistance], [0, 1], Extrapolation.CLAMP)
      : 0,
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
  const acceptStampStyle = useAnimatedStyle(() => {
    const drag = isSuper(translateX.value, translateY.value)
      ? 0
      : interpolate(translateX.value, [0, commitDistance], [0, 1], Extrapolation.CLAMP);
    // Whichever is stronger: how far the thumb has dragged, or the release flash. The
    // flash is what makes a velocity-committed flick show its stamp during the exit
    // flight instead of leaving at a quarter opacity.
    const presence = Math.max(drag, decided.value === 1 ? commitFlash.value : 0);
    return {
      opacity: presence,
      transform: [
        { rotate: '-12deg' },
        { scale: 0.7 + 0.3 * presence },
      ],
    };
  });
  const declineStampStyle = useAnimatedStyle(() => {
    const drag = isSuper(translateX.value, translateY.value)
      ? 0
      : interpolate(translateX.value, [-commitDistance, 0], [1, 0], Extrapolation.CLAMP);
    const presence = Math.max(drag, decided.value === -1 ? commitFlash.value : 0);
    return {
      opacity: presence,
      transform: [
        { rotate: '12deg' },
        { scale: 0.7 + 0.3 * presence },
      ],
    };
  });

  // Centred and upright rather than tilted into a corner like the other two, because it is
  // the one decision made along the card's own axis — and because at the point it appears
  // the thumb is over the middle of the card, not either edge.
  const superStampStyle = useAnimatedStyle(() => {
    const drag = isSuper(translateX.value, translateY.value)
      ? interpolate(-translateY.value, [0, superCommitDistance], [0, 1], Extrapolation.CLAMP)
      : 0;
    const presence = Math.max(drag, decided.value === 2 ? commitFlash.value : 0);
    return {
      opacity: presence,
      transform: [{ scale: 0.7 + 0.3 * presence }],
    };
  });

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[StyleSheet.absoluteFill, cardStyle]}>
        {/* Outside `CandidateCard`, not inside it: the card's own root is
            `overflow-hidden` (it clips the identity gradient to the rounded corners), and
            this frame is drawn *past* the card's bounds, so mounted in there it would be
            clipped away to nothing. See `ThemedCardFrame`. */}
        <ThemedCardFrame theme={candidate.theme} radius={CARD_RADIUS} />
        <CandidateCard candidate={candidate} />

        {/* Over the card and under the stamps. `pointerEvents` off so it never competes
            with the tap that opens the profile. */}
        <Animated.View
          style={[StyleSheet.absoluteFill, styles.wash, superWashStyle]}
          pointerEvents="none"
        >
          <View
            className="flex-1 rounded-card border-2 border-gold"
            style={glow('strong', colors.gold)}
          >
            <GradientView
              colors={['rgba(255,196,0,0.30)', 'rgba(255,138,0,0.12)']}
              direction="vertical"
              className="absolute inset-0 rounded-card"
              pointerEvents="none"
            />
          </View>
        </Animated.View>

        {/* Fixed colours rather than theme tokens: these sit on the card's own colour
            block and must not follow `text-content` into black.

            The rotation moved into the animated style — a `transform` in the static
            StyleSheet and a `transform` in the animated one do not merge, the animated one
            replaces it wholesale, so declaring the tilt in both places would silently drop
            it the moment the scale was added. */}
        <Animated.View
          style={[styles.stamp, styles.stampLeft, { top: layout.stampTop }, acceptStampStyle]}
          pointerEvents="none"
        >
          {/* The glow is what makes it read as neon rather than as a rubber stamp, and it
              goes on this inner plain View because `glow()` returns a style and the node
              carrying it must not be the one Reanimated is driving. */}
          <View
            className="rounded-xl border-4 border-accent px-4 py-1.5"
            style={glow('strong', colors.accent)}
          >
            {/* Type is an inline style from the deck's tier table, not a class - the value
                changes with the device and a class cannot. The multiplier cap keeps large
                system text from overrunning the fixed line box, which trimmed the glyphs
                to nothing inside the border. */}
            <Text
              variant="numeral"
              className="tracking-[2px] text-accent"
              style={layout.stampType}
              maxFontSizeMultiplier={1.2}
            >
              MATCH
            </Text>
          </View>
        </Animated.View>

        <Animated.View
          style={[styles.stamp, styles.stampRight, { top: layout.stampTop }, declineStampStyle]}
          pointerEvents="none"
        >
          <View
            className="rounded-xl border-4 border-white/90 px-4 py-1.5"
            style={glow('soft', '#FFFFFF')}
          >
            <Text
              variant="numeral"
              className="tracking-[2px] text-white"
              style={layout.stampType}
              maxFontSizeMultiplier={1.2}
            >
              PASS
            </Text>
          </View>
        </Animated.View>

        <Animated.View
          style={[styles.superStamp, { top: layout.stampTop }, superStampStyle]}
          pointerEvents="none"
        >
          <View
            className="rounded-xl border-4 border-gold px-4 py-1.5"
            style={glow('strong', colors.gold)}
          >
            <Text
              variant="numeral"
              className="tracking-[2px] text-gold"
              style={layout.stampType}
              maxFontSizeMultiplier={1.2}
            >
              {t.deck.card.superStamp}
            </Text>
          </View>
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

// No `transform` here — the tilt lives in the animated styles above, because an animated
// `transform` replaces a static one rather than merging with it. `top` is tiered and
// lives in the deck layout table, applied inline at the call sites.
//
// The zIndex/elevation pairs are load-bearing on Android: `CandidateCard` carries
// `lift('lg')`, which is `elevation: 10` there, and Android orders *siblings* by Z before
// it considers JSX order — so an overlay with no elevation of its own can paint underneath
// the very card it is stamped on. iOS and web order by JSX and ignore the extra keys.
// The wash sits at 11 (over the card), the stamps at 12 (over the wash). Full-screen
// overlays get the same treatment through `overlay()` in `src/ui/elevation.ts`.
const styles = StyleSheet.create({
  wash: { zIndex: 11, elevation: 11 },
  stamp: { position: 'absolute', zIndex: 12, elevation: 12 },
  stampLeft: { left: 24 },
  stampRight: { right: 24 },
  // Centred horizontally by pinning both edges and letting the row centre its content,
  // which needs no measurement of the stamp itself.
  superStamp: { position: 'absolute', left: 0, right: 0, alignItems: 'center', zIndex: 12, elevation: 12 },
});
