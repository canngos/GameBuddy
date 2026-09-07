import { Hand } from 'lucide-react-native';
import { useEffect } from 'react';
import { Pressable, View } from 'react-native';
import Animated, {
  Easing,
  Extrapolation,
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useT } from '../i18n/useT';
import { Icon, Text } from '../ui';

/**
 * The one thing a tour cannot teach: which way to swipe.
 *
 * The tab walkthrough describes the deck from a card over a dimmed screen, so the deck is
 * not on screen while it is being explained — and a gesture read about three screens
 * earlier is a gesture nobody remembers. This runs on the real first card instead.
 *
 * **It does not block the deck.** `pointerEvents="box-none"` on the wrapper, so a real
 * swipe goes straight through to `SwipeCard` underneath; only the "Got it" pill is
 * touchable. Somebody who already knows how to swipe simply swipes, and the first decision
 * puts this away — see `onDecide` in `app/(main)/home.tsx`.
 *
 * One shared value drives everything. The hand travels right, back, left, back, up, back,
 * and each caption's opacity is interpolated from the same progress, so the words can never
 * describe a direction the hand is not moving in.
 */

/** The full cycle, in the order the captions read. */
const LEG_MS = 750;

export function SwipeDemo({ onDismiss }: { onDismiss: () => void }) {
  const t = useT();
  const reduceMotion = useReducedMotion();
  const progress = useSharedValue(0);

  useEffect(() => {
    if (reduceMotion) return;
    // 0→1 right, 1→2 back, 2→3 left, 3→4 back, 4→5 up, 5→6 back.
    progress.value = withRepeat(
      withSequence(
        // Rewind before replaying. A repeated sequence picks up from wherever the value
        // already sits -- 6, at the end of a lap -- so without this the next lap animates
        // 6→1 and walks the whole demonstration backwards. The hand rests dead centre at
        // both 6 and 0, so the snap itself is invisible.
        withTiming(0, { duration: 0 }),
        ...[1, 2, 3, 4, 5, 6].map((to) =>
          withTiming(to, { duration: LEG_MS, easing: Easing.inOut(Easing.quad) }),
        ),
      ),
      -1,
      false,
    );
  }, [progress, reduceMotion]);

  // Every one of these MUST clamp. Each axis describes only the legs it owns -- X says
  // nothing about 4→6, Y says nothing about 0→4 -- and `interpolate` extends the end slope
  // by default rather than holding the end value. Unclamped, X kept climbing through the
  // "up" leg (ending 108px right of centre) and Y read 216px *below* centre at the start of
  // the cycle, so the hand entered from off-stage and drifted diagonally the whole way: it
  // never once moved purely right, purely left, or purely up. Clamping is what makes each
  // axis rest at zero while the other is doing the talking.
  const handStyle = useAnimatedStyle(() => ({
    transform: [
      {
        translateX: interpolate(
          progress.value,
          [0, 1, 2, 3, 4],
          [0, 54, 0, -54, 0],
          Extrapolation.CLAMP,
        ),
      },
      {
        translateY: interpolate(progress.value, [4, 5, 6], [0, -54, 0], Extrapolation.CLAMP),
      },
    ],
  }));

  const rightStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 0.6, 1.6, 2], [0, 1, 1, 0], Extrapolation.CLAMP),
  }));
  const leftStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [2, 2.6, 3.6, 4], [0, 1, 1, 0], Extrapolation.CLAMP),
  }));
  const upStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [4, 4.6, 5.6, 6], [0, 1, 1, 0], Extrapolation.CLAMP),
  }));

  return (
    <View
      className="absolute inset-0 items-center justify-center"
      pointerEvents="box-none"
    >
      {/* Dim enough to read against a photograph, light enough that the card underneath is
          plainly still there — this explains the card, it does not replace it. */}
      <View className="absolute inset-0 rounded-card bg-black/55" pointerEvents="none" />

      <View className="items-center gap-5" pointerEvents="box-none">
        {reduceMotion ? (
          // Somebody who has asked the system for less motion gets the same three facts
          // without the hand: a moving demonstration is exactly what they turned off.
          <View className="gap-1.5">
            <Caption text={t.hints.swipe.right} />
            <Caption text={t.hints.swipe.left} />
            <Caption text={t.hints.swipe.up} />
          </View>
        ) : (
          <>
            {/* `style` only on an Animated.View — NativeWind drops `className` there in
                silence (UI_NOTE §4.3). The visual classes live on the plain View inside. */}
            <Animated.View style={handStyle}>
              <View className="h-14 w-14 items-center justify-center rounded-full bg-white/15">
                <Icon as={Hand} size={26} tone="inverse" />
              </View>
            </Animated.View>

            {/* All three occupy the same box, so the block does not jump as they swap. */}
            <View className="h-6 justify-center">
              <Animated.View style={[{ position: 'absolute', alignSelf: 'center' }, rightStyle]}>
                <Caption text={t.hints.swipe.right} />
              </Animated.View>
              <Animated.View style={[{ position: 'absolute', alignSelf: 'center' }, leftStyle]}>
                <Caption text={t.hints.swipe.left} />
              </Animated.View>
              <Animated.View style={[{ position: 'absolute', alignSelf: 'center' }, upStyle]}>
                <Caption text={t.hints.swipe.up} />
              </Animated.View>
            </View>
          </>
        )}

        <Pressable
          onPress={onDismiss}
          accessibilityRole="button"
          hitSlop={12}
          className="rounded-full bg-white/20 px-5 py-2"
        >
          <Text variant="button" className="text-white">
            {t.hints.gotIt}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

function Caption({ text }: { text: string }) {
  return (
    <Text variant="bodyStrong" className="text-center text-white">
      {text}
    </Text>
  );
}
