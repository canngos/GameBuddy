import { useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Button } from './Button';
import { ErrorNotice } from './ErrorNotice';
import { Screen } from './Screen';
import { Text } from './Text';

type EditScreenProps = {
  title: string;
  subtitle?: string;
  children: ReactNode;
  /** Runs the save. Navigation back happens on success. */
  onSave: () => void;
  saving?: boolean;
  /** Disables save, e.g. while a minimum has not been met. */
  canSave?: boolean;
  error?: unknown;
  saveLabel?: string;
};

/**
 * Chrome shared by the "change one thing" screens.
 *
 * Save is at the bottom rather than in a header, because these screens are thumbed
 * one-handed and the top of a phone is the hardest place to reach.
 */
export function EditScreen({
  title,
  subtitle,
  children,
  onSave,
  saving = false,
  canSave = true,
  error,
  saveLabel = 'Save',
}: EditScreenProps) {
  const router = useRouter();

  return (
    <Screen scroll edges={['top', 'bottom']}>
      <View className="gap-2 pb-6 pt-8">
        <Text variant="title">{title}</Text>
        {subtitle && (
          <Text variant="body" className="text-muted">
            {subtitle}
          </Text>
        )}
      </View>

      {children}

      {!!error && (
        <View className="pt-4">
          <ErrorNotice error={error} />
        </View>
      )}

      <View className="mt-auto gap-2 pt-10">
        <Button label={saveLabel} loading={saving} disabled={!canSave} onPress={onSave} />
        <Button
          label="Cancel"
          variant="ghost"
          disabled={saving}
          onPress={() => router.back()}
        />
      </View>
    </Screen>
  );
}
