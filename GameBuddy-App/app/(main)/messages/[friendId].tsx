import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { memo, useCallback, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, View } from 'react-native';
import { profileApi } from '../../../src/api/catalogue';
import { chatApi } from '../../../src/api/chat';
import { socialApi } from '../../../src/api/social';
import type { Conversation } from '../../../src/api/types';
import { useConversation } from '../../../src/chat/useConversation';
import type { Dictionary } from '../../../src/i18n/dictionaries/en';
import { useT } from '../../../src/i18n/useT';
import { useThemeColors } from '../../../src/theme';
import { Avatar, cn, ErrorNotice, Screen, Text, TextField } from '../../../src/ui';

export default function Chat() {
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const { friendId, username: passedUsername } = useLocalSearchParams<{
    friendId: string;
    username?: string;
  }>();

  /**
   * The name in the header, resolved rather than assumed.
   *
   * The param is an optimisation — the inbox already knows the name, so passing it paints
   * the header before anything loads. It is not a source of truth, and treating it as one
   * meant every entry point that *cannot* supply it showed "Conversation" with a "?"
   * avatar, permanently. Opening a chat from a push notification did exactly that, which
   * is the one route where you most need to know who is talking to you.
   *
   * Only fetched when the param is missing, so the common path still costs no request.
   * Shares the `['gamer', id]` key with the profile screen this header opens, so arriving
   * there is already warm.
   */
  const profile = useQuery({
    queryKey: ['gamer', friendId],
    queryFn: () => profileApi.byId(friendId),
    enabled: !passedUsername,
  });
  const username = passedUsername ?? profile.data?.username ?? undefined;

  const chat = useConversation(friendId);
  const [draft, setDraft] = useState('');
  const listRef = useRef<FlatList<Conversation>>(null);

  const report = useMutation({
    mutationFn: (messageId: string) => chatApi.report(messageId),
  });

  function submit() {
    const text = draft.trim();
    if (!text || chat.sending) return;
    // Cleared straight away. The send is an ordinary request that either succeeds or
    // reports why; leaving the text sitting there would suggest it had not been taken.
    setDraft('');
    chat.send(text);
  }

  // Stable list callbacks, so the memo on each bubble can bail out. Without them a
  // presence or typing frame — which arrive constantly over the socket — re-rendered
  // every message in the thread.
  const keyExtractor = useCallback((m: Conversation) => m.id, []);
  const scrollToEnd = useCallback(
    () => listRef.current?.scrollToEnd({ animated: false }),
    [],
  );
  const onReport = useCallback((id: string) => report.mutate(id), [report]);
  const renderBubble = useCallback(
    ({ item }: { item: Conversation }) => (
      <Bubble message={item} mine={item.sender === chat.myId} onReport={onReport} />
    ),
    [chat.myId, onReport],
  );

  return (
    // No bottom edge: this sits inside the Messages tab now, and the tab bar already
    // covers the safe area. Claiming it here as well left a strip of empty canvas
    // between the compose box and the bar.
    <Screen edges={['top']} padded={false}>
      <View className="flex-row items-center gap-3 border-b border-line px-4 pb-3 pt-2">
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel={t.common.back}
          hitSlop={12}
          className="h-10 w-10 items-center justify-center active:opacity-60"
        >
          <View className="h-2.5 w-2.5 rotate-45 border-b-2 border-l-2 border-content" />
        </Pressable>

        {/* The header opens their profile. It is where anybody looks for "who is this",
            and it is the only route to blocking or reporting them from a conversation —
            which is exactly the conversation you would want those in. */}
        <Pressable
          onPress={() =>
            router.push({
              pathname: '/messages/gamer/[userId]',
              params: { userId: friendId, username: username ?? '' },
            } as never)
          }
          accessibilityRole="button"
          accessibilityLabel={t.messages.viewProfileA11y(username ?? t.messages.thisGamer)}
          className="flex-1 flex-row items-center gap-3 active:opacity-70"
        >
          <Avatar source={null} name={username ?? '?'} colorSeed={friendId} size={36} />

          <View className="flex-1">
            <Text variant="bodyStrong">{username ?? t.messages.conversation}</Text>
            <StatusLabel status={chat.status} presence={chat.presence} isTyping={chat.isTyping} />
          </View>
        </Pressable>

        <FriendAction userId={friendId} />
      </View>

      {chat.isLoading && (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color={colors.primary} />
        </View>
      )}

      {chat.error && (
        <View className="p-4">
          <ErrorNotice error={chat.error} onRetry={() => void chat.refetch()} />
        </View>
      )}

      {!chat.isLoading && !chat.error && (
        <FlatList
          ref={listRef}
          data={chat.messages}
          keyExtractor={keyExtractor}
          contentContainerClassName="gap-2 p-4"
          onContentSizeChange={scrollToEnd}
          ListEmptyComponent={
            <View className="items-center gap-1 py-10">
              <Text variant="bodyStrong">{t.messages.noMessagesYet}</Text>
              <Text variant="caption" className="text-center">
                {t.messages.emptyBlurb}
              </Text>
            </View>
          }
          renderItem={renderBubble}
        />
      )}

      {!!chat.sendError && (
        <View className="px-4 pb-2">
          {/* The backend re-checks match tier, blocking and age band on *every*
              message, so this is where "you can no longer message this person"
              surfaces — not only at match time. */}
          <ErrorNotice error={chat.sendError} />
        </View>
      )}

      {report.error && (
        <View className="px-4 pb-2">
          <ErrorNotice error={report.error} />
        </View>
      )}
      {report.isSuccess && (
        <View className="px-4 pb-2">
          <Text variant="caption">{t.messages.reported}</Text>
        </View>
      )}

      <View className="flex-row items-end gap-2 border-t border-line p-3">
        <View className="flex-1">
          <TextField
            value={draft}
            onChangeText={(text) => {
              setDraft(text);
              // Only while there is something to type. Clearing the box — including the
              // clear that happens on send — is not typing, and would otherwise put the
              // indicator up on the other side just as the message landed.
              if (text.length > 0) chat.notifyTyping();
            }}
            placeholder={t.messages.placeholder}
            multiline
            maxLength={2000}
            onSubmitEditing={submit}
            returnKeyType="send"
          />
        </View>
        <Pressable
          onPress={submit}
          disabled={draft.trim().length === 0 || chat.sending}
          accessibilityRole="button"
          accessibilityLabel={t.common.send}
          className={cn(
            'h-touch w-touch items-center justify-center rounded-full bg-primary active:opacity-80',
            (draft.trim().length === 0 || chat.sending) && 'opacity-40',
          )}
        >
          {/* A paper-plane needs an icon set; an arrow from two borders does not. */}
          <View className="h-3 w-3 rotate-45 border-r-2 border-t-2 border-white" />
        </Pressable>
      </View>
    </Screen>
  );
}

/**
 * The friend control in the chat header.
 *
 * Four states, and which one shows is the whole point — a single "add friend" button
 * that is always tappable asks somebody to find out by tapping it:
 *
 * - already friends → a filled mark, no action. There is nothing to send.
 * - they asked you  → accepting is the useful action, and it is one tap.
 * - you asked them  → waiting. Tapping again would only return ALREADY_SENT_REQUEST.
 * - neither         → send the request.
 *
 * Reads three small lists that other screens already keep warm, so opening a chat does
 * not usually cost a request. Renders nothing at all until they have loaded, because a
 * plus that turns into a tick a moment later invites the tap it then ignores.
 */
function FriendAction({ userId }: { userId: string }) {
  const colors = useThemeColors();
  const t = useT();
  const queryClient = useQueryClient();

  const friends = useQuery({ queryKey: ['friends'], queryFn: socialApi.friends });
  const incoming = useQuery({
    queryKey: ['friendRequests'],
    queryFn: socialApi.pendingRequests,
  });
  const outgoing = useQuery({ queryKey: ['sentRequests'], queryFn: socialApi.sentRequests });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['friends'] });
    void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
    void queryClient.invalidateQueries({ queryKey: ['sentRequests'] });
    // The messages list splits on friendship, so this row moves between its sections.
    void queryClient.invalidateQueries({ queryKey: ['inbox'] });
  };

  const send = useMutation({
    mutationFn: () => socialApi.sendRequest(userId),
    onSuccess: refresh,
  });
  const accept = useMutation({
    mutationFn: () => socialApi.accept(userId),
    onSuccess: refresh,
  });

  /**
   * Re-read the three lists when the chat is opened.
   *
   * Queries are stale-for-five-minutes by default, and these change because of what
   * somebody *else* did. Without this, a request that arrives while the app is open
   * leaves the plus showing for up to five minutes — and tapping it then sends a second
   * request in the opposite direction, which leaves two pending rows that nothing
   * resolves. Caught exactly that way while testing.
   */
  useFocusEffect(
    useCallback(() => {
      void friends.refetch();
      void incoming.refetch();
      void outgoing.refetch();
      // Refetch functions are stable, so this runs once per focus.
    }, [friends.refetch, incoming.refetch, outgoing.refetch]),
  );

  if (friends.isPending || incoming.isPending || outgoing.isPending) return null;

  const isFriend = (friends.data ?? []).some((f) => f.userId === userId);
  const theyAsked = (incoming.data ?? []).some((f) => f.userId === userId);
  const youAsked = (outgoing.data ?? []).some((f) => f.userId === userId);
  const busy = send.isPending || accept.isPending;

  if (isFriend) {
    return (
      <View
        className="h-10 w-10 items-center justify-center"
        accessibilityLabel={t.messages.alreadyFriends}
      >
        <PersonIcon color={colors.primary} badge="check" background={colors.canvas} />
      </View>
    );
  }

  return (
    <Pressable
      onPress={() => (theyAsked ? accept.mutate() : youAsked ? undefined : send.mutate())}
      disabled={busy || youAsked}
      accessibilityRole="button"
      accessibilityLabel={
        theyAsked
          ? t.messages.acceptRequest
          : youAsked
            ? t.messages.requestSent
            : t.messages.sendRequest
      }
      hitSlop={8}
      className="h-10 w-10 items-center justify-center active:opacity-60"
    >
      {busy ? (
        <ActivityIndicator color={colors.primary} />
      ) : (
        <PersonIcon
          // Waiting reads as inert rather than disabled-looking: same glyph, muted, with
          // the plus replaced by a dot so it does not look like it can still be pressed.
          color={youAsked ? colors.muted : colors.primary}
          badge={theyAsked ? 'check' : youAsked ? 'dot' : 'plus'}
          background={colors.canvas}
        />
      )}
    </Pressable>
  );
}

/**
 * A head and shoulders with a small mark at the corner.
 *
 * Drawn from views, the same way the tab bar's icons are — no icon font and no SVG
 * library is bundled, and this is a few rectangles and a circle.
 *
 * The mark sits on a disc of the page colour. Without it the tick ran into the shoulder
 * line and the two read as one shape; a knockout is what every icon set does here and
 * it costs one view.
 */
function PersonIcon({
  color,
  badge,
  background,
}: {
  color: string;
  badge: 'plus' | 'check' | 'dot';
  background: string;
}) {
  return (
    <View className="h-6 w-6 items-center justify-center">
      <View className="items-center" style={{ marginRight: 5, marginTop: -3 }}>
        <View
          className="rounded-full border-2"
          style={{ width: 9, height: 9, borderColor: color }}
        />
        <View
          className="rounded-t-full border-2 border-b-0"
          style={{ width: 17, height: 8, marginTop: 2, borderColor: color }}
        />
      </View>

      <View
        className="absolute items-center justify-center rounded-full"
        style={{ width: 14, height: 14, bottom: -1, right: -1, backgroundColor: background }}
      >
        {badge === 'plus' && (
          <View className="items-center justify-center" style={{ width: 10, height: 10 }}>
            <View
              className="absolute"
              style={{ width: 10, height: 2, backgroundColor: color }}
            />
            <View
              className="absolute"
              style={{ width: 2, height: 10, backgroundColor: color }}
            />
          </View>
        )}
        {badge === 'check' && (
          <View
            className="border-b-2 border-l-2 border-r-0 border-t-0"
            style={{
              width: 6,
              height: 9,
              marginTop: -2,
              borderColor: color,
              transform: [{ rotate: '-45deg' }],
            }}
          />
        )}
        {badge === 'dot' && (
          <View
            className="rounded-full"
            style={{ width: 6, height: 6, backgroundColor: color }}
          />
        )}
      </View>
    </View>
  );
}

/**
 * The one line under the name: what the other person is doing, or why we cannot say.
 *
 * **Our own connection comes first, and that ordering is the important part.** Presence is
 * something the server pushes to us; if our socket is down we are not being told about
 * changes, so the last value we saw is only a memory. Showing a confident "Online" over a
 * dead connection is worse than admitting the connection is dead — it is the difference
 * between stale and wrong.
 *
 * Below that, typing beats online because it is strictly more specific: somebody typing is
 * obviously online, and saying so instead would be dropping information.
 */
function StatusLabel({
  status,
  presence,
  isTyping,
}: {
  status: ReturnType<typeof useConversation>['status'];
  presence: ReturnType<typeof useConversation>['presence'];
  isTyping: boolean;
}) {
  const t = useT();
  if (status !== 'connected') {
    const label =
      status === 'connecting'
        ? t.messages.connecting
        : status === 'reconnecting'
          ? t.messages.reconnecting
          : t.messages.offline;
    return (
      <Text variant="caption" className={status === 'idle' ? 'text-danger' : 'text-muted'}>
        {label}
      </Text>
    );
  }

  if (isTyping) {
    return (
      <Text variant="caption" className="text-primary">
        {t.messages.typing}
      </Text>
    );
  }

  // Not yet answered. Deliberately blank rather than "Offline": we do not know, and a
  // guess that resolves a moment later reads as the other person having just left.
  if (!presence) return null;

  if (presence.online) {
    return (
      <Text variant="caption" className="text-primary">
        {t.messages.online}
      </Text>
    );
  }

  return (
    <Text variant="caption" className="text-muted">
      {presence.lastSeenAt
        ? t.messages.lastSeen(timeAgo(presence.lastSeenAt, t))
        : t.messages.offline}
    </Text>
  );
}

/**
 * "3m ago", coarsely.
 *
 * Rounded down and capped at a day, because presence is only interesting near the present:
 * the useful distinction is "just missed them" against "not around", and past a day the
 * exact figure says nothing a plain "Offline" would not.
 */
function timeAgo(iso: string, t: Dictionary): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return t.messages.justNow;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return t.messages.minutesAgo(minutes);
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t.messages.hoursAgo(hours);
  return t.messages.aWhileAgo;
}

const Bubble = memo(function Bubble({
  message,
  mine,
  onReport,
}: {
  message: Conversation;
  mine: boolean;
  onReport: (id: string) => void;
}) {
  const t = useT();
  const report = useCallback(() => onReport(message.id), [onReport, message.id]);

  return (
    <Pressable
      // Reporting is only offered on messages you received: the backend refuses a
      // report on your own with RECEIVER_IS_DIFFERENT (143).
      onLongPress={mine ? undefined : report}
      accessibilityHint={mine ? undefined : t.messages.reportHint}
      className={cn('max-w-[80%]', mine ? 'self-end' : 'self-start')}
    >
      <View
        className={cn(
          'rounded-card px-4 py-2.5',
          mine ? 'rounded-br-sm bg-primary' : 'rounded-bl-sm bg-raised',
        )}
      >
        <Text className={mine ? 'text-white' : 'text-content'}>{message.message}</Text>
      </View>
    </Pressable>
  );
});
