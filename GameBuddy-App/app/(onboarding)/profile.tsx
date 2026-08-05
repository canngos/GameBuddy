import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { CountryPicker } from '../../src/onboarding/CountryPicker';
import { AvatarPicker } from '../../src/pickers/AvatarPicker';
import { useDraft } from '../../src/onboarding/draft';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { Button, cn, Screen, Text, TextField } from '../../src/ui';
import { ageError, MIN_AGE } from '../../src/validation';

/**
 * The backend stores gender as a single character and does not constrain it further.
 * `null` here means "not answered" and `''` means "prefer not to say" — a real answer
 * the backend accepts. Keeping them distinct is what stops the decline option from
 * rendering as pre-selected.
 */
const GENDERS = [
  { value: 'M', label: 'Man' },
  { value: 'F', label: 'Woman' },
  { value: 'O', label: 'Other' },
  { value: '', label: 'Prefer not to say' },
] as const;

export default function Profile() {
  const router = useRouter();
  const draft = useDraft();
  const [touched, setTouched] = useState(false);

  const problems = {
    age: ageError(draft.age),
    country: draft.country ? null : 'Choose your country',
    avatar: draft.avatarId ? null : 'Pick an avatar',
  };
  const valid = !problems.age && !problems.country && !problems.avatar;

  function next() {
    setTouched(true);
    if (!valid) return;
    router.push('/games');
  }

  return (
    <Screen scroll>
      <StepHeader
        step={2}
        total={4}
        title="About you"
        subtitle="Age decides who you are shown. Under-18 and over-18 are never matched with each other."
      />

      <View className="gap-6">
        <TextField
          label="Age"
          value={draft.age}
          onChangeText={(t) => draft.set({ age: t.replace(/\D/g, '').slice(0, 2) })}
          error={touched ? problems.age : null}
          hint={`You must be at least ${MIN_AGE}.`}
          keyboardType="number-pad"
          maxLength={2}
          placeholder="21"
        />

        <CountryPicker
          value={draft.country}
          onChange={(country) => draft.set({ country })}
          error={touched ? problems.country : null}
        />

        <View>
          <Text variant="label" className="mb-3 text-muted">
            Gender
          </Text>
          <View className="flex-row flex-wrap gap-2">
            {GENDERS.map((option) => {
              const selected = draft.gender === option.value;
              return (
                <Pressable
                  key={option.label}
                  onPress={() => draft.set({ gender: option.value })}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  className={cn(
                    'rounded-full border-2 px-4 py-2.5 active:opacity-80',
                    selected ? 'border-brand bg-brand' : 'border-line bg-raised',
                  )}
                >
                  <Text
                    variant={selected ? 'bodyStrong' : 'body'}
                    className={selected ? 'text-white' : 'text-content'}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View>
          <Text variant="label" className="mb-3 text-muted">
            Avatar
          </Text>

          <AvatarPicker
            selected={draft.avatarId}
            onSelect={(avatarId) => draft.set({ avatarId })}
          />

          {touched && problems.avatar && (
            <Text variant="caption" className="mt-3 text-danger">
              {problems.avatar}
            </Text>
          )}
        </View>
      </View>

      {/* No Back: the username step arrives here with `replace`, so this is the bottom
          of the stack and a back button would do nothing. */}
      <View className="mt-auto pt-10">
        <Button label="Continue" onPress={next} />
      </View>
    </Screen>
  );
}
