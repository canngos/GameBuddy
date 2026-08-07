import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import { BirthDateField } from '../../../src/onboarding/BirthDateField';
import { Text } from '../../../src/ui';
import { EditScreen } from '../../../src/ui/EditScreen';
import { birthDateError, MIN_AGE, parseBirthDate, toIsoDate } from '../../../src/validation';

export default function EditBirthDate() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  // `birthDate` comes back as yyyy-MM-dd, and only for your own profile. Accounts that
  // predate the 18+ change have an age but no date, so the fields start empty and the
  // account holder types one — which is the only way to get a real date rather than one
  // reverse-engineered from a number.
  const [day, setDay] = useState(() => partOf(me.data?.birthDate, 2));
  const [month, setMonth] = useState(() => partOf(me.data?.birthDate, 1));
  const [year, setYear] = useState(() => partOf(me.data?.birthDate, 0));
  const [touched, setTouched] = useState(false);

  const problem = birthDateError(day, month, year);

  const save = useMutation({
    mutationFn: () => authApi.changeBirthDate(toIsoDate(parseBirthDate(day, month, year)!)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="Date of birth"
      subtitle="Used to confirm you are old enough to be here. Other people see your age, never the date."
      onSave={() => {
        setTouched(true);
        if (problem) return;
        save.mutate();
      }}
      saving={save.isPending}
      error={save.error}
    >
      <BirthDateField
        day={day}
        month={month}
        year={year}
        onChange={(parts) => {
          if (parts.day !== undefined) setDay(parts.day);
          if (parts.month !== undefined) setMonth(parts.month);
          if (parts.year !== undefined) setYear(parts.year);
        }}
        error={touched ? problem : null}
        hint={`You must be ${MIN_AGE} or over.`}
      />

      <View className="mt-4 rounded-card border border-line bg-raised p-4">
        <Text variant="caption">
          Changes to your date of birth are recorded. GameBuddy is for adults only, and a
          date that puts you under {MIN_AGE} will be refused.
        </Text>
      </View>
    </EditScreen>
  );
}

/** `2000-08-24` split on the dash; index 0 is the year, 2 the day. */
function partOf(iso: string | null | undefined, index: number): string {
  return iso?.split('-')[index] ?? '';
}
