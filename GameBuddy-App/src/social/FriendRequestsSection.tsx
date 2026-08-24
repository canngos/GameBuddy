import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect } from 'expo-router';
import { Check, ChevronDown, ChevronUp, UserX, X } from 'lucide-react-native';
import { memo, useCallback, useMemo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { socialApi } from '../api/social';
import type { GamerSummary } from '../api/types';
import { useUpper } from '../i18n/case';
import { useCountryName } from '../i18n/countryNames';
import { useT } from '../i18n/useT';
import { Avatar, Card, Icon, Text, cn, messageOf, showToast } from '../ui';

/**
 * Incoming friend requests, each answerable in place.
 *
 * **Lives on the messages tab.** It was a section at the bottom of Profile, on the grounds
 * that Profile is where an answerable prompt is least likely to be missed — but a friend
 * request is somebody asking for a conversation, and the tab named for conversations is
 * where testers went looking for it. Kept above both sections there, so it is the first
 * thing on the screen while anything is pending.
 *
 * **It folds, and it is built to the same pattern as the two sections below it.** The first
 * version reused the row from Profile unchanged: a full-width name, two word-labelled
 * buttons, and a card tall enough that one pending request pushed the conversations off the
 * screen — and a long username wrapped underneath the Accept button. Same overline header
 * with a count and a chevron, same collapse, and the answer is now two icon buttons that
 * cannot grow with the language they are labelled in.
 *
 * Owns its own query rather than taking one, because the two screens that could show it
 * fetch it for different reasons — Profile counted requests, Messages lists them — and one
 * of them passing a differently-configured query is exactly how the two would drift.
 *
 * Renders nothing at all when there is nothing to answer: an empty "no requests" card at
 * the top of the inbox would be permanent furniture for almost everyone.
 */
export function FriendRequestsSection() {
  const t = useT();
  const upper = useUpper();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(true);

  /*
   * Never served stale, and re-asked whenever this screen comes back.
   *
   * A friend request has no socket event behind it — unlike a message, a presence change or
   * anything to do with a lobby — so the only things that could put one on screen were a
   * push notification and the global five-minute `staleTime`. That is a long time to sit on
   * an empty list looking at a section that renders nothing when it is empty, and it is
   * indistinguishable from the request never having been sent. A gamer who withdrew a
   * request and sent it again reported exactly that, and this is the half of it the client
   * owns; the other half was the push being collapsed away, fixed in `FCMService`.
   *
   * Focus is the right trigger because it is when somebody has come to look.
   *
   * **Focus alone was not enough.** A withdrawal is silent by design — the backend sends no
   * push and no socket frame for it — so a request cancelled while this tab is already open
   * stayed on screen until the gamer navigated away and came back, which is exactly what the
   * next round of testing reported. Tapping Accept on that ghost row then failed with
   * `FRIEND_NO_REQUEST`, which reads as a broken app rather than as a race.
   *
   * A poll is the honest fix for state that changes without telling us. It costs nothing
   * when it matters least: react-query is wired to `focusManager` in `app/_layout.tsx`, so
   * this stops entirely while the app is backgrounded, and the query is cheap and idempotent.
   */
  // focusManager stops the poll while the app is backgrounded, but not while this tab
  // is merely blurred: `freezeOnBlur` suspends rendering via react-freeze, and query
  // observers keep firing underneath it. Gating the interval on tab focus is what
  // actually stops the 4-requests-a-minute drip from a tab nobody is looking at.
  const [focused, setFocused] = useState(false);

  const query = useQuery({
    queryKey: ['friendRequests'],
    queryFn: socialApi.pendingRequests,
    staleTime: 0,
    refetchInterval: focused ? 15_000 : false,
  });

  const refetch = query.refetch;
  useFocusEffect(
    useCallback(() => {
      setFocused(true);
      void refetch();
      return () => setFocused(false);
    }, [refetch]),
  );

  const answer = useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      accept ? socialApi.accept(userId) : socialApi.reject(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
      // Accepting moves this person from the matches section into friends, and the inbox
      // is what the split is drawn from. Without this the row stays where it was until
      // the tab is next focused.
      void queryClient.invalidateQueries({ queryKey: ['inbox'] });
    },

    /*
     * A failure here is nearly always a row that should not still be on screen — the
     * request was withdrawn or answered elsewhere between the list loading and the tap.
     * So the list is re-read on failure too. Previously only `onSuccess` invalidated,
     * which meant the one event that *proved* the row was stale left it sitting there.
     *
     * Said with a toast rather than a banner. The banner was rendered from `answer.error`,
     * which persists until the next mutation, so it outlived the row it described and was
     * still showing underneath the *next* request to arrive — reported, and fair. A toast
     * expires on its own after a few seconds and cannot contradict what is on screen later.
     */
    onError: (error) => {
      void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
      showToast({
        id: 'friend-request-failed',
        title: t.messages.requestGoneTitle,
        body: messageOf(error) ?? t.errors.generic,
        icon: UserX,
        tone: 'muted',
      });
    },
  });

  // Stable, so the rows below can actually bail out of re-rendering. Depends on
  // `mutate`, not the mutation: react-query's mutation *object* is a fresh literal every render; `mutate` is its stable part.
  const { mutate: answerMutate } = answer;
  const onAnswer = useCallback(
    (userId: string, accept: boolean) => answerMutate({ userId, accept }),
    [answerMutate],
  );

  const requests = query.data ?? [];
  if (requests.length === 0) return null;

  return (
    <View className="gap-2 pb-1">
      <Pressable
        onPress={() => setOpen((current) => !current)}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={t.messages.sectionA11y(t.profile.friendRequestsShort, requests.length)}
        className="flex-row items-center gap-2 pb-1 pt-4 active:opacity-70"
      >
        <Text variant="overline">
          {upper(t.profile.friendRequestsShort)} · {requests.length}
        </Text>

        <View className="flex-1" />

        {/* Matches the chevron on the sections below, and must keep matching it — the two
            headers sit one above the other on the same tab. See the note there for why
            this is a Lucide glyph rather than a rotated bordered box. */}
        <Icon as={open ? ChevronUp : ChevronDown} size={16} tone="muted" />
      </Pressable>

      {open && (
        // A `map`, not a nested FlatList: this sits inside the messages list's header, and
        // a virtualized list inside another one un-virtualizes itself and warns. Pending
        // requests are self-limiting anyway — answering them is the point of the section.
        <View className="gap-2">
          {requests.map((person) => (
            <RequestRow
              key={person.userId}
              person={person}
              busy={answer.isPending}
              onAnswer={onAnswer}
            />
          ))}
        </View>
      )}

    </View>
  );
}

/**
 * One pending request: who it is, and two ways to answer.
 *
 * Sized like a conversation row rather than like a form. The two answers are icon buttons
 * because that is what keeps the row one line high in every language — "Accept" is
 * "Kabul et" in Turkish and "Annehmen" in German, and the word-labelled version wrapped
 * the username in all three.
 *
 * Memoised on primitives and a stable `onAnswer`, so answering one request does not
 * re-render the rest of them.
 */
const RequestRow = memo(function RequestRow({
  person,
  busy,
  onAnswer,
}: {
  person: GamerSummary;
  busy: boolean;
  onAnswer: (userId: string, accept: boolean) => void;
}) {
  const t = useT();
  const localize = useCountryName();
  const accept = useCallback(() => onAnswer(person.userId, true), [onAnswer, person.userId]);
  const decline = useCallback(() => onAnswer(person.userId, false), [onAnswer, person.userId]);
  const meta = useMemo(
    () => [person.age, localize(person.country)].filter(Boolean).join(' · '),
    [person.age, person.country, localize],
  );

  return (
    <Card className="flex-row items-center gap-3 px-4 py-3">
      {/* Plain Avatar, not FramedAvatar: a request is not yet somebody whose cosmetics you
          have agreed to look at, and the frame is what made this row tall. */}
      <Avatar source={person.avatar} name={person.username} colorSeed={person.userId} size={36} />

      <View className="min-w-0 flex-1">
        <Text variant="bodyStrong" numberOfLines={1}>
          {person.username}
        </Text>
        <Text variant="caption" numberOfLines={1}>
          {meta}
        </Text>
      </View>

      <AnswerButton
        icon={X}
        tone="muted"
        label={t.profile.declineRequestA11y(person.username)}
        busy={busy}
        onPress={decline}
      />
      <AnswerButton
        icon={Check}
        tone="inverse"
        filled
        label={t.profile.acceptRequestA11y(person.username)}
        busy={busy}
        onPress={accept}
      />
    </Card>
  );
});

/** One 36dp answer: filled for accept, outlined for decline. */
function AnswerButton({
  icon,
  tone,
  filled = false,
  label,
  busy,
  onPress,
}: {
  icon: typeof Check;
  tone: 'muted' | 'inverse';
  filled?: boolean;
  label: string;
  busy: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={busy}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: busy }}
      hitSlop={6}
      className={cn(
        'h-9 w-9 items-center justify-center rounded-full border',
        filled ? 'border-transparent bg-primary' : 'border-line bg-transparent',
        busy ? 'opacity-40' : 'active:opacity-70',
      )}
    >
      <Icon as={icon} size={18} tone={tone} strokeWidth={2.5} />
    </Pressable>
  );
}
