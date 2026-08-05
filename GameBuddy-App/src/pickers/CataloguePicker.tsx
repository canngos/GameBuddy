import { useMemo, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { useThemeColors } from '../theme';
import { Chip } from '../ui/Chip';
import { ErrorNotice } from '../ui/ErrorNotice';
import { Text } from '../ui/Text';
import { TextField } from '../ui/TextField';

export type PickerItem = {
  id: string;
  label: string;
  /** Extra text the search should match, e.g. a game's category. */
  keywords?: string;
};

type CataloguePickerProps = {
  items: PickerItem[];
  selected: string[];
  onToggle: (id: string) => void;
  isLoading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  searchPlaceholder?: string;
};

/**
 * Search plus a wrap of selectable chips.
 *
 * Shared by onboarding and by editing, because they are the same interaction on the
 * same data — the only difference is what happens when you press the button at the
 * bottom. Two copies would have drifted the first time one of them gained a feature.
 */
export function CataloguePicker({
  items,
  selected,
  onToggle,
  isLoading = false,
  error,
  onRetry,
  searchPlaceholder = 'Search',
}: CataloguePickerProps) {
  const colors = useThemeColors();
  const [query, setQuery] = useState('');

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        item.label.toLowerCase().includes(q) || item.keywords?.toLowerCase().includes(q),
    );
  }, [items, query]);

  return (
    <View className="gap-4">
      <TextField
        value={query}
        onChangeText={setQuery}
        placeholder={searchPlaceholder}
        returnKeyType="search"
      />

      {isLoading && <ActivityIndicator color={colors.brand} />}
      {/* `!!` because `error` is `unknown`: `unknown && <JSX/>` is itself `unknown`,
          which is not a valid child. */}
      {!!error && <ErrorNotice error={error} onRetry={onRetry} />}

      <View className="flex-row flex-wrap gap-2">
        {visible.map((item) => (
          <Chip
            key={item.id}
            label={item.label}
            selected={selected.includes(item.id)}
            onPress={() => onToggle(item.id)}
          />
        ))}
      </View>

      {!isLoading && items.length > 0 && visible.length === 0 && (
        <Text className="text-center text-muted">Nothing matches “{query}”.</Text>
      )}
    </View>
  );
}
