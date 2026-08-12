import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { billingApi } from '../api/billing';
import { trackFunnel } from '../api/funnel';
import { Button } from '../ui/Button';
import { Text } from '../ui/Text';

type UpgradePromptSheetProps = {
  /** The server's answer to "is it due". Everything behind it is decided there. */
  due: boolean;
};

/**
 * The one-time day-3 nudge towards Gold.
 *
 * Shown on the deck, on the third day, to somebody who has already had a match — never
 * before then, and never twice. The server decides all of that; this component's only
 * responsibilities are to render it and to report honestly that it did.
 *
 * **It is not shown at a moment of frustration**, which is what separates it from
 * `LimitSheet`. That one appears when a limit has just stopped somebody, and its job is to
 * offer the way past. This one appears when things are going well, and says so — the pitch
 * is "you are getting matches, here is how to get more", not "you have run out".
 *
 * See {@link LimitSheet} for why this is a positioned sibling rather than a `Modal`, and
 * why no `className` appears on an animated component.
 */
export function UpgradePromptSheet({ due }: UpgradePromptSheetProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  // Latched, not read straight from `due`. Acknowledging it invalidates the subscription
  // query, which flips `due` back to false — and driving the animation directly off that
  // would snap the sheet away mid-dismissal instead of letting it slide out.
  const [visible, setVisible] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const acknowledged = useRef(false);

  useEffect(() => {
    if (due && !dismissed) setVisible(true);
  }, [due, dismissed]);

  // Reported once, when it actually reaches the screen. The server deliberately does not
  // mark it on the read — see billingApi.markUpgradePromptSeen — so this is what spends
  // the single showing, and it must happen exactly once per mount.
  useEffect(() => {
    if (!visible || acknowledged.current) return;
    acknowledged.current = true;

    trackFunnel('PAYWALL_TRIGGERED');
    void billingApi
      .markUpgradePromptSeen()
      .then(() => {
        // So a later screen does not re-read a stale `upgradePromptDue: true`.
        void queryClient.invalidateQueries({ queryKey: ['subscription'] });
      })
      .catch(() => {
        // Swallowed on purpose. The worst outcome is being asked once more on a later
        // visit, which is not worth an error in front of somebody who is enjoying the app.
      });
  }, [visible, queryClient]);

  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - progress.value) * 420 }],
  }));

  function close() {
    setVisible(false);
    // Latched separately so the effect above cannot re-open it while `due` is still true
    // in the cache.
    setDismissed(true);
  }

  if (!visible) return null;

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          <Pressable className="flex-1" onPress={close} accessibilityLabel="Close" />

          <Animated.View style={sheetStyle}>
            <View className="gap-4 rounded-t-[28px] bg-surface p-6 pb-10">
              <View className="h-1 w-10 self-center rounded-full bg-line" />

              <View className="gap-2">
                <Text variant="title">It's working</Text>
                <Text variant="body" className="text-muted">
                  You've matched with someone. Gold is for when you want to do more of it.
                </Text>
              </View>

              {/* Three lines, and each is a thing they have already bumped into rather
                  than a feature list. Somebody three days in has seen the like cap, has
                  wondered who liked them, and has watched the deck run dry. */}
              <View className="gap-2 rounded-card bg-raised p-4">
                <Benefit text="No daily like limit" />
                <Benefit text="See everyone who liked you" />
                <Benefit text="Filter the deck by game, region and who's online" />
              </View>

              <View className="gap-2">
                <Button
                  label="See Gold"
                  onPress={() => {
                    close();
                    router.push('/gold');
                  }}
                />
                {/* "Not now" rather than "No thanks": this is asked once, and the wording
                    should not make declining feel like a door closing. */}
                <Button label="Not now" variant="ghost" onPress={close} />
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

function Benefit({ text }: { text: string }) {
  return (
    <View className="flex-row items-start gap-2">
      <Text className="text-[14px] leading-[20px] text-brand">✓</Text>
      <Text variant="caption" className="flex-1">
        {text}
      </Text>
    </View>
  );
}
