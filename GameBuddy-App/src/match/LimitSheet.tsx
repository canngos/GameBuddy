import { useRouter } from 'expo-router';
import { useEffect } from 'react';
import { trackFunnel } from '../api/funnel';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import type { SwipeAllowance } from '../api/types';
import { Button } from '../ui/Button';
import { Text } from '../ui/Text';
import type { Block } from './useDeck';

type LimitSheetProps = {
  block: Block | null;
  allowance: SwipeAllowance | null;
  onDismiss: () => void;
};

const COPY: Record<Block['kind'], { title: string; body: string }> = {
  'accept-limit': {
    title: "That's today's likes",
    body: 'You can keep looking and passing. Gold lifts the cap.',
  },
  'swipe-limit': {
    title: "That's today's swipes",
    body: 'The deck comes back tomorrow. Gold removes the daily limit.',
  },
  subscription: {
    title: 'GameBuddy Gold',
    body: 'This one is part of Gold.',
  },
};

/**
 * Shown when the backend refuses a decision because of a limit.
 *
 * The card that triggered it has already been put back by `useDeck`, so dismissing
 * this returns the gamer to exactly where they were rather than one person poorer.
 *
 * The upgrade button goes to the paywall, not to a store sheet: no IAP library is
 * installed yet, so `/gold` is where a purchase currently gets made (with a development
 * receipt). When the library lands, only the paywall changes.
 *
 * See {@link MatchOverlay} for why this is a positioned sibling rather than a `Modal`,
 * and why no `className` appears on an animated component here.
 */
export function LimitSheet({ block, allowance, onDismiss }: LimitSheetProps) {
  const router = useRouter();
  const visible = !!block;
  const progress = useSharedValue(0);

  // The moment a limit actually bites. Recorded here rather than at the paywall because
  // this is where demand appears; whether it converts is the next event's job.
  useEffect(() => {
    if (visible) trackFunnel('PAYWALL_TRIGGERED');
  }, [visible]);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    // Slides up from below its own height.
    transform: [{ translateY: (1 - progress.value) * 380 }],
  }));

  if (!block) return null;
  const copy = COPY[block.kind];

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          {/* Tapping the dimmed area closes, which is the gesture people try first. */}
          <Pressable className="flex-1" onPress={onDismiss} accessibilityLabel="Close" />

          <Animated.View style={sheetStyle}>
            <View className="gap-4 rounded-t-[28px] bg-surface p-6 pb-10">
              <View className="h-1 w-10 self-center rounded-full bg-line" />

              <View className="gap-2">
                <Text variant="title">{copy.title}</Text>
                <Text variant="body" className="text-muted">
                  {copy.body}
                </Text>
              </View>

              {/* The server's own message, kept because it is the authority on what was
                  refused and why — the copy above is framing, not the reason. */}
              <View className="rounded-card bg-raised p-4">
                <Text variant="caption">{block.message}</Text>
              </View>

              {allowance && !allowance.unlimited && (
                <View className="flex-row gap-3">
                  <Tally label="Swipes left" value={allowance.remainingSwipes} />
                  <Tally label="Likes left" value={allowance.remainingAccepts} />
                </View>
              )}

              {/* The upgrade is the primary action and dismissing is secondary, because
                  this sheet only appears at the moment the limit is the thing in the way
                  — which is the one moment removing it is worth paying for. It used to
                  offer only "Keep looking", which acknowledged the wall and then left the
                  gamer standing at it. */}
              <View className="gap-2">
                <Button
                  label="Get Gold — no daily limit"
                  onPress={() => {
                    // Dismiss first: the sheet is a positioned sibling of the deck rather
                    // than a Modal, so leaving it mounted would put it over the paywall.
                    onDismiss();
                    router.push('/gold');
                  }}
                />
                <Button label="Keep looking" variant="ghost" onPress={onDismiss} />
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

function Tally({ label, value }: { label: string; value: number }) {
  return (
    <View className="flex-1 items-center gap-0.5 rounded-card bg-raised py-3">
      <Text className="font-bold text-[22px] leading-[28px] text-brand">{value}</Text>
      <Text variant="caption">{label}</Text>
    </View>
  );
}
