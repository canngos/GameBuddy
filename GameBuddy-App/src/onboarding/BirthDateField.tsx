import { useRef } from 'react';
import { type TextInput, View } from 'react-native';
import { useT } from '../i18n/useT';
import { Text, TextField } from '../ui';

type BirthDateFieldProps = {
  day: string;
  month: string;
  year: string;
  onChange: (parts: { day?: string; month?: string; year?: string }) => void;
  error?: string | null;
  hint?: string;
};

/**
 * Three number fields rather than a native date picker.
 *
 * A spinner defaulting to today is a bad way to enter a birthday — reaching 1998 means
 * scrolling through three hundred months — and `@react-native-community/datetimepicker`
 * would be a native dependency, rendered differently on each platform, to collect three
 * numbers. Typing them is faster and behaves identically everywhere.
 *
 * Focus advances on its own: two digits in the day box jumps to the month, two in the
 * month jumps to the year. Without that, entering a date is three taps and three keyboard
 * dismissals.
 */
export function BirthDateField({ day, month, year, onChange, error, hint }: BirthDateFieldProps) {
  const t = useT();
  const monthRef = useRef<TextInput>(null);
  const yearRef = useRef<TextInput>(null);

  const digitsOnly = (value: string, max: number) => value.replace(/\D/g, '').slice(0, max);

  return (
    <View>
      <Text variant="label" className="mb-3 text-muted">
        {t.settings.birthDate}
      </Text>

      <View className="flex-row gap-3">
        <View className="flex-1">
          <TextField
            label={t.onboarding.birthDate.day}
            value={day}
            onChangeText={(text) => {
              const next = digitsOnly(text, 2);
              onChange({ day: next });
              if (next.length === 2) monthRef.current?.focus();
            }}
            keyboardType="number-pad"
            maxLength={2}
            placeholder="24"
          />
        </View>

        <View className="flex-1">
          <TextField
            ref={monthRef}
            label={t.onboarding.birthDate.month}
            value={month}
            onChangeText={(text) => {
              const next = digitsOnly(text, 2);
              onChange({ month: next });
              if (next.length === 2) yearRef.current?.focus();
            }}
            keyboardType="number-pad"
            maxLength={2}
            placeholder="08"
          />
        </View>

        <View className="flex-[1.4]">
          <TextField
            ref={yearRef}
            label={t.onboarding.birthDate.year}
            value={year}
            onChangeText={(text) => onChange({ year: digitsOnly(text, 4) })}
            keyboardType="number-pad"
            maxLength={4}
            placeholder="1998"
          />
        </View>
      </View>

      {error ? (
        <Text variant="caption" className="mt-2 text-danger">
          {error}
        </Text>
      ) : (
        hint && (
          <Text variant="caption" className="mt-2 text-muted">
            {hint}
          </Text>
        )
      )}
    </View>
  );
}
