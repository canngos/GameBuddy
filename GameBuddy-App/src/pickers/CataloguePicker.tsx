import { Image } from 'expo-image';
import {
  memo,
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useT } from '../i18n/useT';
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
  /**
   * Must be identity-stable — a fresh closure here defeats the `memo` on every card and
   * puts the whole catalogue back on the render path for one tap. Both onboarding screens
   * pass a zustand action; both settings screens pass a `useCallback` over a functional
   * `setState`.
   */
  onToggle: (id: string) => void;
  /**
   * `grid` is two per row with a cover image — games, where the picture identifies the
   * thing faster than the name does. `rows` is one per row — keywords, where the
   * explanation needs the width and is the reason to read the row at all.
   *
   * Fixed for the life of a mounted picker: it decides `numColumns`, which React Native
   * cannot change without a remount.
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
  /**
   * Scrolls away above the search field — a step header, a title.
   *
   * A prop rather than a sibling because the picker owns the scrolling surface now (see
   * the note on the component), so anything a screen wants to scroll with the list has to
   * come through here.
   */
  header?: ReactNode;
  /** Scrolls in below the last card. A selection count, a submit error. */
  footer?: ReactNode;
};

/** Stable identity, so an unfiltered facet does not look like a changed prop. */
const NO_IDS: string[] = [];

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
 *
 * **This component is the scrolling surface, not a block of content inside one.** It used
 * to map every match into a flex-wrap `View` inside the screen's `ScrollView`, which at
 * three hundred games meant a hundred and fifty rows — about fifty-five screenfuls —
 * mounted at once, with three hundred covers fetched and decoded for the two that were
 * visible. Filtering took about a second and a tap did not feel like a tap. So the screens
 * hand their chrome to `header` / `footer` and stop scrolling themselves; everything above
 * the first card rides in `ListHeaderComponent`.
 */
export function CataloguePicker({
  items,
  selected,
  onToggle,
  layout = 'rows',
  isLoading = false,
  error,
  onRetry,
  searchPlaceholder,
  filters,
  header,
  footer,
}: CataloguePickerProps) {
  const t = useT();
  const [query, setQuery] = useState('');
  /**
   * The field stays bound to `query` so typing is never held up; only the filtering reads
   * the deferred copy. React then keeps the previous list on screen while it recomputes
   * rather than blocking the keystroke behind three hundred items.
   */
  const deferredQuery = useDeferredValue(query);

  /** Chosen option ids per facet. Empty or absent means that facet constrains nothing. */
  const [chosen, setChosen] = useState<Record<string, string[]>>({});

  const activeCount = useMemo(
    () => Object.values(chosen).reduce((total, ids) => total + ids.length, 0),
    [chosen],
  );

  /**
   * One lowercased haystack per item, built when the catalogue arrives.
   *
   * The search used to call `toLowerCase()` on three fields of every item on every
   * keystroke — nine hundred allocations per character, thrown away immediately.
   */
  const haystacks = useMemo(
    () =>
      items.map((item) =>
        `${item.label}\n${item.detail ?? ''}\n${item.keywords ?? ''}`.toLowerCase(),
      ),
    [items],
  );

  const visible = useMemo(() => {
    const q = deferredQuery.trim().toLowerCase();

    // OR within a facet, AND across facets: picking PC and Switch widens, picking Switch
    // and RPG narrows. That is what the two rows look like they do, and getting it the
    // other way round makes selecting a second platform mysteriously return nothing.
    const active = Object.entries(chosen).filter(([, ids]) => ids.length > 0);

    return items.filter((item, index) => {
      if (q && !haystacks[index].includes(q)) return false;
      return active.every(([key, ids]) => {
        const values = item.facets?.[key];
        return !!values && ids.some((id) => values.includes(id));
      });
    });
  }, [items, haystacks, deferredQuery, chosen]);

  /**
   * A set, not the array, because every card asks whether it is selected — `includes` made
   * one render of the grid O(items × selection).
   */
  const selectedSet = useMemo(() => new Set(selected), [selected]);

  const toggleFilter = useCallback(
    (key: string, id: string) =>
      setChosen((current) => {
        const ids = current[key] ?? NO_IDS;
        return {
          ...current,
          [key]: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id],
        };
      }),
    [],
  );

  const clearFilters = useCallback(() => setChosen({}), []);

  const grid = layout === 'grid';

  /**
   * Narrowing the list shortens it under the reader. Without this, somebody scrolled
   * halfway down who taps a chip lands in whatever is left at that offset — usually
   * nothing — and reads it as the filter having broken.
   *
   * `scrollToOffset` does not blur the field, so this is safe to run on every keystroke.
   */
  const listRef = useRef<FlatList<PickerItem>>(null);
  useEffect(() => {
    listRef.current?.scrollToOffset({ offset: 0, animated: false });
  }, [deferredQuery, chosen]);

  const keyExtractor = useCallback((item: PickerItem) => item.id, []);

  const renderItem = useCallback(
    ({ item }: { item: PickerItem }) =>
      grid ? (
        <GameCard item={item} selected={selectedSet.has(item.id)} onToggle={onToggle} />
      ) : (
        <KeywordRow item={item} selected={selectedSet.has(item.id)} onToggle={onToggle} />
      ),
    [grid, selectedSet, onToggle],
  );

  return (
    <FlatList
      ref={listRef}
      className="flex-1"
      data={visible}
      // `numColumns` cannot change on a mounted list (FlatList.js asserts it). `layout` is
      // fixed per call site, so this only ever matters as a guard.
      key={grid ? 'grid' : 'rows'}
      numColumns={grid ? 2 : 1}
      // A plain style rather than `columnWrapperClassName`. The class form exists, but
      // FlatList asserts `!columnWrapperStyle` for a single-column list, and an interop
      // that turned an absent class into an empty array — truthy — would take the two
      // keyword pickers down. Not worth the doubt for one gap.
      columnWrapperStyle={grid ? styles.column : undefined}
      keyExtractor={keyExtractor}
      renderItem={renderItem}
      // The set is a new object per selection change, which is what tells the list its
      // rows are stale. Without it `memo` would hold the old borders on screen.
      extraData={selectedSet}
      ListHeaderComponent={
        // An element of a module-scope component, never an inline arrow: an arrow is a new
        // component *type* every render, so the TextInput inside would remount and lose
        // focus after every character typed.
        <PickerHeader
          header={header}
          query={query}
          onQueryChange={setQuery}
          searchPlaceholder={searchPlaceholder ?? t.common.search}
          filters={filters}
          chosen={chosen}
          onToggleFilter={toggleFilter}
          onClearFilters={clearFilters}
          activeCount={activeCount}
          isLoading={isLoading}
          error={error}
          onRetry={onRetry}
          emptyQuery={deferredQuery}
          showEmpty={!isLoading && items.length > 0 && visible.length === 0}
        />
      }
      // Wrapped because ListFooterComponent takes an element or a component type, not any
      // ReactNode.
      ListFooterComponent={footer ? <View>{footer}</View> : null}
      contentContainerClassName={cn('pb-8', grid ? 'gap-3' : 'gap-2')}
      showsVerticalScrollIndicator={false}
      // A tap on a card while the keyboard is up has to select the card, not just close
      // the keyboard and be swallowed.
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      // In rows, not items: with `numColumns` the list counts `ceil(n / columns)`.
      initialNumToRender={grid ? 6 : 12}
      maxToRenderPerBatch={grid ? 4 : 8}
      updateCellsBatchingPeriod={50}
      // Roughly three viewports either side. This is the knob that caps how many covers
      // are decoded at once; raise it if a violent fling shows blank cells.
      windowSize={7}
      // `removeClippedSubviews` is deliberately not set: it already defaults to true on
      // Android, and forcing it on iOS is the documented way to get missing content.
    />
  );
}

/**
 * Everything above the first card: the screen's own header, search, the empty-state line,
 * the facet chips, and whatever the query is doing.
 *
 * Module scope on purpose — see the `ListHeaderComponent` note above.
 */
function PickerHeader({
  header,
  query,
  onQueryChange,
  searchPlaceholder,
  filters,
  chosen,
  onToggleFilter,
  onClearFilters,
  activeCount,
  isLoading,
  error,
  onRetry,
  emptyQuery,
  showEmpty,
}: {
  header?: ReactNode;
  query: string;
  onQueryChange: (value: string) => void;
  searchPlaceholder: string;
  filters?: PickerFilter[];
  chosen: Record<string, string[]>;
  onToggleFilter: (key: string, id: string) => void;
  onClearFilters: () => void;
  activeCount: number;
  isLoading: boolean;
  error: unknown;
  onRetry?: () => void;
  emptyQuery: string;
  showEmpty: boolean;
}) {
  const colors = useThemeColors();
  const t = useT();

  return (
    <View className="gap-4 pb-1">
      {header}

      <TextField
        value={query}
        onChangeText={onQueryChange}
        placeholder={searchPlaceholder}
        returnKeyType="search"
      />

      {/* Above the filters, not below the list.

          Says which of the two narrowed things to nothing, because the fix differs: clear
          the filters, or search for something else. Blaming the query when a chip emptied
          the list sends people to retype a search that was never the problem.

          Directly under the search field because that is where the eye already is, and
          because the two chip rows are ~380px tall: sitting after them, the line fell
          below the fold on a phone the moment the keyboard was up — which is precisely
          when a search returns nothing and the explanation is needed. */}
      {showEmpty && (
        <Text className="text-center text-muted">
          {emptyQuery && activeCount > 0
            ? t.pickers.noMatchesQueryFiltered(emptyQuery)
            : activeCount > 0
              ? t.pickers.noMatchesFiltered
              : t.pickers.noMatchesQuery(emptyQuery)}
        </Text>
      )}

      {filters?.map((filter) => (
        <FilterRow
          key={filter.key}
          filter={filter}
          chosen={chosen[filter.key] ?? NO_IDS}
          onToggle={onToggleFilter}
        />
      ))}

      {activeCount > 0 && (
        <Pressable
          onPress={onClearFilters}
          className="min-h-touch justify-center"
          accessibilityRole="button"
          accessibilityLabel={t.pickers.clearFiltersA11y}
        >
          <Text className="text-primary">{t.pickers.clearFilters(activeCount)}</Text>
        </Pressable>
      )}

      {isLoading && <ActivityIndicator color={colors.primary} />}
      {/* `!!` because `error` is `unknown`: `unknown && <JSX/>` is itself `unknown`,
          which is not a valid child. */}
      {!!error && <ErrorNotice error={error} onRetry={onRetry} />}
    </View>
  );
}

/**
 * `flex-1` with a half-width cap rather than a fixed width, so two fit a row on a small
 * phone and on a large one without measuring the screen. The cap is what stops a lone card
 * on an odd last row stretching across the whole width.
 */
const styles = StyleSheet.create({
  card: { flex: 1, maxWidth: '50%' },
  column: { gap: 12 },
  // expo-image is not registered with NativeWind — only React Native's own components are
  // — so `className` on it resolves to nothing and fails silently, which is the trap in
  // UI_NOTE §4.9. This is a style.
  cover: { width: '100%', height: '100%' },
});

/**
 * One game: cover, name, category.
 *
 * Memoised on a boolean `selected` and a stable `onToggle`, so selecting a game re-renders
 * that card rather than the catalogue.
 */
const GameCard = memo(function GameCard({
  item,
  selected,
  onToggle,
}: {
  item: PickerItem;
  selected: boolean;
  onToggle: (id: string) => void;
}) {
  const onPress = useCallback(() => onToggle(item.id), [onToggle, item.id]);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      accessibilityLabel={item.label}
      style={styles.card}
      className={cn(
        'overflow-hidden rounded-card border-2 bg-surface active:opacity-80',
        selected ? 'border-primary' : 'border-transparent',
      )}
    >
      <View className="aspect-[3/4] w-full bg-raised">
        {item.image ? (
          <Image
            source={{ uri: item.image }}
            style={styles.cover}
            // `contentFit`, not `resizeMode`: that is React Native's prop and expo-image
            // ignores it.
            contentFit="cover"
            // A disk cache, so the second visit to the picker — register now, edit from
            // settings later — paints without going back to R2.
            cachePolicy="memory-disk"
            // Without this a recycled cell shows the previous game's cover until the new
            // one decodes, which during a fling looks like the grid is mislabelled.
            recyclingKey={item.id}
            transition={150}
            priority="low"
          />
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
});

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
 *
 * Memoised because it sits in the list header, which re-renders on every keystroke, and
 * twenty-six chips is not free.
 */
const FilterRow = memo(function FilterRow({
  filter,
  chosen,
  onToggle,
}: {
  filter: PickerFilter;
  chosen: string[];
  onToggle: (key: string, id: string) => void;
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
              onPress={() => onToggle(filter.key, option.id)}
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
});

/** One keyword: icon, name, and what picking it means. */
const KeywordRow = memo(function KeywordRow({
  item,
  selected,
  onToggle,
}: {
  item: PickerItem;
  selected: boolean;
  onToggle: (id: string) => void;
}) {
  const colors = useThemeColors();
  const onPress = useCallback(() => onToggle(item.id), [onToggle, item.id]);

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
});
