import { useMemo, useState } from 'react';
import { FlatList, Modal, Pressable, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { searchCountries } from '../countries';
import { Button } from '../ui/Button';
import { cn } from '../ui/cn';
import { Text } from '../ui/Text';
import { TextField } from '../ui/TextField';

type CountryPickerProps = {
  value: string;
  onChange: (country: string) => void;
  error?: string | null;
};

export function CountryPicker({ value, onChange, error }: CountryPickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');

  const results = useMemo(() => searchCountries(query), [query]);

  function choose(country: string) {
    onChange(country);
    setOpen(false);
    // Reset so reopening starts from the suggested list rather than the last search.
    setQuery('');
  }

  return (
    <View>
      <Text variant="label" className="mb-2 text-muted">
        Country
      </Text>

      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={value ? `Country: ${value}` : 'Choose a country'}
        className={cn(
          'min-h-touch flex-row items-center justify-between rounded-field border-2 bg-field px-4 active:bg-field-focus',
          error ? 'border-danger bg-field-focus' : 'border-transparent',
        )}
      >
        <Text className={value ? 'text-content' : 'text-muted'}>
          {value || 'Choose a country'}
        </Text>
        {/* A chevron drawn from a rotated square: no icon font is bundled yet. */}
        <View className="h-2 w-2 -translate-y-0.5 rotate-45 border-b-2 border-r-2 border-muted" />
      </Pressable>

      {error && (
        <Text variant="caption" className="ml-1 mt-1.5 text-danger">
          {error}
        </Text>
      )}

      <Modal visible={open} animationType="slide" onRequestClose={() => setOpen(false)}>
        <SafeAreaView className="flex-1 bg-canvas">
          <View className="gap-4 px-6 pb-4 pt-6">
            <Text variant="heading">Where are you?</Text>
            <TextField
              value={query}
              onChangeText={setQuery}
              placeholder="Search"
              autoFocus
              returnKeyType="search"
            />
          </View>

          <FlatList
            data={results}
            keyExtractor={(item) => item}
            keyboardShouldPersistTaps="handled"
            ListEmptyComponent={
              <Text className="p-6 text-center text-muted">
                No country matches “{query}”.
              </Text>
            }
            renderItem={({ item }) => (
              <Pressable
                onPress={() => choose(item)}
                className="min-h-touch flex-row items-center justify-between px-6 active:bg-raised"
              >
                <Text variant={item === value ? 'bodyStrong' : 'body'}>{item}</Text>
                {item === value && (
                  <View className="h-2.5 w-2.5 rounded-full bg-primary" />
                )}
              </Pressable>
            )}
          />

          <View className="border-t border-line p-4">
            <Button label="Cancel" variant="ghost" onPress={() => setOpen(false)} />
          </View>
        </SafeAreaView>
      </Modal>
    </View>
  );
}
