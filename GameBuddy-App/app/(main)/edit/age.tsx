import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import { EditScreen } from '../../../src/ui/EditScreen';
import { Text, TextField } from '../../../src/ui';
import { ageError, MIN_AGE } from '../../../src/validation';

export default function EditAge() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  const [age, setAge] = useState(me.data?.age ?? '');
  const [touched, setTouched] = useState(false);

  const currentBand = bandOf(Number(me.data?.age));
  const nextBand = bandOf(Number(age));
  const crossesBand = !!currentBand && !!nextBand && currentBand !== nextBand;

  const save = useMutation({
    mutationFn: () => authApi.changeAge(Number(age)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // Age decides which population is shown, so the cached feed is now wrong.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="Your age"
      subtitle="Age decides who you are shown. Under-18 and over-18 are never matched with each other."
      onSave={() => {
        setTouched(true);
        if (ageError(age)) return;
        save.mutate();
      }}
      saving={save.isPending}
      error={save.error}
    >
      <TextField
        label="Age"
        value={age}
        onChangeText={(t) => setAge(t.replace(/\D/g, '').slice(0, 2))}
        error={touched ? ageError(age) : null}
        hint={`Between ${MIN_AGE} and 99.`}
        keyboardType="number-pad"
        maxLength={2}
        placeholder="21"
      />

      {crossesBand && (
        <View className="mt-4 rounded-card border border-danger/40 bg-danger/10 p-4">
          <Text variant="bodyStrong" className="text-danger">
            This moves you to a different age group
          </Text>
          {/* Precisely what happens: `getMatches` does not filter by band, so existing
              matches stay in the list — but `ChatMessageService` refuses every message
              with AGE_BAND_MISMATCH, and the deck only draws from the new band. Saying
              "they disappear" would be wrong and would surprise people later. */}
          <Text variant="caption" className="mt-1">
            People you already matched with in your current group stay in your matches,
            but you will no longer be able to message them. New people you are shown
            will all be from the other group.
          </Text>
        </View>
      )}
    </EditScreen>
  );
}

/** The split the backend enforces on every pairing. See AgeBand. */
function bandOf(age: number): 'minor' | 'adult' | null {
  if (!Number.isFinite(age) || age <= 0) return null;
  return age < 18 ? 'minor' : 'adult';
}
