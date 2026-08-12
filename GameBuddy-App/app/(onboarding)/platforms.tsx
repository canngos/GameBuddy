import { Redirect, useRouter } from 'expo-router';
import { View } from 'react-native';
import { useDraft } from '../../src/onboarding/draft';
import { SelectionCount } from '../../src/onboarding/SelectionCount';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { PlatformPicker } from '../../src/profile/PlatformPicker';
import { MIN_PLATFORMS } from '../../src/profile/platforms';
import { Button, Screen } from '../../src/ui';
import { MIN_GAMES, parseBirthDate } from '../../src/validation';

/**
 * What you play on.
 *
 * Sits between games and keywords because that is the order the questions make sense in:
 * what you play, what you play it on, then how you play. It is also the cheapest screen in
 * the run — five options, one tap — so it is a good place to be right after the longest.
 *
 * Nothing is submitted here. Like every other step, the answers go into the draft and the
 * whole profile is written once from the keywords screen.
 */
export default function Platforms() {
  const router = useRouter();
  const draft = useDraft();

  // Same guard as the keywords step: reachable directly after a fast refresh, when the
  // in-memory draft is empty and the earlier answers cannot be recovered.
  const draftIntact =
    !!parseBirthDate(draft.birthDay, draft.birthMonth, draft.birthYear) &&
    !!draft.country &&
    draft.gameIds.length >= MIN_GAMES;
  if (!draftIntact) return <Redirect href="/profile" />;

  return (
    <Screen
      scroll
      footer={
        <View className="gap-2">
          <SelectionCount picked={draft.platformIds.length} minimum={MIN_PLATFORMS} />
          <Button
            label="Next"
            disabled={draft.platformIds.length < MIN_PLATFORMS}
            onPress={() => router.push('/keywords')}
          />
          <Button label="Back" variant="ghost" onPress={() => router.back()} />
        </View>
      }
    >
      <StepHeader
        step={5}
        total={6}
        title="What do you play on?"
        subtitle="Pick everything you use. Other players filter by this, so more is better than fewer."
      />

      <PlatformPicker selected={draft.platformIds} onToggle={draft.togglePlatform} />
    </Screen>
  );
}
