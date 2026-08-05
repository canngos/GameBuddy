import { useQuery } from '@tanstack/react-query';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { chatApi } from '../../../src/api/chat';
import { matchApi } from '../../../src/api/match';
import { socialApi } from '../../../src/api/social';
import { useThemeColors } from '../../../src/theme';
import { Card, ErrorNotice, FramedAvatar, Screen, Text } from '../../../src/ui';

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
 */
export default function Messages() {
  const colors = useThemeColors();
  const [openFriends, setOpenFriends] = useState(true);
  const [openMatches, setOpenMatches] = useState(true);

  const inbox = useQuery({
    queryKey: ['inbox'],
    queryFn: chatApi.inbox,
    // Conversations move while this screen is not open and there is no push yet, so
    // it polls. Cheap: the inbox is a handful of rows.
    refetchInterval: 15_000,
  });

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
      // Refetch functions are stable across renders, so this runs once per focus.
    }, [friends.refetch, matches.refetch]),
  );

  const friendIds = new Set((friends.data ?? []).map((friend) => friend.userId));

  // The inbox does not carry a worn frame; the other two do. Resolving it here keeps a
  // row looking like the same person they are on every other screen.
  const frames = new Map<string, string | null>();
  for (const match of matches.data ?? []) frames.set(match.userId, match.frame);
  for (const friend of friends.data ?? []) frames.set(friend.userId, friend.frame);

  const rows: Row[] = [];
  const seen = new Set<string>();

  // The inbox first, in its own order — the server sorts it by most recent message, and
  // that ordering is what this screen is for.
  for (const entry of inbox.data ?? []) {
    seen.add(entry.userId);
    rows.push({
      userId: entry.userId,
      username: entry.username,
      avatar: entry.avatar,
      frame: frames.get(entry.userId) ?? null,
      lastMessage: entry.lastMessage,
      unreadCount: entry.unreadCount,
      hasConversation: true,
    });
  }

  for (const match of matches.data ?? []) {
    if (seen.has(match.userId)) continue;
    seen.add(match.userId);
    rows.push({
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
  for (const friend of friends.data ?? []) {
    if (seen.has(friend.userId)) continue;
    seen.add(friend.userId);
    rows.push({
      userId: friend.userId,
      username: friend.username,
      avatar: friend.avatar,
      frame: friend.frame,
      lastMessage: null,
      unreadCount: 0,
      hasConversation: false,
    });
  }

  const friendRows = rows.filter((row) => friendIds.has(row.userId));
  const matchRows = rows.filter((row) => !friendIds.has(row.userId));
  const loading = inbox.isPending || matches.isPending || friends.isPending;

  return (
    <Screen scroll edges={['top']}>
      <View className="gap-1 pb-6 pt-8">
        <Text variant="overline">MESSAGES</Text>
        <Text variant="title">Your conversations</Text>
      </View>

      {loading && <ActivityIndicator color={colors.brand} />}
      {inbox.error && <ErrorNotice error={inbox.error} onRetry={() => inbox.refetch()} />}

      <View className="gap-4 pb-4">
        <Section
          title="FRIENDS"
          rows={friendRows}
          open={openFriends}
          onToggle={() => setOpenFriends((value) => !value)}
          empty="Nobody yet. You can add a friend once you have matched with them."
        />

        <Section
          title="MATCHES"
          rows={matchRows}
          open={openMatches}
          onToggle={() => setOpenMatches((value) => !value)}
          empty="No matches yet. Swipe on the Home tab to find someone."
        />
      </View>
    </Screen>
  );
}

/**
 * One collapsible group.
 *
 * The unread count sits on the header as well as on the rows, so folding a section away
 * cannot hide the fact that somebody is waiting for an answer.
 */
function Section({
  title,
  rows,
  open,
  onToggle,
  empty,
}: {
  title: string;
  rows: Row[];
  open: boolean;
  onToggle: () => void;
  empty: string;
}) {
  const unread = rows.reduce((total, row) => total + row.unreadCount, 0);

  return (
    <View className="gap-2">
      <Pressable
        onPress={onToggle}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={`${title}, ${rows.length}`}
        className="flex-row items-center gap-2 py-2 active:opacity-70"
      >
        <Text variant="overline">
          {title} · {rows.length}
        </Text>

        {unread > 0 && (
          <View className="min-w-[20px] items-center rounded-full bg-brand px-1.5">
            <Text variant="caption" className="text-white">
              {unread}
            </Text>
          </View>
        )}

        <View className="flex-1" />

        {/* Rotated through `style`, not a class. A `rotate-*` utility present in one
            state and absent in the other stops NativeWind painting the subtree — see
            `src/ui/hairline.ts`. A transform value is just a number. */}
        <View
          className="h-2 w-2 border-b-2 border-r-2 border-muted"
          style={{ transform: [{ rotate: open ? '45deg' : '-45deg' }] }}
        />
      </Pressable>

      {open &&
        (rows.length === 0 ? (
          <Card>
            <Text variant="caption">{empty}</Text>
          </Card>
        ) : (
          rows.map((row) => <ConversationRow key={row.userId} row={row} />)
        ))}
    </View>
  );
}

function ConversationRow({ row }: { row: Row }) {
  const router = useRouter();

  return (
    <Pressable
      onPress={() =>
        router.push({
          pathname: '/messages/[friendId]',
          params: { friendId: row.userId, username: row.username },
        } as never)
      }
      accessibilityRole="button"
      accessibilityLabel={
        row.hasConversation
          ? `Conversation with ${row.username}`
          : `Start a conversation with ${row.username}`
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
            {row.hasConversation ? (row.lastMessage ?? 'No messages yet') : 'Say something first'}
          </Text>
        </View>

        {row.unreadCount > 0 && (
          <View className="min-w-[24px] items-center rounded-full bg-brand px-2 py-1">
            <Text variant="caption" className="text-white">
              {row.unreadCount}
            </Text>
          </View>
        )}
      </Card>
    </Pressable>
  );
}
