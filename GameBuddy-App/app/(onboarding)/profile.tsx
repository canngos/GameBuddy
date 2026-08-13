import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { CountryPicker } from '../../src/onboarding/CountryPicker';
import { useDraft } from '../../src/onboarding/draft';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { BirthDateField } from '../../src/onboarding/BirthDateField';
import { Button, cn, Screen, Text } from '../../src/ui';
import { birthDateError, MIN_AGE } from '../../src/validation';

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
    birthDate: birthDateError(draft.birthDay, draft.birthMonth, draft.birthYear),
    country: draft.country ? null : 'Choose your country',
  };
  const valid = !problems.birthDate && !problems.country;

  function next() {
    setTouched(true);
    if (!valid) return;
    router.push('/avatar');
  }

  return (
    <Screen scroll>
      <StepHeader
        step={2}
        total={6}
        title="About you"
        subtitle="GameBuddy is for adults. We use your date of birth to confirm you are 18 or over — it is never shown to anyone."
      />

      <View className="gap-6">
        <BirthDateField
          day={draft.birthDay}
          month={draft.birthMonth}
          year={draft.birthYear}
          onChange={(parts) =>
            draft.set({
              ...(parts.day !== undefined && { birthDay: parts.day }),
              ...(parts.month !== undefined && { birthMonth: parts.month }),
              ...(parts.year !== undefined && { birthYear: parts.year }),
            })
          }
          error={touched ? problems.birthDate : null}
          hint={`You must be ${MIN_AGE} or over.`}
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
                    selected ? 'border-primary bg-primary' : 'border-line bg-raised',
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

      </View>

      {/* No Back: the username step arrives here with `replace`, so this is the bottom
          of the stack and a back button would do nothing. */}
      <View className="mt-auto pt-10">
        <Button label="Continue" onPress={next} />
      </View>
    </Screen>
  );
}
