import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { useT } from '../../src/i18n/useT';
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
const GENDER_VALUES = ['M', 'F', 'O', ''] as const;

export default function Profile() {
  const router = useRouter();
  const t = useT();
  const draft = useDraft();
  const [touched, setTouched] = useState(false);

  // Built here rather than as a module constant: a `const` array of labels is evaluated
  // once at import time, long before anybody has chosen a language.
  const genderLabels: Record<(typeof GENDER_VALUES)[number], string> = {
    M: t.onboarding.profile.genderMan,
    F: t.onboarding.profile.genderWoman,
    O: t.onboarding.profile.genderOther,
    '': t.onboarding.profile.genderNone,
  };

  const problems = {
    birthDate: birthDateError(draft.birthDay, draft.birthMonth, draft.birthYear),
    country: draft.country ? null : t.onboarding.profile.countryRequired,
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
        title={t.onboarding.profile.title}
        subtitle={t.onboarding.profile.subtitle}
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
          error={touched && problems.birthDate ? problems.birthDate(t) : null}
          hint={t.onboarding.profile.ageHint(MIN_AGE)}
        />

        <CountryPicker
          value={draft.country}
          onChange={(country) => draft.set({ country })}
          error={touched ? problems.country : null}
        />

        <View>
          <Text variant="label" className="mb-3 text-muted">
            {t.onboarding.profile.gender}
          </Text>
          <View className="flex-row flex-wrap gap-2">
            {GENDER_VALUES.map((value) => {
              const selected = draft.gender === value;
              const label = genderLabels[value];
              return (
                <Pressable
                  key={value || 'none'}
                  onPress={() => draft.set({ gender: value })}
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
                    {label}
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
        <Button label={t.common.continue} onPress={next} />
      </View>
    </Screen>
  );
}
