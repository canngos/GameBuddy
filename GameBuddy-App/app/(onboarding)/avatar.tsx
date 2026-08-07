import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { useDraft } from '../../src/onboarding/draft';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { AvatarPicker } from '../../src/pickers/AvatarPicker';
import { Button, Screen, Text } from '../../src/ui';

/**
 * Choosing a picture, on its own screen.
 *
 * It used to sit at the bottom of the profile step under the date of birth, the country
 * and the gender question, rendered at 64px in a wrapping row — small enough that you
 * were picking a coloured circle rather than a face. It is the first thing anybody else
 * sees of an account, so it gets the room to be looked at.
 */
export default function AvatarStep() {
  const router = useRouter();
  const draft = useDraft();
  const [touched, setTouched] = useState(false);

  function next() {
    setTouched(true);
    if (!draft.avatarId) return;
    router.push('/games');
  }

  return (
    <Screen scroll>
      <StepHeader
        step={3}
        total={5}
        title="Pick your look"
        subtitle="This is what people see first. You can upload a real photo later, from your profile."
      />

      <AvatarPicker
        selected={draft.avatarId}
        onSelect={(avatarId) => draft.set({ avatarId })}
        // Three to a row on a phone, against the eight-per-row scatter this was before.
        size={96}
        columns={3}
      />

      {touched && !draft.avatarId && (
        <Text variant="caption" className="mt-4 text-danger">
          Pick one to carry on.
        </Text>
      )}

      <View className="mt-auto gap-2 pt-10">
        <Button label="Continue" onPress={next} />
        <Button label="Back" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
