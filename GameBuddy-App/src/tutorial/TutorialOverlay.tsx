import { useRouter } from 'expo-router';
import { useEffect } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
import { Button, Text } from '../ui';
import { TUTORIAL_STEPS, useTutorial } from './store';

/**
 * The one-time walkthrough of the five tabs.
 *
 * It actually switches tab as it goes, rather than describing the tabs from a stack of
 * cards over a dimmed screen. People remember where a thing is, not what it was called,
 * and the screen behind each caption is the real one with their own data on it.
 *
 * Rendered inside the tabs layout so the tab bar stays visible and keeps moving
 * underneath — the point is to watch the highlight travel along it.
 *
 * A `Modal` rather than an absolutely-positioned View: the tab bar and the deck's gesture
 * handler both sit above ordinary siblings, and an overlay somebody can swipe a card
 * through is not an overlay.
 */
export function TutorialOverlay() {
  const router = useRouter();
  const t = useT();
  const upper = useUpper();
  const insets = useSafeAreaInsets();

  const step = useTutorial((s) => s.step);
  const next = useTutorial((s) => s.next);
  const finish = useTutorial((s) => s.finish);

  const current = step === null ? null : TUTORIAL_STEPS[step];

  // Navigation is a side effect of the step changing, not something the buttons do, so
  // starting at step 0 lands on the deck without every caller having to remember to.
  useEffect(() => {
    if (current) router.replace(current.route);
  }, [current, router]);

  if (!current || step === null) return null;

  const copy = t.tutorial.steps[current.key];
  const isLast = step === TUTORIAL_STEPS.length - 1;

  return (
    <Modal transparent animationType="fade" statusBarTranslucent onRequestClose={() => void finish()}>
      {/* Dimmed, and tapping the dim area advances. A modal that only responds to a
          small button is a modal people feel stuck in. */}
      <Pressable className="flex-1 justify-end bg-black/70" onPress={next}>
        {/* Stops a tap on the card itself from counting as a tap on the backdrop. */}
        <Pressable
          onPress={() => {}}
          style={{ marginBottom: insets.bottom + 72 }}
          className="mx-4 gap-3 rounded-card bg-surface p-5"
        >
          <View className="flex-row items-center justify-between">
            <Text variant="overline">
              {upper(t.tutorial.stepOf(step + 1, TUTORIAL_STEPS.length))}
            </Text>
            <Pressable onPress={() => void finish()} hitSlop={12} accessibilityRole="button">
              <Text variant="label" className="text-muted">
                {t.common.skip}
              </Text>
            </Pressable>
          </View>

          <Text variant="heading">{copy.title}</Text>
          <Text variant="body" className="text-muted">
            {copy.body}
          </Text>

          {/* Progress dots, mirroring the segmented bar onboarding uses, so the two
              read as the same idea at two moments. */}
          <View className="flex-row gap-1.5 pt-1">
            {TUTORIAL_STEPS.map((s, i) => (
              <View
                key={s.route}
                className={`h-1 flex-1 rounded-full ${i <= step ? 'bg-primary' : 'bg-line'}`}
              />
            ))}
          </View>

          <View className="pt-2">
            <Button label={isLast ? t.tutorial.startPlaying : t.common.next} onPress={next} />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
