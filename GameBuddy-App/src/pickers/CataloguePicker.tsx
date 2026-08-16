import { useMemo, useState } from 'react';
import { ActivityIndicator, Image, Pressable, ScrollView, View } from 'react-native';
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
  /**
   * Which filter options this item matches, keyed by facet id.
   *
   * `{ platform: ['PC', 'SWITCH'], genre: ['RPG'] }`. An item missing a facet matches no
   * option in it, so it disappears when that facet is narrowed and comes back when the
   * facet is cleared — which is the honest behaviour for a game we have not tagged, and
   * the reason the filters start empty.
   */
  facets?: Record<string, string[]>;
};

export type PickerFilter = {
  /** Matches the key used in {@link PickerItem.facets}. */
  key: string;
  /** Names the row for the user. */
  label: string;
  options: { id: string; label: string }[];
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
  /**
   * Optional facet filters, rendered as chip rows above the list.
   *
   * Omitted entirely by the keyword picker, which has forty-eight items and one dimension
   * to think about. Games have three hundred and two.
   */
  filters?: PickerFilter[];
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
  filters,
}: CataloguePickerProps) {
  const colors = useThemeColors();
  const [query, setQuery] = useState('');

  /** Chosen option ids per facet. Empty or absent means that facet constrains nothing. */
  const [chosen, setChosen] = useState<Record<string, string[]>>({});

  const activeCount = useMemo(
    () => Object.values(chosen).reduce((total, ids) => total + ids.length, 0),
    [chosen],
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();

    // OR within a facet, AND across facets: picking PC and Switch widens, picking Switch
    // and RPG narrows. That is what the two rows look like they do, and getting it the
    // other way round makes selecting a second platform mysteriously return nothing.
    const active = Object.entries(chosen).filter(([, ids]) => ids.length > 0);

    return items.filter((item) => {
      if (
        q &&
        !item.label.toLowerCase().includes(q) &&
        !item.detail?.toLowerCase().includes(q) &&
        !item.keywords?.toLowerCase().includes(q)
      ) {
        return false;
      }
      return active.every(([key, ids]) => {
        const values = item.facets?.[key];
        return !!values && ids.some((id) => values.includes(id));
      });
    });
  }, [items, query, chosen]);

  const toggleFilter = (key: string, id: string) =>
    setChosen((current) => {
      const ids = current[key] ?? [];
      return {
        ...current,
        [key]: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id],
      };
    });

  return (
    <View className="gap-4">
      <TextField
        value={query}
        onChangeText={setQuery}
        placeholder={searchPlaceholder}
        returnKeyType="search"
      />

      {filters?.map((filter) => (
        <FilterRow
          key={filter.key}
          filter={filter}
          chosen={chosen[filter.key] ?? []}
          onToggle={(id) => toggleFilter(filter.key, id)}
        />
      ))}

      {activeCount > 0 && (
        <Pressable
          onPress={() => setChosen({})}
          className="min-h-touch justify-center"
          accessibilityRole="button"
          accessibilityLabel="Clear all filters"
        >
          <Text className="text-primary">
            Clear {activeCount} filter{activeCount === 1 ? '' : 's'}
          </Text>
        </Pressable>
      )}

      {isLoading && <ActivityIndicator color={colors.primary} />}
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

      {/* Says which of the two narrowed things to nothing, because the fix differs: clear
          the filters, or search for something else. Blaming the query when a chip emptied
          the list sends people to retype a search that was never the problem. */}
      {!isLoading && items.length > 0 && visible.length === 0 && (
        <Text className="text-center text-muted">
          {query && activeCount > 0
            ? `Nothing matches “${query}” with these filters.`
            : activeCount > 0
              ? 'Nothing matches these filters.'
              : `Nothing matches “${query}”.`}
        </Text>
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
        selected ? 'border-primary' : 'border-transparent',
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
          <View className="absolute right-2 top-2 h-6 w-6 items-center justify-center rounded-full bg-primary">
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
/**
 * One facet's chips, scrolling sideways.
 *
 * Sideways because the two rows together carry twenty-six options, and stacking them as a
 * wrap pushes the games themselves below the fold on a phone — which defeats a control
 * whose job is to help you look at games. Horizontal keeps the filters one line each and
 * the list where it was.
 *
 * `border-primary` for a chosen chip rather than a filled background: the cards below use
 * exactly that to mean "picked", and a filter that highlights differently from a selection
 * reads as a different kind of state.
 */
function FilterRow({
  filter,
  chosen,
  onToggle,
}: {
  filter: PickerFilter;
  chosen: string[];
  onToggle: (id: string) => void;
}) {
  return (
    <View className="gap-1.5">
      <Text className="text-xs text-muted">{filter.label}</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        // Without this the last chip sits flush against the screen edge and looks clipped
        // rather than scrollable.
        contentContainerClassName="gap-2 pr-4"
        keyboardShouldPersistTaps="handled"
      >
        {filter.options.map((option) => {
          const picked = chosen.includes(option.id);
          return (
            <Pressable
              key={option.id}
              onPress={() => onToggle(option.id)}
              accessibilityRole="button"
              accessibilityState={{ selected: picked }}
              className={cn(
                'rounded-full border-2 bg-surface px-3 py-1.5 active:opacity-80',
                picked ? 'border-primary' : 'border-transparent',
              )}
            >
              <Text className={cn('text-sm', picked ? 'text-primary' : 'text-muted')}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

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
        selected ? 'border-primary' : 'border-transparent',
      )}
    >
      <View
        className={cn(
          'h-10 w-10 items-center justify-center rounded-full',
          selected ? 'bg-primary' : 'bg-raised',
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
