import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Clock, Users } from 'lucide-react-native';
import { useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, View } from 'react-native';
import { lobbyApi, PAGE_SIZE } from '../../../src/api/lobby';
import type { Lobby, LobbyTone } from '../../../src/api/types';
import { LobbyCard } from '../../../src/lobby/LobbyCard';
import { TONES, ToneChip } from '../../../src/lobby/ToneChip';
import { useThemeColors } from '../../../src/theme';
import { Button, EmptyState, ErrorNotice, Icon, Screen, Text } from '../../../src/ui';
import { cn } from '../../../src/ui/cn';

/**
 * The lobby tab: what I am in, then what is open.
 *
 * Browsing is free for everyone — the paywall guards *creating* and lives on the create
 * screen, not here. A lobby list only Gold members could read would be an empty room
 * nobody pays to enter.
 */
export default function LobbyHome() {
  const router = useRouter();
  const colors = useThemeColors();
  const [tone, setTone] = useState<LobbyTone | null>(null);
  // Its own switch, not a fifth tone. "When it plays" and "what it plays like" are
  // different questions, and somebody wanting a competitive game *right now* should be
  // able to ask both at once.
  const [startingSoon, setStartingSoon] = useState(false);

  const mine = useQuery({ queryKey: ['my-lobbies'], queryFn: lobbyApi.mine });

  const feed = useInfiniteQuery({
    queryKey: ['lobbies', tone, startingSoon],
    queryFn: ({ pageParam }) =>
      lobbyApi.browse(pageParam, undefined, tone ?? undefined, startingSoon),
    initialPageParam: 0,
    // No total in the response; a short page is the end-of-list signal.
    getNextPageParam: (last, all) => (last.length < PAGE_SIZE ? undefined : all.length),
  });

  const myLobbies = mine.data ?? [];

  /**
   * My own lobbies are pinned above; repeating them in the feed would list them twice.
   *
   * Through a `Set` and a memo. This is an infinite query, so `pages.flat()` grows with
   * every page fetched, and the previous `myLobbies.some(...)` inside the filter made the
   * whole thing O(pages × mine) — recomputed on every render of a screen that also owns
   * two chip rows and a pull-to-refresh.
   */
  const open = useMemo(() => {
    const mineIds = new Set(myLobbies.map((lobby) => lobby.id));
    return (feed.data?.pages.flat() ?? []).filter((lobby) => !mineIds.has(lobby.id));
  }, [feed.data?.pages, myLobbies]);

  // One live lobby per owner is the rule the backend enforces (LOBBY_LIMIT_REACHED).
  // Knowing it here too means the button can say so before somebody fills in a form
  // that was always going to be refused.
  const ownsLive = myLobbies.some(
    (lobby) => lobby.myStatus === 'OWNER' && (lobby.status === 'OPEN' || lobby.status === 'LOCKED'),
  );

  const keyExtractor = useCallback((lobby: Lobby) => lobby.id, []);
  const renderLobby = useCallback(
    ({ item }: { item: Lobby }) => <LobbyCard lobby={item} />,
    [],
  );

  return (
    <Screen edges={['top']} padded={false}>
      <FlatList
        data={open}
        keyExtractor={keyExtractor}
        contentContainerClassName="gap-3 px-6 pb-8"
        showsVerticalScrollIndicator={false}
        onEndReachedThreshold={0.5}
        onEndReached={() => {
          if (feed.hasNextPage && !feed.isFetchingNextPage) void feed.fetchNextPage();
        }}
        refreshing={feed.isRefetching && !feed.isFetchingNextPage}
        onRefresh={() => {
          void feed.refetch();
          void mine.refetch();
        }}
        renderItem={renderLobby}
        ListHeaderComponent={
          <View className="gap-5 pb-2">
            <View className="flex-row items-end justify-between pt-8">
              <View className="gap-1">
                <Text variant="overline">LOBBY</Text>
                <Text variant="title">Find your team</Text>
              </View>
            </View>

            <View className="gap-2">
              <Button
                label="Open a lobby"
                disabled={ownsLive}
                onPress={() => router.push('/lobby/create' as never)}
              />
              {ownsLive && (
                <Text variant="caption" className="text-center">
                  You have a lobby open. End or cancel it to start another.
                </Text>
              )}
            </View>

            {myLobbies.length > 0 && (
              <View className="gap-2">
                <Text variant="overline">
                  {myLobbies.length === 1 ? 'YOUR LOBBY' : 'YOUR LOBBIES'}
                </Text>
                {myLobbies.map((lobby) => (
                  <LobbyCard key={lobby.id} lobby={lobby} />
                ))}
              </View>
            )}

            <View className="gap-2">
              <Text variant="overline">OPEN LOBBIES</Text>
              <View className="flex-row flex-wrap items-center gap-2">
                {/* First, and set apart, because it filters a different thing: the tone
                    chips are one-of-four, this one is on or off alongside them. */}
                <StartingSoonChip
                  active={startingSoon}
                  onPress={() => setStartingSoon(!startingSoon)}
                />
                <View className="h-6 w-px bg-line" />
                {TONES.map((t) => (
                  <ToneChip
                    key={t.value}
                    tone={t.value}
                    active={tone === t.value}
                    // Tapping the active chip clears the filter — four chips and an
                    // implicit "all" beats a fifth chip saying so.
                    onPress={() => setTone(tone === t.value ? null : t.value)}
                  />
                ))}
              </View>
            </View>

            {feed.isPending && <ActivityIndicator color={colors.primary} className="mt-4" />}
            {feed.error && <ErrorNotice error={feed.error} onRetry={() => feed.refetch()} />}
            {mine.error && <ErrorNotice error={mine.error} onRetry={() => mine.refetch()} />}
          </View>
        }
        ListEmptyComponent={
          feed.isPending || feed.error ? null : (
            <EmptyState
              icon={Users}
              title={emptyTitle(startingSoon, tone)}
              blurb={
                startingSoon
                  ? 'Nothing kicking off in the next 15 minutes. Turn off Now to see what is planned later.'
                  : tone
                    ? 'Nothing with that vibe at the moment. Try another filter, or open your own.'
                    : 'Open one, set a time, and choose who joins you.'
              }
            />
          )
        }
      />
    </Screen>
  );
}

function emptyTitle(startingSoon: boolean, tone: LobbyTone | null): string {
  if (startingSoon) return 'Nothing starting right now';
  if (tone) return 'Nothing with that vibe';
  return 'No open lobbies right now';
}

/**
 * "Starting now" — lobbies due to begin within the next quarter of an hour.
 *
 * A toggle rather than one of the tone chips: it answers *when*, they answer *what
 * kind*, and the two combine. The clock glyph is what tells them apart at a glance,
 * along with the rule separating it from the group.
 */
function StartingSoonChip({ active, onPress }: { active: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel="Starting within 15 minutes"
      className={cn(
        'flex-row items-center gap-1.5 rounded-full px-3.5 py-2',
        active ? 'bg-primary' : 'bg-raised',
      )}
    >
      <Icon as={Clock} size={14} tone={active ? 'inverse' : 'content'} />
      <Text variant="label" className={active ? 'text-white' : 'text-content'}>
        Now
      </Text>
    </Pressable>
  );
}
