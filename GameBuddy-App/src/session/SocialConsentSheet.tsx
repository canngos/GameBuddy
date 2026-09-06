import { useEffect } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useT } from '../i18n/useT';
import { openPrivacy, openTerms } from '../legal';
import { Button, Checkbox, Text } from '../ui';

type SocialConsentSheetProps = {
  visible: boolean;
  /** True while the retried sign-in is in flight, so the button can show it. */
  busy: boolean;
  accepted: boolean;
  onAccepted: (accepted: boolean) => void;
  onContinue: () => void;
  onDismiss: () => void;
};

/**
 * The age and terms tick, asked only when a social sign-in is about to create an account.
 *
 * **Why it is here rather than on the welcome screen.** A checkbox under two buttons would
 * be asked of everybody, including the returning gamer who agreed months ago — and a consent
 * everybody has to re-give is not consent, it is a speed bump. The server knows which case
 * this is and says so: the first call comes back `TERMS_NOT_ACCEPTED`, this opens, and the
 * same credential is sent again with the tick. Somebody signing back in never sees it.
 *
 * The sentence itself is the one the registration screen shows, from the same dictionary
 * key, as ordered segments so each language keeps its own word order around the bold age
 * phrase and the two document links. Two wordings of the same promise would be one of them
 * wrong.
 *
 * A positioned sibling rather than a `Modal`, like every other sheet here — see
 * `MatchOverlay` — and no `className` on an animated component.
 */
export function SocialConsentSheet({
  visible,
  busy,
  accepted,
  onAccepted,
  onContinue,
  onDismiss,
}: SocialConsentSheetProps) {
  const t = useT();
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - progress.value) * 380 }],
  }));

  if (!visible) return null;

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          <Pressable className="flex-1" onPress={onDismiss} accessibilityLabel={t.common.close} />

          <Animated.View style={sheetStyle}>
            <View className="max-h-[86%] overflow-hidden rounded-t-[28px] bg-surface">
              <View className="h-1 w-10 self-center rounded-full bg-line" style={{ marginTop: 12 }} />

              <ScrollView contentContainerClassName="gap-4 px-6 pt-4" showsVerticalScrollIndicator={false}>
                <View className="gap-2">
                  <Text variant="title">{t.auth.social.consentTitle}</Text>
                  <Text variant="body" className="text-muted">
                    {t.auth.social.consentBody}
                  </Text>
                </View>

                <Checkbox
                  checked={accepted}
                  onChange={onAccepted}
                  accessibilityLabel={t.auth.register.consentA11y}
                >
                  <Text variant="body">
                    {t.auth.register.consent.map((segment, index) =>
                      segment.link ? (
                        <Text
                          key={index}
                          variant="bodyStrong"
                          className="text-primary"
                          onPress={segment.link === 'terms' ? openTerms : openPrivacy}
                        >
                          {segment.text}
                        </Text>
                      ) : segment.bold ? (
                        <Text key={index} variant="bodyStrong">
                          {segment.text}
                        </Text>
                      ) : (
                        segment.text
                      ),
                    )}
                  </Text>
                </Checkbox>
              </ScrollView>

              <View className="gap-2 px-6 pb-10 pt-2">
                <Button
                  label={t.auth.social.continue}
                  // Disabled rather than refused on tap: there is exactly one thing to do
                  // here and nothing to explain about why it did not work.
                  disabled={!accepted}
                  loading={busy}
                  onPress={onContinue}
                />
                <Button label={t.common.cancel} variant="ghost" onPress={onDismiss} />
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}
