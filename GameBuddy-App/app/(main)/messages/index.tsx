import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useRouter } from 'expo-router';
import { ChevronDown, ChevronUp } from 'lucide-react-native';
import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, View } from 'react-native';
import { chatApi } from '../../../src/api/chat';
import { useChatSocketApi, useChatSocketStatus } from '../../../src/chat/ChatSocketProvider';
import { matchApi } from '../../../src/api/match';
import { socialApi } from '../../../src/api/social';
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
import { FriendRequestsSection } from '../../../src/social/FriendRequestsSection';
import { useThemeColors } from '../../../src/theme';
import { Card, ErrorNotice, FramedAvatar, Icon, Screen, Text } from '../../../src/ui';

/**
 * One person you can talk to, however you came to know them.
 *
 * The three sources — the inbox, your matches and your friends — overlap heavily and
 * describe the same people in three different shapes. Normalising once here means the
 * rows below take a single type and the two sections differ only in who lands in them.
 */
type Row = {
  userId: string;
  username: string;
  avatar: string | null;
  frame: string | null;
  lastMessage: string | null;
  unreadCount: number;
  /** False for someone you can message but never have. */
  hasConversation: boolean;
};

type SectionId = 'friends' | 'matches';

/**
 * The list, flattened.
 *
 * Two folding sections used to be two `.map()`s inside a ScrollView. Flattening them into
 * one tagged array is what lets a single `FlatList` own both — and it makes folding a
 * section a slice of this array rather than a branch in the tree, which is why the fold
 * state can stay out of the expensive merge below.
 */
type ListItem =
  | { kind: 'section'; id: SectionId; key: string; title: string; count: number; unread: number; open: boolean }
  | { kind: 'row'; key: string; row: Row }
  | { kind: 'empty'; key: string; text: string };

/**
 * Conversations, split into friends and matches.
 *
 * The two are different relationships and the app treats them that way everywhere else
 * — a match is somebody who said yes back, a friend is a match you then chose to keep —
 * so an inbox that mixes them loses the distinction exactly where it matters most.
 * Friends come first because it is the shorter and more deliberate list.
 *
 * Both sections fold. Matches is the one that grows without limit, and somebody with
 * forty of them should be able to put it away and still see their friends.
 *
 * **Virtualized, and the merge below is memoised.** Both matter here more than anywhere
 * else in the app: this screen polls every fifteen seconds, and it used to rebuild the
 * whole merge — a Set, two Maps, three loops and two filters — and re-render every row
 * four times a minute whether anything had changed or not.
 */
export default function Messages() {
  // The narrow hooks, not `useChatSocket()`: this screen cares whether the socket is up
  // and wants to be told about messages. It has no interest in who is online, and taking
  // the whole context would have it re-render on every presence frame in the app.
  const { onMessage } = useChatSocketApi();
  const { status: socketStatus } = useChatSocketStatus();
  const t = useT();
  const queryClient = useQueryClient();

  const inbox = useQuery({
    queryKey: ['inbox'],
    queryFn: chatApi.inbox,
    /*
     * A fallback, not the mechanism.
     *
     * This was an unconditional fifteen seconds, which is what made this screen the most
     * expensive one in the app: every tick refetched the inbox, re-ran the merge below and
     * re-rendered every row, four times a minute, whether or not anything had changed —
     * and the socket was delivering the same news live the whole time.
     *
     * Now the socket drives it (see the effect below) and the timer only runs when there
     * is no socket to drive it with.
     */
    refetchInterval: socketStatus === 'connected' ? false : 30_000,
  });

  /**
   * A message arriving anywhere moves this list, so refresh it when one does.
   *
   * `useConversation` already does this for the case where a chat is open; without it here
   * the inbox only learned about a new message from its own timer, which is precisely the
   * timer being removed above.
   */
  useEffect(
    () => onMessage(() => queryClient.invalidateQueries({ queryKey: ['inbox'] })),
    [onMessage, queryClient],
  );

  // Matches with no conversation yet: people you *can* message but never have. Without
  // this a new match looks like nothing happened until someone sends the first line.
  const matches = useQuery({ queryKey: ['matches'], queryFn: matchApi.matches });

  // Which of them are friends. Also the source of a friend's row if they are somehow in
  // neither of the other two lists, so a friend can never be missing from this screen.
  const friends = useQuery({ queryKey: ['friends'], queryFn: socialApi.friends });

  /**
   * Re-read the two lists that decide the split whenever the tab is opened.
   *
   * Only the inbox polls, because only the inbox changes on its own. These two change
   * because of something the gamer did — accepting a request on Profile, matching on
   * Home — and without this the row stays in the wrong section until the app restarts.
   * Which is worse than a stale timestamp: the whole point of the screen is which of
   * the two groups somebody is in.
   *
   * On focus rather than on a timer: this is not a race with anybody, it is catching up
   * with a change that has already happened.
   */
  useFocusEffect(
    useCallback(() => {
      void friends.refetch();
      void matches.refetch();
      // The inbox too, and for a different reason: coming back from a conversation, the
      // unread counts on this screen are the ones fetched before it was opened. The chat
      // zeroes its own row optimistically, but only a refetch confirms it — and only this
      // catches messages that arrived while the socket was down.
      void inbox.refetch();
      // Refetch functions are stable across renders, so this runs once per focus.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [friends.refetch, matches.refetch, inbox.refetch]),
  );

  /**
   * The merge, and the only expensive thing on this screen.
   *
   * Keyed on the three payloads alone — deliberately not on the fold state, so opening
   * or closing a section does not rebuild any of it. The payloads are pulled into locals
   * so the memo closes over exactly what its dependency list names; reading
   * `friends.data` inside while depending on `friends.data` made the compiler infer
   * `friends` and bail out of optimizing the whole screen.
   */
  const friendsData = friends.data;
  const matchesData = matches.data;
  const inboxData = inbox.data;
  const { friendRows, matchRows } = useMemo(() => {
    const friendIds = new Set((friendsData ?? []).map((friend) => friend.userId));

    // The inbox does not carry a worn frame; the other two do. Resolving it here keeps a
    // row looking like the same person they are on every other screen.
    const frames = new Map<string, string | null>();
    for (const match of matchesData ?? []) frames.set(match.userId, match.frame);
    for (const friend of friendsData ?? []) frames.set(friend.userId, friend.frame);

    const all: Row[] = [];
    const seen = new Set<string>();

    // The inbox first, in its own order — the server sorts it by most recent message, and
    // that ordering is what this screen is for.
    for (const entry of inboxData ?? []) {
      seen.add(entry.userId);
      all.push({
        userId: entry.userId,
        username: entry.username,
        avatar: entry.avatar,
        frame: frames.get(entry.userId) ?? null,
        lastMessage: entry.lastMessage,
        unreadCount: entry.unreadCount,
        hasConversation: true,
      });
    }

    for (const match of matchesData ?? []) {
      if (seen.has(match.userId)) continue;
      seen.add(match.userId);
      all.push({
        userId: match.userId,
        username: match.gamerUsername,
        avatar: match.avatar,
        frame: match.frame,
        lastMessage: null,
        unreadCount: 0,
        hasConversation: false,
      });
    }

    // A friend in neither list still belongs here. Friendship is granted on top of a
    // match so it should not happen — but "should not" is not a reason to drop somebody
    // from the one screen they are meant to be reachable on.
    for (const friend of friendsData ?? []) {
      if (seen.has(friend.userId)) continue;
      seen.add(friend.userId);
      all.push({
        userId: friend.userId,
        username: friend.username,
        avatar: friend.avatar,
        frame: friend.frame,
        lastMessage: null,
        unreadCount: 0,
        hasConversation: false,
      });
    }

    return {
      friendRows: all.filter((row) => friendIds.has(row.userId)),
      matchRows: all.filter((row) => !friendIds.has(row.userId)),
    };
  }, [inboxData, matchesData, friendsData]);

  const [open, setOpen] = useState<Record<SectionId, boolean>>({ friends: true, matches: true });

  const toggleSection = useCallback(
    (id: SectionId) => setOpen((current) => ({ ...current, [id]: !current[id] })),
    [],
  );

  const data = useMemo(
    () => [
      ...section('friends', t.messages.friends, friendRows, open.friends, t.messages.friendsEmpty),
      ...section('matches', t.messages.matches, matchRows, open.matches, t.messages.matchesEmpty),
    ],
    [friendRows, matchRows, open, t],
  );

  const router = useRouter();

  /**
   * One handler for the whole list rather than one `useRouter()` and one closure per row.
   * Both were per-row costs, and the closure was what stopped `ConversationRow`'s `memo`
   * from ever bailing out.
   */
  const openConversation = useCallback(
    (userId: string, username: string) => {
      router.push({
        pathname: '/messages/[friendId]',
        params: { friendId: userId, username },
      } as never);
    },
    [router],
  );

  const keyExtractor = useCallback((item: ListItem) => item.key, []);

  const renderItem = useCallback(
    ({ item }: { item: ListItem }) => {
      if (item.kind === 'section') {
        return (
          <SectionHeader
            id={item.id}
            title={item.title}
            count={item.count}
            unread={item.unread}
            open={item.open}
            onToggle={toggleSection}
          />
        );
      }
      if (item.kind === 'empty') {
        return (
          <Card>
            <Text variant="caption">{item.text}</Text>
          </Card>
        );
      }
      return <ConversationRow row={item.row} onOpen={openConversation} />;
    },
    [toggleSection, openConversation],
  );

  const loading = inbox.isPending || matches.isPending || friends.isPending;

  return (
    <Screen edges={['top']}>
      <FlatList
        className="flex-1"
        data={data}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        // An element of a module-scope component, never an inline arrow — an arrow is a
        // new component type every render and would remount the header each time.
        ListHeaderComponent={
          <ListHeader
            loading={loading}
            error={inbox.error}
            onRetry={inbox.refetch}
          />
        }
        contentContainerClassName="gap-2 pb-8"
        showsVerticalScrollIndicator={false}
        initialNumToRender={10}
        maxToRenderPerBatch={8}
        windowSize={7}
      />
    </Screen>
  );
}

/** One section's header, its rows, and its empty state, as flat list items. */
function section(
  id: SectionId,
  title: string,
  rows: Row[],
  open: boolean,
  empty: string,
): ListItem[] {
  const header: ListItem = {
    kind: 'section',
    id,
    key: `section:${id}`,
    title,
    count: rows.length,
    // On the header as well as on the rows, so folding a section away cannot hide the
    // fact that somebody is waiting for an answer.
    unread: rows.reduce((total, row) => total + row.unreadCount, 0),
    open,
  };

  if (!open) return [header];
  if (rows.length === 0) return [header, { kind: 'empty', key: `empty:${id}`, text: empty }];
  return [header, ...rows.map((row): ListItem => ({ kind: 'row', key: row.userId, row }))];
}

function ListHeader({
  loading,
  error,
  onRetry,
}: {
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}) {
  const colors = useThemeColors();
  const t = useT();
  const upper = useUpper();

  return (
    <View className="gap-2 pb-2">
      <View className="gap-1 pb-4 pt-8">
        <Text variant="overline">{upper(t.messages.header)}</Text>
        <Text variant="title">{t.messages.title}</Text>
      </View>

      {loading && <ActivityIndicator color={colors.primary} />}
      {!!error && <ErrorNotice error={error} onRetry={onRetry} />}

      {/* Above both sections: somebody asking to be your friend is waiting on an answer,
          and the two lists below are not. It draws nothing when nothing is pending. */}
      <FriendRequestsSection />
    </View>
  );
}

const SectionHeader = memo(function SectionHeader({
  id,
  title,
  count,
  unread,
  open,
  onToggle,
}: {
  id: SectionId;
  title: string;
  count: number;
  unread: number;
  open: boolean;
  onToggle: (id: SectionId) => void;
}) {
  const t = useT();
  const upper = useUpper();
  const onPress = useCallback(() => onToggle(id), [onToggle, id]);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ expanded: open }}
      accessibilityLabel={t.messages.sectionA11y(title, count)}
      className="flex-row items-center gap-2 pb-1 pt-4 active:opacity-70"
    >
      <Text variant="overline">
        {upper(title)} · {count}
      </Text>

      {unread > 0 && (
        <View className="min-w-[20px] items-center rounded-full bg-primary px-1.5">
          <Text variant="caption" className="text-white">
            {unread}
          </Text>
        </View>
      )}

      <View className="flex-1" />

      {/* A real chevron, not two borders on a rotated box.

          The hand-drawn version was an 8×8 square turned 45°, and a square turned 45° is
          wider than it was: its diagonal is 8√2 ≈ 11.3dp, while layout still reserved the
          8dp it started with. The tip therefore hung ~1.7dp outside its own box on every
          side, and since this is the last child in the row that put the right-hand point
          past the row's edge — which is what a tester saw and reported as an arrow cut
          off at the screen edge.

          Lucide measures honestly: the glyph is drawn inside the box it asks for, so
          nothing overflows and the two states are the same size. Swapping the glyph rather
          than rotating one also sidesteps the NativeWind rule that a `rotate-*` utility
          present in one state and absent in the other stops the subtree painting — see
          `src/ui/hairline.ts`. */}
      <Icon as={open ? ChevronUp : ChevronDown} size={16} tone="muted" />
    </Pressable>
  );
});

const ConversationRow = memo(function ConversationRow({
  row,
  onOpen,
}: {
  row: Row;
  onOpen: (userId: string, username: string) => void;
}) {
  const t = useT();
  const onPress = useCallback(() => onOpen(row.userId, row.username), [onOpen, row.userId, row.username]);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={
        row.hasConversation
          ? t.messages.conversationWith(row.username)
          : t.messages.startConversationWith(row.username)
      }
      className="active:opacity-70"
    >
      <Card className="flex-row items-center gap-3 p-4">
        <FramedAvatar
          frame={row.frame}
          source={row.avatar}
          name={row.username}
          colorSeed={row.userId}
          size={48}
        />
        <View className="flex-1 gap-0.5">
          <Text variant="bodyStrong">{row.username}</Text>
          <Text variant="caption" numberOfLines={1}>
            {row.hasConversation
              ? (row.lastMessage ?? t.messages.noMessagesYet)
              : t.messages.sayFirst}
          </Text>
        </View>

        {row.unreadCount > 0 && (
          <View className="min-w-[24px] items-center rounded-full bg-primary px-2 py-1">
            <Text variant="caption" className="text-white">
              {row.unreadCount}
            </Text>
          </View>
        )}
      </Card>
    </Pressable>
  );
});
