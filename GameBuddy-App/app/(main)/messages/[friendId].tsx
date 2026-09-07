import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { memo, useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, View } from 'react-native';
import { profileApi } from '../../../src/api/catalogue';
import { chatApi } from '../../../src/api/chat';
import { socialApi } from '../../../src/api/social';
import type {
  Candidate,
  Conversation,
  GamerSummary,
  InboxEntry,
} from '../../../src/api/types';
import { useCoachmarkTarget } from '../../../src/hints/coachmark';
import { useConversation } from '../../../src/chat/useConversation';
import type { Dictionary } from '../../../src/i18n/dictionaries/en';
import { useT } from '../../../src/i18n/useT';
import type { QueryKeyRoot } from '../../../src/query/keys';
import { useThemeColors } from '../../../src/theme';
import {
  ActionSheet,
  cn,
  ConfirmDialog,
  ErrorNotice,
  FramedAvatar,
  Screen,
  Text,
  TextField,
  type ConfirmRequest,
} from '../../../src/ui';

export default function Chat() {
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const { friendId, username: passedUsername } = useLocalSearchParams<{
    friendId: string;
    username?: string;
  }>();

  const queryClient = useQueryClient();

  /**
   * What the screen this was opened from already knows about the other person.
   *
   * The inbox draws this exact face one tap away, out of three caches that are still in
   * memory when it pushes this screen — so the header reads them rather than asking the
   * server to describe somebody it was just shown. The three are the ones the inbox
   * itself merges, and they are read in its order for the same reason: `['inbox']` is
   * authoritative on the name and the picture, and carries no worn frame, so the frame
   * comes from `['matches']` or `['friends']`, which do.
   *
   * A plain read rather than three more `useQuery` subscriptions. This is wanted once, at
   * mount, and by then it is either there or it never will be — whatever route filled
   * these caches did so before navigating here. So a miss is a real miss, and it falls
   * through to {@link profile} below, which *is* reactive.
   */
  const known = useMemo(() => {
    const roots = ['inbox', 'matches', 'friends'] satisfies QueryKeyRoot[];
    const entry = queryClient
      .getQueryData<InboxEntry[]>([roots[0]])
      ?.find((row) => row.userId === friendId);
    const match = queryClient
      .getQueryData<Candidate[]>([roots[1]])
      ?.find((row) => row.userId === friendId);
    const friend = queryClient
      .getQueryData<GamerSummary[]>([roots[2]])
      ?.find((row) => row.userId === friendId);

    // Absent from all three is the answer, not an empty one: it means this screen was
    // reached without passing through the inbox, and only then is a request warranted.
    if (!entry && !match && !friend) return null;

    return {
      username: entry?.username ?? match?.gamerUsername ?? friend?.username ?? null,
      // `??` and not `||`, throughout: null here means "has no avatar", which is a fact
      // worth keeping — falling through to the next list would not improve on it, and
      // the monogram is the right answer for somebody who has not set one.
      avatar: entry?.avatar ?? match?.avatar ?? friend?.avatar ?? null,
      frame: match?.frame ?? friend?.frame ?? null,
    };
  }, [queryClient, friendId]);

  /**
   * The fallback for the entry points that cannot have those caches warm.
   *
   * Two of the three routes here are exactly that: a MESSAGE push on a cold start has no
   * cache at all, and a fresh match is somebody `['matches']` was fetched before. Both
   * need the round trip, and both are rare — opening a conversation from the inbox, which
   * is how this screen is nearly always reached, still costs nothing.
   *
   * The `username` param is a separate optimisation and is kept: it paints the name before
   * anything at all has loaded. It is not a source of truth, though, and treating it as
   * one is what caused this. Gating the fetch on it meant the ordinary route learned the
   * name and nothing else — no avatar to draw, so the header hardcoded a blank one and
   * showed a monogram where the row it was opened from showed a face. Every entry point
   * without the param, meanwhile, showed "Conversation" and a "?" permanently.
   *
   * Shares the `['gamer', id]` key with the profile this header opens, so if it does run,
   * tapping through is already warm.
   */
  const profile = useQuery({
    queryKey: ['gamer', friendId],
    queryFn: () => profileApi.byId(friendId),
    enabled: !known,
  });

  const username = passedUsername ?? known?.username ?? profile.data?.username ?? undefined;
  const avatar = known?.avatar ?? profile.data?.avatar ?? null;
  const frame = known?.frame ?? profile.data?.frame ?? null;

  const chat = useConversation(friendId);
  const [draft, setDraft] = useState('');
  // Both belong to the header's friend control, but they are owned here: an ActionSheet
  // only covers the screen when it is the last child of one.
  const [friendMenuOpen, setFriendMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState<ConfirmRequest | null>(null);

  const report = useMutation({
    mutationFn: (messageId: string) => chatApi.report(messageId),
  });

  // What to actually say, on the first conversation somebody opens. Held back while the
  // thread is still loading, and once there is a draft: somebody who is already typing
  // has worked out what the box is for.
  const { attach: attachComposerHint } = useCoachmarkTarget(
    'chat.composer',
    !chat.isLoading && !chat.error && draft.length === 0,
  );

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
  // The conversation arrives oldest first, which is the order it is written and read in.
  // The list below is inverted, so it wants the other one.
  const newestFirst = useMemo(() => [...chat.messages].reverse(), [chat.messages]);
  // Depends on `mutate`, not the mutation: react-query's mutation *object* is a fresh literal every render; `mutate` is its stable part.
  // With [report] here, every keystroke in the compose box re-rendered every bubble.
  const { mutate: reportMutate } = report;
  const onReport = useCallback((id: string) => reportMutate(id), [reportMutate]);
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
          {/* Framed, like the inbox row this was opened from: a frame somebody paid for
              should not vanish the moment you start talking to them. Safe here because
              the ring overhangs its box by ~14% and nothing in this header clips. */}
          <FramedAvatar
            frame={frame}
            source={avatar}
            name={username ?? '?'}
            colorSeed={friendId}
            size={36}
          />

          <View className="flex-1">
            <Text variant="bodyStrong">{username ?? t.messages.conversation}</Text>
            <StatusLabel status={chat.status} presence={chat.presence} isTyping={chat.isTyping} />
          </View>
        </Pressable>

        <FriendAction userId={friendId} onOpenMenu={() => setFriendMenuOpen(true)} />
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
          // Newest first, drawn bottom-up. `inverted` flips the whole list on its vertical
          // axis, so index 0 sits against the compose box and "the start of the list" —
          // the only position a virtualised list can be sure of without measuring
          // everything — is the latest message.
          //
          // The old shape was the natural one, oldest first with `scrollToEnd` on
          // `onContentSizeChange`, and it opened long threads part-way up the history.
          // That is a race it cannot win: with no `getItemLayout` to go on, the content
          // height at that moment is ten measured bubbles plus an estimate for the rest,
          // so it scrolled to an estimated end which then moved as the real heights
          // arrived. Short threads fit in the first batch and looked fine, which is why
          // this only ever reproduced on the conversations worth having.
          //
          // Inverting also removes the jump when a send refetches the history, and puts
          // new messages where they belong with no scrolling at all.
          inverted
          data={newestFirst}
          keyExtractor={keyExtractor}
          contentContainerClassName="gap-2 p-4"
          ListEmptyComponent={
            // No counter-flip here. VirtualizedList already un-inverts this cell for us,
            // and its own transform is `scale: -1` on Android rather than `scaleY: -1` —
            // composing ours on top replaced theirs and left the text mirrored
            // horizontally on exactly the platform we ship.
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

      <View
        ref={attachComposerHint}
        collapsable={false}
        className="flex-row items-end gap-2 border-t border-line p-3"
      >
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

      {/* Last children of the Screen, so they cover it. The friend control in the header
          only asks for them; owning them there would put a full-screen overlay inside a
          40dp row. */}
      {friendMenuOpen && (
        <FriendMenu
          userId={friendId}
          username={username ?? t.messages.thisGamer}
          onClose={() => setFriendMenuOpen(false)}
          onConfirm={setConfirm}
        />
      )}

      {confirm && (
        <ConfirmDialog request={confirm} busy={false} onCancel={() => setConfirm(null)} />
      )}
    </Screen>
  );
}

/**
 * What you can do about somebody you are already friends with.
 *
 * The same three actions as their profile screen, offered without making anybody go and
 * find it. Removing and blocking both ask first — they are quiet, one-tap actions with
 * consequences the other person sees, and `ConfirmDialog` exists for exactly that.
 * Reporting does not, because `ReportSheet` on the profile is its own second step.
 */
function FriendMenu({
  userId,
  username,
  onClose,
  onConfirm,
}: {
  userId: string;
  username: string;
  onClose: () => void;
  onConfirm: (request: ConfirmRequest) => void;
}) {
  const t = useT();
  const router = useRouter();
  const queryClient = useQueryClient();

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['friends'] });
    void queryClient.invalidateQueries({ queryKey: ['inbox'] });
  };

  const remove = useMutation({
    mutationFn: () => socialApi.remove(userId),
    onSuccess: refresh,
  });

  // Blocking ends the conversation, so there is nothing left to stay on. The profile
  // screen does the same thing for the same reason.
  const block = useMutation({
    mutationFn: () => socialApi.block(userId),
    onSuccess: () => {
      refresh();
      void queryClient.invalidateQueries({ queryKey: ['blocked'] });
      router.replace('/messages');
    },
  });

  return (
    <ActionSheet
      onCancel={onClose}
      actions={[
        {
          label: t.profile.removeFriend,
          destructive: true,
          onPress: () => {
            onClose();
            onConfirm({
              title: t.profile.removeConfirmTitle(username),
              body: t.profile.removeConfirmBody,
              confirmLabel: t.common.remove,
              destructive: true,
              onConfirm: () => remove.mutate(),
            });
          },
        },
        {
          label: t.profile.block,
          destructive: true,
          onPress: () => {
            onClose();
            onConfirm({
              title: t.profile.blockConfirmTitle(username),
              body: t.profile.blockConfirmBody,
              confirmLabel: t.profile.block,
              destructive: true,
              onConfirm: () => block.mutate(),
            });
          },
        },
      ]}
    />
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
 * - you asked them  → waiting, and tapping offers to take the request back. It used to be
 *   inert, which left a mistaken tap on the plus permanent until the other side answered.
 * - neither         → send the request.
 *
 * Reads three small lists that other screens already keep warm, so opening a chat does
 * not usually cost a request. Renders nothing at all until they have loaded, because a
 * plus that turns into a tick a moment later invites the tap it then ignores.
 */
function FriendAction({ userId, onOpenMenu }: { userId: string; onOpenMenu: () => void }) {
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
   * Taking the request back, on the tap and without a confirmation — the same as the icon
   * on their profile. It undoes something you did yourself and can redo just as easily.
   */
  const withdraw = useMutation({
    mutationFn: () => socialApi.withdraw(userId),
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
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [friends.refetch, incoming.refetch, outgoing.refetch]),
  );

  if (friends.isPending || incoming.isPending || outgoing.isPending) return null;

  const isFriend = (friends.data ?? []).some((f) => f.userId === userId);
  const theyAsked = (incoming.data ?? []).some((f) => f.userId === userId);
  const youAsked = (outgoing.data ?? []).some((f) => f.userId === userId);
  const busy = send.isPending || accept.isPending || withdraw.isPending;

  /*
   * Being friends is a state you can act on, not a badge.
   *
   * This used to render a bare `View`: no press handler, not even a Pressable, so the
   * control that had offered "add friend" a moment earlier became inert the instant it
   * succeeded. A tester reported tapping it and getting nothing, which is the right
   * complaint — the icon still looks like the button it used to be.
   *
   * The actions themselves live on the profile screen already; this opens the same set
   * without making somebody navigate to find them. Reported through `onOpenMenu` rather
   * than owning the sheet here, because an `ActionSheet` must be the last child of a
   * `Screen` to cover it — see the note in `src/ui/ActionSheet.tsx`.
   */
  if (isFriend) {
    return (
      <Pressable
        onPress={onOpenMenu}
        accessibilityRole="button"
        accessibilityLabel={t.profile.moreActionsA11y}
        hitSlop={8}
        className="h-10 w-10 items-center justify-center active:opacity-60"
      >
        <PersonIcon color={colors.primary} badge="check" background={colors.canvas} />
      </Pressable>
    );
  }

  return (
    <Pressable
      onPress={() =>
        theyAsked ? accept.mutate() : youAsked ? withdraw.mutate() : send.mutate()
      }
      disabled={busy}
      accessibilityRole="button"
      accessibilityLabel={
        theyAsked
          ? t.messages.acceptRequest
          : youAsked
            ? t.profile.withdrawRequest
            : t.messages.sendRequest
      }
      hitSlop={8}
      className="h-10 w-10 items-center justify-center active:opacity-60"
    >
      {busy ? (
        <ActivityIndicator color={colors.primary} />
      ) : (
        <PersonIcon
          // Waiting is muted with the plus replaced by a dot: nothing to send, but still
          // tappable — that is where withdrawing the request lives.
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
