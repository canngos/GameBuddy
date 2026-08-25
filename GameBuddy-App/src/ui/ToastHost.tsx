import { useEffect, useRef } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { lift } from './elevation';
import { useHairline } from './hairline';
import { Icon } from './Icon';
import { Text } from './Text';
import { useToasts, type Toast } from './toast';

/**
 * The in-app notification banner, in the app's voice rather than Android's.
 *
 * Before this, a notification arriving while the app was open was drawn by the operating
 * system: a grey system banner, with the OS's typography, over an app that was already
 * looking at the thing being announced. UI_NOTE §5.2 is the complaint; this is the answer.
 * `usePushRegistration` now suppresses the system banner for every kind this app knows how
 * to draw, so the two cannot both appear.
 *
 * Mounted once in `app/(main)/_layout.tsx`, immediately **before** `MatchCelebration` so a
 * match overlay still draws over a toast. A match is the rarest and biggest event in the
 * product and keeps the full screen; badges, messages, friend requests and community
 * activity are all toast-sized.
 *
 * Two conventions inherited from `MatchOverlay`, both learned the hard way:
 *
 * - **No `Modal`.** An absolutely positioned sibling behaves identically on both platforms
 *   and does not bring `Modal`'s stacking and sizing problems on web.
 * - **`className` never on an `Animated.View`.** Reanimated creates its animated components
 *   lazily, so NativeWind drops the class in silence — which reads as a layout bug, not a
 *   styling one. Animated wrappers here carry `style` only; every visual class is on a plain
 *   `View` inside them.
 */

/** How long a toast sits before it leaves on its own. */
const LIFETIME_MS = 4200;
/** Long enough to be seen leaving, short enough that the next one is not kept waiting. */
const EXIT_MS = 180;
/** Past this much upward drag, a release dismisses instead of springing back. */
const SWIPE_THRESHOLD = 28;

export function ToastHost() {
  const queue = useToasts((state) => state.queue);
  const dismiss = useToasts((state) => state.dismiss);

  const head = queue[0];

  // Mounted conditionally rather than hidden with a class. Swapping a class *key* set
  // between renders is what stops NativeWind 4.2.6 painting a subtree — see
  // `src/ui/hairline.ts`. Keying on the id also gives each toast a fresh mount, which is
  // what restarts the entry animation without any imperative replay.
  if (!head) return null;

  return <ToastCard key={head.id} toast={head} onDone={() => dismiss(head.id)} />;
}

function ToastCard({ toast, onDone }: { toast: Toast; onDone: () => void }) {
  const insets = useSafeAreaInsets();
  const hairline = useHairline();

  const progress = useSharedValue(0);
  const drag = useSharedValue(0);

  /**
   * Guards every exit path against running twice.
   *
   * There are three of them — the timer, a tap, and a swipe — and they race. Without this,
   * tapping a toast a moment before its timer fires calls `onDone` twice, and the second
   * call dismisses the *next* toast in the queue before it has been on screen for a frame.
   */
  const leaving = useRef(false);

  /**
   * The exit animation's onDone timer. Cleared on unmount: sign-out clears the whole
   * toast store while a card may be mid-exit, and a late onDone would dismiss the head
   * of the *next* account's queue.
   */
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    progress.value = withSpring(1, { damping: 18, stiffness: 220 });

    const timer = setTimeout(() => leave(), LIFETIME_MS);
    return () => {
      clearTimeout(timer);
      if (exitTimer.current) clearTimeout(exitTimer.current);
    };
    // Mount-only, deliberately, and the empty dependency list is not an oversight. `leave`
    // touches only refs and shared values, all of which are stable across renders — and
    // re-running this would restart the four-second lifetime every time the parent
    // re-rendered, which for a toast raised during a busy screen means one that never
    // leaves. Each toast gets a fresh mount via its key, so mount-only is exactly once per
    // toast.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function leave() {
    if (leaving.current) return;
    leaving.current = true;

    progress.value = withTiming(0, { duration: EXIT_MS });
    exitTimer.current = setTimeout(onDone, EXIT_MS);
  }

  function act() {
    if (leaving.current) return;
    toast.onPress?.();
    leave();
  }

  /**
   * Upward only.
   *
   * A toast comes from the top, so up is where it goes back to; allowing it to be flung
   * downwards would send it across the content it is announcing. Downward drag is clamped
   * to zero rather than resisted, which reads as the toast being anchored rather than as a
   * gesture that failed.
   */
  const pan = Gesture.Pan()
    .onChange((event) => {
      drag.value = Math.min(0, drag.value + event.changeY);
    })
    .onEnd(() => {
      if (drag.value < -SWIPE_THRESHOLD) {
        drag.value = withTiming(-160, { duration: EXIT_MS });
        scheduleOnRN(leave);
        return;
      }
      drag.value = withSpring(0, { damping: 20, stiffness: 260 });
    });

  const animated = useAnimatedStyle(() => ({
    opacity: progress.value,
    // The whole transform lives here. An animated transform *replaces* a static one rather
    // than merging with it, so declaring any part of it in a stylesheet would silently drop
    // whichever half lost.
    transform: [{ translateY: (1 - progress.value) * -80 + drag.value }],
  }));

  return (
    <View
      style={[StyleSheet.absoluteFill, { zIndex: 40 }]}
      // The host fills the screen so the toast can be positioned against the top inset, but
      // it must not eat taps on the app underneath. Only the card itself is touchable —
      // `box-none` is what lets presses through the wrapper while keeping them on children.
      pointerEvents="box-none"
    >
      <GestureDetector gesture={pan}>
        <Animated.View style={[{ marginTop: insets.top + 8, marginHorizontal: 12 }, animated]}>
          {/* Outer node carries the depth, inner clips — Android's `overflow: hidden`
              clips a node's own shadow, so one node cannot do both. Same split as `Button`
              and the glowing avatar in `MatchOverlay`. */}
          <View style={lift('lg')}>
            <Pressable
              onPress={act}
              accessibilityRole="button"
              accessibilityLabel={toast.body ? `${toast.title}. ${toast.body}` : toast.title}
              // `active:` sits on the Pressable itself and nowhere below it. On a nested
              // View, NativeWind gives that element its own press handling and it swallows
              // the gesture — the button goes completely dead while still looking like one.
              className="flex-row items-center gap-3 overflow-hidden rounded-card bg-elevated px-4 py-3.5 active:opacity-80"
              style={hairline}
            >
              <View className="h-9 w-9 items-center justify-center rounded-full bg-surface">
                <Icon as={toast.icon} size={18} tone={toast.tone} />
              </View>

              <View className="flex-1 gap-0.5">
                <Text variant="bodyStrong" numberOfLines={1}>
                  {toast.title}
                </Text>
                {/* Mounted conditionally, never emptied — a `Text` with no children still
                    takes a line's height, which makes a one-line toast look mis-padded. */}
                {!!toast.body && (
                  <Text variant="caption" numberOfLines={2}>
                    {toast.body}
                  </Text>
                )}
              </View>
            </Pressable>
          </View>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}
