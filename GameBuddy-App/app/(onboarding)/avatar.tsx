import { useRouter } from "expo-router";
import { useState } from "react";
import { View } from "react-native";
import { useT } from "../../src/i18n/useT";
import { useDraft } from "../../src/onboarding/draft";
import { StepHeader } from "../../src/onboarding/StepHeader";
import { AvatarPicker } from "../../src/pickers/AvatarPicker";
import { BackButton, Button, Screen, Text, useScreenScale } from "../../src/ui";

/**
 * Choosing a picture, on its own screen.
 *
 * It used to sit at the bottom of the profile step under the date of birth, the country
 * and the gender question, rendered at 64px in a wrapping row — small enough that you
 * were picking a coloured circle rather than a face. It is the first thing anybody else
 * sees of an account, so it gets the room to be looked at.
 */
export default function AvatarStep() {
  const { pick } = useScreenScale();
  const router = useRouter();
  const t = useT();
  const draft = useDraft();
  const [touched, setTouched] = useState(false);

  function next() {
    setTouched(true);
    if (!draft.avatarId) return;
    router.push("/games");
  }

  return (
    <Screen
      scroll
      footer={
        <View className="gap-2">
          <Button label={t.common.continue} onPress={next} />
        </View>
      }
    >
      <BackButton />

      <StepHeader
        step={3}
        total={6}
        title={t.onboarding.avatar.title}
        subtitle={t.onboarding.avatar.subtitle}
      />

      <AvatarPicker
        selected={draft.avatarId}
        onSelect={(avatarId) => draft.set({ avatarId })}
        // Three to a row on a phone, against the eight-per-row scatter this was before.
        // Three 96dp tiles plus their gaps overrun a 360dp screen's content width, so the
        // tile gives way rather than the column count — two-per-row would change the shape
        // of the step, not just its size.
        size={pick(72, 84, 96)}
        columns={3}
      />

      {touched && !draft.avatarId && (
        <Text variant="caption" className="mt-4 text-danger">
          {t.onboarding.avatar.pickOne}
        </Text>
      )}
    </Screen>
  );
}
