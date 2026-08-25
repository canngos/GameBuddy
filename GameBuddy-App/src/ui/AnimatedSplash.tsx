import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  runOnJS,
  useAnimatedProps,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Defs, LinearGradient, Path, Stop } from 'react-native-svg';

/**
 * The animated half of the splash — the "stroke draw-on".
 *
 * The native splash (configured in app.json) is a static frame the OS paints before any
 * JavaScript runs; it cannot animate. This overlay mounts the moment the app is ready,
 * on top of the already-rendered shell, and plays the brand's opening move: the mark's
 * outline draws itself in, the two buddies appear, the gradient floods the tile, the
 * wordmark settles, and the whole thing lifts to reveal the app — about two seconds,
 * once per cold start.
 *
 * It always runs on the dark brand canvas (`#0B0B12`) regardless of theme, matching the
 * native splash behind it, so the sequence native-splash → this → app has no colour jump.
 *
 * The drawing is the same Duo-Grip as GameBuddy-Web/public/favicon.svg, in the same
 * 64-unit box — change them together. The dash lengths below are the measured
 * `getTotalLength()` of each path; the draw-on works by animating `strokeDashoffset`
 * from that length (fully hidden) to zero (fully drawn).
 *
 * Not Lottie, for the reason documented in `Burst.tsx`. Reanimated moves everything else
 * in the app and cannot fail silently into an empty panel.
 */

const AnimatedPath = Animated.createAnimatedComponent(Path);
const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** The favicon's rounded-square tile, as a path so its outline can be dash-animated. */
const TILE_OUTLINE = 'M16 4H48A12 12 0 0 1 60 16V48A12 12 0 0 1 48 60H16A12 12 0 0 1 4 48V16A12 12 0 0 1 16 4Z';
const TILE_LENGTH = 203.41;

const CONTROLLER =
  'M20 25h24a10 10 0 0 1 9.6 12.8l-2.4 8A6 6 0 0 1 40 47l-3-4H27l-3 4a6 6 0 0 1-10.8-1.2l-2.4-8A10 10 0 0 1 20 25Z';
const CONTROLLER_LENGTH = 125.63;

const MARK_SIZE = 96;

type AnimatedSplashProps = {
  /** Fired once the overlay has fully faded; the caller unmounts it. */
  onDone: () => void;
};

export function AnimatedSplash({ onDone }: AnimatedSplashProps) {
  // One shared value per stage rather than one master progress: the stages overlap
  // (the controller starts drawing while the tile outline is still finishing), and
  // `withDelay` timelines read better than a pile of interpolate ranges off one clock.
  const tileDash = useSharedValue(TILE_LENGTH);
  const controllerDash = useSharedValue(CONTROLLER_LENGTH);
  const headsOpacity = useSharedValue(0);
  const fillOpacity = useSharedValue(0);
  const sketchOpacity = useSharedValue(1);
  const wordProgress = useSharedValue(0);
  const overlayOpacity = useSharedValue(1);

  useEffect(() => {
    const out = Easing.out(Easing.cubic);
    tileDash.value = withTiming(0, { duration: 520, easing: out });
    controllerDash.value = withDelay(180, withTiming(0, { duration: 560, easing: out }));
    headsOpacity.value = withDelay(620, withTiming(1, { duration: 240 }));
    fillOpacity.value = withDelay(840, withTiming(1, { duration: 320 }));
    sketchOpacity.value = withDelay(900, withTiming(0, { duration: 240 }));
    wordProgress.value = withDelay(1020, withTiming(1, { duration: 300, easing: out }));
    /*
     * The last visual event is the wordmark, which finishes at 1320 (1020 + 300). The
     * fade used to start at 1700, so 380ms of the launch was a completely still frame —
     * nothing drawing, nothing fading, the shell already mounted underneath and unusable
     * because this overlay is opaque and covers it.
     *
     * 1400 keeps a beat after the wordmark lands and gives that dead time back. Do not
     * push it below ~1350: the fade would then start while the wordmark is still moving,
     * which cuts the brand moment rather than the pause after it.
     */
    overlayOpacity.value = withDelay(
      1400,
      withTiming(0, { duration: 280 }, () => {
        // Called on cancellation too: an interrupted fade must still hand over,
        // or the opaque overlay sits over a mounted, interactive app forever.
        runOnJS(onDone)();
      }),
    );
    // Safety net for the path where the timing callback never runs at all
    // (backgrounded at the wrong moment, a UI-thread hiccup). Cleared on unmount,
    // which is what a normal hand-over does; firing twice is harmless either way.
    const fallback = setTimeout(onDone, 2300);
    return () => clearTimeout(fallback);
    // The timeline plays exactly once, on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const tileProps = useAnimatedProps(() => ({ strokeDashoffset: tileDash.value }));
  const controllerProps = useAnimatedProps(() => ({ strokeDashoffset: controllerDash.value }));
  const headProps = useAnimatedProps(() => ({ opacity: headsOpacity.value }));

  const sketchStyle = useAnimatedStyle(() => ({ opacity: sketchOpacity.value }));
  const fillStyle = useAnimatedStyle(() => ({ opacity: fillOpacity.value }));
  const wordStyle = useAnimatedStyle(() => ({
    opacity: wordProgress.value,
    transform: [{ translateY: (1 - wordProgress.value) * 10 }],
  }));
  const overlayStyle = useAnimatedStyle(() => ({ opacity: overlayOpacity.value }));

  return (
    <Animated.View style={[styles.overlay, overlayStyle]}>
      <View style={styles.mark}>
        {/* The sketch: white outlines drawing themselves in. */}
        <Animated.View style={[StyleSheet.absoluteFill, sketchStyle]}>
          <Svg width={MARK_SIZE} height={MARK_SIZE} viewBox="0 0 64 64">
            <AnimatedPath
              d={TILE_OUTLINE}
              stroke="#ECECF5"
              strokeWidth={2.6}
              fill="none"
              strokeLinecap="round"
              strokeDasharray={TILE_LENGTH}
              animatedProps={tileProps}
            />
            <AnimatedPath
              d={CONTROLLER}
              stroke="#ECECF5"
              strokeWidth={2.6}
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray={CONTROLLER_LENGTH}
              animatedProps={controllerProps}
            />
            <AnimatedCircle cx={20} cy={17.5} r={5} stroke="#ECECF5" strokeWidth={2.6} fill="none" animatedProps={headProps} />
            <AnimatedCircle cx={44} cy={17.5} r={5} stroke="#ECECF5" strokeWidth={2.6} fill="none" animatedProps={headProps} />
          </Svg>
        </Animated.View>

        {/* The finished mark: the gradient floods in over the sketch. */}
        <Animated.View style={[StyleSheet.absoluteFill, fillStyle]}>
          <Svg width={MARK_SIZE} height={MARK_SIZE} viewBox="0 0 64 64">
            <Defs>
              <LinearGradient id="gbSplash" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="64" y2="64">
                <Stop offset="0" stopColor="#7C4DFF" />
                <Stop offset="1" stopColor="#00E5FF" />
              </LinearGradient>
            </Defs>
            <Path d="M14 0H50A14 14 0 0 1 64 14V50A14 14 0 0 1 50 64H14A14 14 0 0 1 0 50V14A14 14 0 0 1 14 0Z" fill="url(#gbSplash)" />
            <Circle cx={20} cy={17.5} r={5} fill="#FFFFFF" />
            <Circle cx={44} cy={17.5} r={5} fill="#FFFFFF" />
            <Path d={CONTROLLER} fill="#FFFFFF" />
            <Path d="M21 32.5v7M17.5 36h7" fill="none" stroke="url(#gbSplash)" strokeWidth={2.8} strokeLinecap="round" />
            <Circle cx={43} cy={34} r={1.9} fill="url(#gbSplash)" />
            <Circle cx={47} cy={38} r={1.9} fill="url(#gbSplash)" />
          </Svg>
        </Animated.View>
      </View>

      <Animated.Text style={[styles.word, wordStyle]}>GameBuddy</Animated.Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    // The brand canvas, hard-coded on purpose: this is the one surface that stays dark
    // in both themes, because the native splash behind it does too.
    backgroundColor: '#0B0B12',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
  },
  mark: {
    width: MARK_SIZE,
    height: MARK_SIZE,
    marginBottom: 20,
  },
  word: {
    fontFamily: 'ChakraPetch_700Bold',
    fontSize: 20,
    letterSpacing: 0.5,
    color: '#ECECF5',
  },
});
