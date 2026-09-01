import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { memo, useCallback, useEffect, useState } from 'react';
import { FlatList, Pressable, ScrollView, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import { usePromoDraft } from '../../src/admin/promoDraft';
import type { DirectoryFilter, DirectoryUser } from '../../src/api/types';
import { Avatar, Button, Card, ErrorNotice, Screen, Text, TextField } from '../../src/ui';

/**
 * Choosing who a code goes to.
 *
 * The console has deliberately never listed the user base — the Accounts tab says so in its
 * own comment, and browsing everybody is exactly what it refused to be. This screen is the
 * exception the feature forced: a gift has to be addressed to somebody, and there is no way
 * to address one to a person you cannot find. So it is a search rather than a directory —
 * you type who you are looking for, or pick a group the app can already describe.
 *
 * The cohorts are the reason it is worth having at all. "Everybody who has not opened the
 * app in a fortnight" is not a list anyone can assemble by scrolling, and it is the group
 * a promotion is actually for.
 */
const FILTERS: { key: DirectoryFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'OFFLINE_14D', label: 'Away 14d' },
  { key: 'REPORT_CONTRIBUTORS', label: 'Reporters' },
  { key: 'GOLD', label: 'Gold' },
  { key: 'FREE', label: 'Free' },
  { key: 'NEW_7D', label: 'New' },
];

export default function PromoUsersScreen() {
  const router = useRouter();

  const selected = usePromoDraft((state) => state.selected);
  const locked = usePromoDraft((state) => state.locked);
  const toggle = usePromoDraft((state) => state.toggle);
  const add = usePromoDraft((state) => state.add);
  const storedQuery = usePromoDraft((state) => state.query);
  const storedFilter = usePromoDraft((state) => state.filter);
  const setSearch = usePromoDraft((state) => state.setSearch);

  const [typed, setTyped] = useState(storedQuery);
  const [query, setQuery] = useState(storedQuery);
  const [filter, setFilter] = useState<DirectoryFilter>(storedFilter);
  const [truncated, setTruncated] = useState(false);

  // Debounced, because the query runs against the whole gamer table and a request per
  // keystroke would put five of them in flight for a five-letter name.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(typed.trim()), 300);
    return () => clearTimeout(timer);
  }, [typed]);

  // Remembered so backing out to the editor and returning does not lose the search that
  // took a moment to type.
  useEffect(() => setSearch(query, filter), [query, filter, setSearch]);

  const people = useQuery({
    queryKey: ['admin', 'users', query, filter],
    queryFn: () => adminApi.searchUsers({ q: query || undefined, filter }),
    staleTime: 30_000,
  });

  const selectAll = useCallback(async () => {
    const matching = await adminApi.searchUserIds({ q: query || undefined, filter });
    // The ids come back without names, and the chips in the editor read better with them,
    // so the page that is already loaded fills in whoever it knows about.
    const known = new Map((people.data?.users ?? []).map((person) => [person.userId, person]));
    add(
      matching.ids.map(
        (userId) =>
          known.get(userId) ?? {
            userId,
            username: null,
            email: null,
            avatar: null,
            createdDate: null,
            lastActiveAt: null,
            gold: false,
          },
      ),
    );
    setTruncated(matching.truncated);
  }, [query, filter, people.data, add]);

  const rows = people.data?.users ?? [];
  const count = Object.keys(selected).length;

  const keyExtractor = useCallback((person: DirectoryUser) => person.userId, []);
  const renderRow = useCallback(
    ({ item }: { item: DirectoryUser }) => (
      <PersonRow
        person={item}
        selected={Boolean(selected[item.userId])}
        locked={locked.has(item.userId)}
        onToggle={toggle}
      />
    ),
    [selected, locked, toggle],
  );

  return (
    <Screen edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Promotion codes</Text>
        <Text variant="title">Choose people</Text>
      </View>

      <TextField
        label="Search"
        value={typed}
        onChangeText={setTyped}
        autoCapitalize="none"
        autoCorrect={false}
        hint="The start of a username or an address."
      />

      <View className="py-3">
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2">
          {FILTERS.map((option) => (
            <FilterChip
              key={option.key}
              label={option.label}
              active={filter === option.key}
              onPress={() => setFilter(option.key)}
            />
          ))}
        </ScrollView>
      </View>

      <View className="flex-row items-center justify-between pb-3">
        <Text variant="caption">
          {count === 0 ? 'Nobody selected' : `${count} selected`}
          {people.data ? ` · ${people.data.total} match` : ''}
        </Text>
        {people.data && people.data.total > 0 && (
          <Pressable onPress={() => void selectAll()} accessibilityRole="button">
            <Text variant="caption" className="text-primary">
              Select all matching
            </Text>
          </Pressable>
        )}
      </View>

      {truncated && (
        <Text variant="caption" className="pb-3 text-danger">
          Only the first 200 were selected — that is as many as one code can be sent to at
          a time.
        </Text>
      )}

      {people.error && <ErrorNotice error={people.error} onRetry={() => void people.refetch()} />}

      <FlatList
        data={rows}
        keyExtractor={keyExtractor}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-8"
        onRefresh={() => void people.refetch()}
        refreshing={people.isFetching}
        ListEmptyComponent={
          people.isLoading ? null : (
            <Text variant="body" className="mt-8 text-center text-muted">
              Nobody matches that.
            </Text>
          )
        }
        renderItem={renderRow}
      />

      <View className="pb-2 pt-3">
        <Button label="Done" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}

/**
 * One cohort pill.
 *
 * Not `Segment`, which divides a row between two or three choices and sizes each with
 * `flex-1`. Six of these scroll sideways, so each has to be as wide as its own label —
 * with `flex-1` they arrive crushed against their text, which is what the first run on a
 * device showed.
 *
 * Both branches carry the same class keys and only the values differ. A key that appears
 * in one state and not the other stops NativeWind painting the subtree.
 */
function FilterChip({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      className={
        active ? 'rounded-full bg-primary px-4 py-2.5' : 'rounded-full bg-raised px-4 py-2.5'
      }
    >
      <Text variant="label" className={active ? 'text-inverse' : 'text-content'}>
        {label}
      </Text>
    </Pressable>
  );
}

/** When somebody was last around, which is what the cohorts are really about. */
function lastSeen(iso: string | null) {
  if (!iso) return 'never opened the app';
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days < 1) return 'active today';
  if (days === 1) return 'active yesterday';
  return `${days} days away`;
}

const PersonRow = memo(function PersonRow({
  person,
  selected,
  locked,
  onToggle,
}: {
  person: DirectoryUser;
  selected: boolean;
  locked: boolean;
  onToggle: (person: DirectoryUser) => void;
}) {
  const press = useCallback(() => onToggle(person), [onToggle, person]);

  return (
    <Pressable
      onPress={press}
      disabled={locked}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected, disabled: locked }}
      accessibilityLabel={person.username ?? person.userId}
    >
      {/* The selected state is a border colour rather than a class that appears and
          disappears: a class key that comes and goes stops NativeWind painting the
          subtree. Both branches carry the same keys. */}
      <Card className={selected ? 'mb-3 border border-primary' : 'mb-3 border border-transparent'}>
        <View className="flex-row items-center">
          <Avatar source={person.avatar} name={person.username ?? '?'} size={40} />
          <View className="ml-3 flex-1">
            <Text variant="bodyStrong" numberOfLines={1}>
              {person.username ?? 'No username'}
            </Text>
            {/* One line, truncated: a bot address is longer than the column and wrapping it
                pushed the row's trailing word into the middle of it. */}
            <Text variant="caption" numberOfLines={1}>
              {person.email ?? person.userId}
            </Text>
            <Text variant="caption" className="text-muted" numberOfLines={1}>
              {lastSeen(person.lastActiveAt)}
              {person.gold ? ' · Gold' : ''}
              {locked ? ' · already redeemed' : ''}
            </Text>
          </View>
          <Text
            variant="caption"
            className={selected ? 'ml-3 shrink-0 text-primary' : 'ml-3 shrink-0 text-muted'}
          >
            {selected ? 'Selected' : 'Tap to add'}
          </Text>
        </View>
      </Card>
    </Pressable>
  );
});
