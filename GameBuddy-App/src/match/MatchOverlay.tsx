import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import type { Candidate } from '../api/types';
import { Avatar } from '../ui/Avatar';
import { Button } from '../ui/Button';
import { Text } from '../ui/Text';

type MatchOverlayProps = {
  candidate: Candidate | null;
  onDismiss: () => void;
  onMessage: (candidate: Candidate) => void;
};

/**
 * The mutual-match celebration.
 *
 * Driven by the `matched` flag on the accept response, not by the message text — see
 * `AcceptResponseBody` on the backend. Matching is the entire point of the product, so
 * this moment must not depend on English copy staying the same.
 *
 * Two deliberate choices, both learned the hard way:
 *
 * **No `Modal`.** This is an overlay inside our own screen, not something that needs to
 * escape the layout, and React Native's `Modal` brought stacking and sizing problems on
 * web for nothing in return. An absolutely positioned sibling behaves identically on
 * both platforms.
 *
 * **`className` never goes on an `Animated.View`.** NativeWind only routes `className`
 * into a style for components it has registered, and Reanimated's animated components
 * are created lazily, so registering them does not reliably catch the instance JSX
 * uses. The class is then dropped in silence — which does not look like a styling bug,
 * it looks like a layout bug: a full-screen backdrop renders transparent and zero-high.
 * So animated wrappers carry `style` only, and every visual class sits on a plain
 * `View` inside them.
 */
export function MatchOverlay({ candidate, onDismiss, onMessage }: MatchOverlayProps) {
  const visible = !!candidate;
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 14, stiffness: 160 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const contentStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    // Overshoot slightly and settle, which reads as a pop rather than a fade.
    transform: [{ scale: 0.85 + progress.value * 0.15 }],
  }));

  if (!candidate) return null;

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 items-center justify-center bg-ink-900/90 px-8">
          <Animated.View style={contentStyle}>
            <View className="w-full items-center gap-6">
              <View className="items-center gap-1">
                <Text className="font-bold text-[38px] leading-[46px] text-brand">
                  It&apos;s a match!
                </Text>
                <Text className="text-center text-white/80">
                  You and {candidate.gamerUsername} both said yes.
                </Text>
              </View>

              <Avatar
                source={candidate.avatar}
                name={candidate.gamerUsername}
                colorSeed={candidate.userId}
                size={120}
              />

              <View className="w-full gap-3 pt-2">
                <Button label="Send a message" onPress={() => onMessage(candidate)} />
                {/* Secondary, not ghost: ghost's label is `text-muted`, which is dark
                    grey in light mode and would vanish against this overlay. */}
                <Button label="Keep swiping" variant="secondary" onPress={onDismiss} />
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}
