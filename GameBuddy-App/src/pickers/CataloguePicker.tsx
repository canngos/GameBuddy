import { useMemo, useState } from 'react';
import { ActivityIndicator, Image, Pressable, View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from '../ui/cn';
import { ErrorNotice } from '../ui/ErrorNotice';
import { KeywordIcon } from '../ui/KeywordIcon';
import { Text } from '../ui/Text';
import { TextField } from '../ui/TextField';

export type PickerItem = {
  id: string;
  label: string;
  /** The second line: a game's category, or what a keyword actually means. */
  detail?: string | null;
  /** A game's cover. Null renders the lettered fallback rather than a gap. */
  image?: string | null;
  /** Extra text the search should match but which is not displayed. */
  keywords?: string;
};

type CataloguePickerProps = {
  items: PickerItem[];
  selected: string[];
  onToggle: (id: string) => void;
  /**
   * `grid` is two per row with a cover image — games, where the picture identifies the
   * thing faster than the name does. `rows` is one per row — keywords, where the
   * explanation needs the width and is the reason to read the row at all.
   */
  layout?: 'grid' | 'rows';
  isLoading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  searchPlaceholder?: string;
};

/**
 * Search, then a list of selectable rows.
 *
 * Was a wrap of chips. At a hundred games and forty-eight keywords that is a wall of
 * identically-shaped words: nothing is scannable, and the label is all you get — no
 * category, no cover, no idea what "min-maxer" is asking you. The list costs vertical
 * space and buys the ability to actually choose.
 *
 * Shared by onboarding and by editing, because they are the same interaction on the same
 * data; the only difference is what the button at the bottom does.
 */
export function CataloguePicker({
  items,
  selected,
  onToggle,
  layout = 'rows',
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
        item.label.toLowerCase().includes(q) ||
        item.detail?.toLowerCase().includes(q) ||
        item.keywords?.toLowerCase().includes(q),
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

      <View className={cn(layout === 'grid' ? 'flex-row flex-wrap gap-3' : 'gap-2')}>
        {visible.map((item) =>
          layout === 'grid' ? (
            <GameCard
              key={item.id}
              item={item}
              selected={selected.includes(item.id)}
              onPress={() => onToggle(item.id)}
            />
          ) : (
            <KeywordRow
              key={item.id}
              item={item}
              selected={selected.includes(item.id)}
              onPress={() => onToggle(item.id)}
            />
          ),
        )}
      </View>

      {!isLoading && items.length > 0 && visible.length === 0 && (
        <Text className="text-center text-muted">Nothing matches “{query}”.</Text>
      )}
    </View>
  );
}

/**
 * One game: cover, name, category.
 *
 * `flex-[0_0_48%]` rather than a fixed width, so two fit a row on a small phone and on a
 * large one without measuring the screen.
 */
function GameCard({
  item,
  selected,
  onPress,
}: {
  item: PickerItem;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      accessibilityLabel={item.label}
      style={{ flexBasis: '48%' }}
      className={cn(
        'overflow-hidden rounded-card border-2 bg-surface active:opacity-80',
        selected ? 'border-brand' : 'border-transparent',
      )}
    >
      <View className="aspect-[3/4] w-full bg-raised">
        {item.image ? (
          <Image source={{ uri: item.image }} resizeMode="cover" className="h-full w-full" />
        ) : (
          // No cover yet. The initial beats an empty rectangle, and beats a broken-image
          // icon by a long way.
          <View className="h-full w-full items-center justify-center">
            <Text variant="display" className="text-muted">
              {item.label.slice(0, 1).toUpperCase()}
            </Text>
          </View>
        )}

        {selected && (
          <View className="absolute right-2 top-2 h-6 w-6 items-center justify-center rounded-full bg-brand">
            <View className="h-2 w-2 rounded-full bg-white" />
          </View>
        )}
      </View>

      <View className="gap-0.5 p-2.5">
        <Text variant="bodyStrong" numberOfLines={1}>
          {item.label}
        </Text>
        {!!item.detail && (
          <Text variant="caption" numberOfLines={1}>
            {item.detail}
          </Text>
        )}
      </View>
    </Pressable>
  );
}

/** One keyword: icon, name, and what picking it means. */
function KeywordRow({
  item,
  selected,
  onPress,
}: {
  item: PickerItem;
  selected: boolean;
  onPress: () => void;
}) {
  const colors = useThemeColors();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      accessibilityLabel={item.label}
      accessibilityHint={item.detail ?? undefined}
      className={cn(
        'min-h-touch flex-row items-center gap-3 rounded-card border-2 bg-surface px-3 py-2.5 active:opacity-80',
        selected ? 'border-brand' : 'border-transparent',
      )}
    >
      <View
        className={cn(
          'h-10 w-10 items-center justify-center rounded-full',
          selected ? 'bg-brand' : 'bg-raised',
        )}
      >
        <KeywordIcon
          keyword={item.label}
          // White on the filled circle, muted otherwise — the same treatment the tab bar
          // gives its icons, so selection reads the same way everywhere.
          color={selected ? '#FFFFFF' : colors.muted}
        />
      </View>

      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{item.label}</Text>
        {!!item.detail && <Text variant="caption">{item.detail}</Text>}
      </View>
    </Pressable>
  );
}
