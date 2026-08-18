/**
 * Somebody else's profile, as a component rather than a route.
 *
 * It hangs off two doors — the person at the top of a chat, and a row in the friends
 * list — and each needs its own back stack: backing out of a profile opened from a chat
 * belongs in that chat, and one opened from Friends belongs in Friends. Expo Router
 * gives that for free when the same component is mounted at two routes, and takes it
 * away the moment one route is pushed from the other tab.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Check, Clock, MoreVertical, UserCheck, UserPlus } from 'lucide-react-native';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { profileApi } from '../api/catalogue';
import { matchApi } from '../api/match';
import { moderationApi } from '../api/moderation';
import { socialApi } from '../api/social';
import { useCountryName } from '../i18n/countryNames';
import { useT } from '../i18n/useT';
import { useCelebration } from '../match/celebration';
import { useThemeColors } from '../theme';
import {
  ActionSheet,
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  FramedAvatar,
  Icon,
  ProfileBanner,
  ReportSheet,
  Screen,
  Text,
  ConfirmDialog,
  cn,
  type ConfirmRequest,
} from '../ui';

/**
 * Somebody else's profile.
 *
 * Reachable by tapping the person at the top of a chat, which is where anyone looks for
 * "who is this" and, more to the point, for the way out: block, report, or drop the
 * friendship. Those three were reachable from nowhere at all — `remove` and `block` had
 * endpoints the app never called, and a profile could not be reported at any price.
 *
 * The read is the same endpoint the own-profile tab uses. It returns less for somebody
 * else — no e-mail, no coin balance, no friend list — and that filtering is the server's
 * decision, not this screen's.
 */
type GamerProfileScreenProps = {
  /**
   * Where to land after blocking, which is the one thing this screen cannot decide for
   * itself: blocking closes the conversation behind it, so staying put would leave the
   * gamer looking at a profile with a dead screen underneath. The route back depends on
   * which door they came through.
   */
  afterBlock: '/messages' | '/friends';
};

export function GamerProfileScreen({ afterBlock }: GamerProfileScreenProps) {
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const localize = useCountryName();
  const queryClient = useQueryClient();
  const { userId, username } = useLocalSearchParams<{
    userId: string;
    username?: string;
  }>();

  const [reporting, setReporting] = useState(false);
  const [reported, setReported] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  /** The question currently being asked, or null. See `src/ui/ConfirmDialog.tsx`. */
  const [confirm, setConfirm] = useState<ConfirmRequest | null>(null);

  const gamer = useQuery({
    queryKey: ['gamer', userId],
    queryFn: () => profileApi.byId(userId),
  });

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const friends = useQuery({
    queryKey: ['friends'],
    queryFn: socialApi.friends,
  });

  /**
   * The four lists that decide which relationship buttons this screen offers.
   *
   * All four are keys other screens keep warm, so arriving here from the inbox or the
   * admirers grid usually costs nothing. `admirers` is the one that earns its place: it is
   * how the screen knows this person already liked you, which is what makes matching a
   * single tap rather than a trip back to the deck to find them again.
   */
  const matches = useQuery({ queryKey: ['matches'], queryFn: matchApi.matches });
  const admirers = useQuery({ queryKey: ['admirers'], queryFn: matchApi.likedYou });
  const incoming = useQuery({ queryKey: ['friendRequests'], queryFn: socialApi.pendingRequests });
  const outgoing = useQuery({ queryKey: ['sentRequests'], queryFn: socialApi.sentRequests });

  const isSelf = me.data?.userId === userId;
  const isFriend = (friends.data ?? []).some((friend) => friend.userId === userId);
  const isMatched = (matches.data ?? []).some((match) => match.userId === userId);
  const likedYou = (admirers.data?.likedYou ?? []).some((c) => c.userId === userId);
  const theyAsked = (incoming.data ?? []).some((person) => person.userId === userId);
  const youAsked = (outgoing.data ?? []).some((person) => person.userId === userId);

  /** Everything that changes who this person is to you touches the same three lists. */
  const refreshRelationship = () => {
    void queryClient.invalidateQueries({ queryKey: ['friends'] });
    void queryClient.invalidateQueries({ queryKey: ['inbox'] });
    void queryClient.invalidateQueries({ queryKey: ['matches'] });
  };

  /** The friend-request lists, which the three request actions all move between. */
  const refreshRequests = () => {
    refreshRelationship();
    void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
    void queryClient.invalidateQueries({ queryKey: ['sentRequests'] });
  };

  const remove = useMutation({
    mutationFn: () => socialApi.remove(userId),
    onError: () => setConfirm(null),
    onSuccess: () => {
      setConfirm(null);
      refreshRelationship();
    },
  });

  /**
   * Likes them back, which for somebody who already liked you is a match on the spot.
   *
   * The whole reason this button exists: the only way to match with an admirer used to be
   * to find them again in the deck, and their profile — the screen you land on from the
   * Liked You grid — offered nothing but report and block.
   *
   * The celebration is raised through the same store the deck uses, so matching here gets
   * the full-screen moment and its "send a message" route rather than a quieter version of
   * the same event. Costs a swipe from the daily allowance exactly as the deck does, and
   * the server says so — hence `allowance` in the refresh.
   */
  const match = useMutation({
    mutationFn: () => matchApi.accept(userId),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['admirers'] });
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      refreshRelationship();
      if (result.matched && gamer.data) {
        useCelebration.getState().celebrate({
          userId,
          username: gamer.data.username ?? t.profile.thisGamer,
          avatar: gamer.data.avatar,
          frame: gamer.data.frame,
        });
      }
    },
  });

  const sendRequest = useMutation({
    mutationFn: () => socialApi.sendRequest(userId),
    onSuccess: refreshRequests,
  });
  const acceptRequest = useMutation({
    mutationFn: () => socialApi.accept(userId),
    onSuccess: refreshRequests,
  });
  const withdrawRequest = useMutation({
    mutationFn: () => socialApi.withdraw(userId),
    onError: () => setConfirm(null),
    onSuccess: () => {
      setConfirm(null);
      refreshRequests();
    },
  });

  const block = useMutation({
    mutationFn: () => socialApi.block(userId),
    onError: () => setConfirm(null),
    onSuccess: () => {
      setConfirm(null);
      refreshRelationship();
      void queryClient.invalidateQueries({ queryKey: ['blocked'] });
      // Straight out of the screen. Blocking is mutual and total — the conversation
      // behind this is now closed in both directions, so leaving the gamer looking at
      // their profile with a dead chat underneath would be a worse answer than leaving.
      router.dismissAll();
      router.replace(afterBlock);
    },
  });

  const report = useMutation({
    mutationFn: (reason: string) => moderationApi.reportProfile(userId, reason),
    onSuccess: () => {
      setReporting(false);
      setReported(true);
    },
    onError: () => setReporting(false),
  });

  /** The person's name, for the confirmations that address them by it. */
  const who = gamer.data?.username ?? t.profile.thisGamer;

  const confirmRemove = () =>
    setConfirm({
      title: t.profile.removeConfirmTitle(who),
      body: t.profile.removeConfirmBody,
      confirmLabel: t.common.remove,
      destructive: true,
      onConfirm: () => remove.mutate(),
    });

  const confirmBlock = () =>
    setConfirm({
      title: t.profile.blockConfirmTitle(who),
      body: t.profile.blockConfirmBody,
      confirmLabel: t.profile.block,
      destructive: true,
      onConfirm: () => block.mutate(),
    });

  const confirmWithdraw = () =>
    setConfirm({
      title: t.profile.withdrawConfirmTitle(who),
      body: t.profile.withdrawConfirmBody,
      confirmLabel: t.profile.withdrawRequest,
      destructive: true,
      onConfirm: () => withdrawRequest.mutate(),
    });

  const busy =
    remove.isPending ||
    block.isPending ||
    report.isPending ||
    match.isPending ||
    sendRequest.isPending ||
    acceptRequest.isPending ||
    withdrawRequest.isPending;

  /**
   * The one control for the friendship, beside the name.
   *
   * A stack of full-width buttons under an ACTIONS heading is what a settings screen looks
   * like, not a profile — and most of the time three of the four states had nothing to say.
   * One icon carries the whole state machine instead: it shows where the friendship stands
   * and, in the two states that have an action, performing it is a single tap.
   *
   * Withdrawing asks nothing first. It undoes something the gamer did themselves a moment
   * ago and can redo just as easily, so a confirmation would be ceremony. Removing a friend
   * does ask, because that one throws away a relationship the other person agreed to.
   */
  const onFriendPress = () => {
    if (isFriend) return confirmRemove();
    if (youAsked) return withdrawRequest.mutate();
    if (theyAsked) return acceptRequest.mutate();
    if (isMatched) sendRequest.mutate();
  };

  const friendState: FriendState = isFriend
    ? 'friends'
    : youAsked
      ? 'sent'
      : theyAsked
        ? 'asked'
        : isMatched
          ? 'none'
          : 'locked';

  return (
    // Not `Screen scroll`: the report sheet positions itself absolutely over the whole
    // screen, and inside a ScrollView's content view "the whole screen" means the whole
    // scrollable content instead — the sheet would scroll away with the page. The list
    // scrolls in its own ScrollView and the sheet is a sibling of it.
    <Screen edges={['top']} padded={false}>
      <ScrollView
        contentContainerClassName="grow px-6 pb-8"
        showsVerticalScrollIndicator={false}
      >
        <BackHeader
          title={gamer.data?.username ?? username ?? t.profile.fallbackTitle}
          // Report and block moved behind this. They were two red buttons at the bottom of
          // every profile, which made looking at a person read as filling in a moderation
          // form — and they are actions somebody needs once, not every visit.
          right={
            isSelf ? undefined : (
              <Pressable
                onPress={() => setMenuOpen(true)}
                accessibilityRole="button"
                accessibilityLabel={t.profile.moreActionsA11y}
                hitSlop={12}
                className="h-10 w-10 items-center justify-center active:opacity-60"
              >
                <Icon as={MoreVertical} size={22} tone="content" />
              </Pressable>
            )
          }
        />

        {gamer.isPending && <ActivityIndicator color={colors.primary} />}
        {gamer.error && <ErrorNotice error={gamer.error} onRetry={() => gamer.refetch()} />}

        {gamer.data && (
          <View className="gap-6">
            <Card className="gap-5">
              <ProfileBanner source={gamer.data.banner} />

              <View className="-mt-9 flex-row items-end gap-4">
                <FramedAvatar
                  frame={gamer.data.frame}
                  source={gamer.data.avatar}
                  name={gamer.data.username}
                  colorSeed={gamer.data.userId}
                  size={72}
                />
                <View className="min-w-0 flex-1 gap-0.5 pb-1">
                  <Text variant="heading" numberOfLines={1}>
                    {gamer.data.username}
                  </Text>
                  <Text variant="caption">
                    {[gamer.data.age, localize(gamer.data.country)].filter(Boolean).join(' · ')}
                  </Text>
                </View>

                {/* The friendship, as one control beside the name. See `onFriendPress`. */}
                {!isSelf && (
                  <FriendIcon
                    state={friendState}
                    busy={
                      sendRequest.isPending ||
                      acceptRequest.isPending ||
                      withdrawRequest.isPending ||
                      remove.isPending
                    }
                    onPress={onFriendPress}
                  />
                )}
              </View>

              {/* No stats row. Friend and coin counts are the owner's business — what is
                worth showing about somebody else is what they chose to display. */}
              {(gamer.data.badges ?? []).length > 0 && (
                <View className="flex-row items-start gap-3">
                  {gamer.data.badges.map((badge) => (
                    <View key={badge.code} className="items-center gap-1" style={{ width: 80 }}>
                      <Image
                        source={{ uri: badge.icon }}
                        style={{ width: 48, height: 48 }}
                        contentFit="contain"
                        transition={150}
                        cachePolicy="memory-disk"
                        recyclingKey={badge.code}
                      />
                      <Text variant="caption" numberOfLines={2} className="text-center">
                        {badge.title}
                      </Text>
                    </View>
                  ))}
                </View>
              )}

              <View className="h-px bg-line" />

              <Tags
                title={t.profile.games}
                items={gamer.data.games.map((game) => game.gameName)}
                accent
              />
              <Tags
                title={t.profile.keywords}
                items={gamer.data.keywords.map((keyword) => keyword.keywordName)}
              />
            </Card>

            {/* No ACTIONS section. Report and block live behind the header menu, the
              friendship is the icon beside the name, and what is left is the one thing
              worth a full-width button: liking somebody back. Anything else here turned
              a profile into a form. */}
            {!isSelf && (
              <View className="gap-2 pb-8">
                {remove.error && <ErrorNotice error={remove.error} />}
                {block.error && <ErrorNotice error={block.error} />}
                {report.error && <ErrorNotice error={report.error} />}
                {match.error && <ErrorNotice error={match.error} />}
                {sendRequest.error && <ErrorNotice error={sendRequest.error} />}
                {acceptRequest.error && <ErrorNotice error={acceptRequest.error} />}
                {withdrawRequest.error && <ErrorNotice error={withdrawRequest.error} />}
                {reported && (
                  <Card>
                    <Text variant="caption">{t.profile.reported}</Text>
                  </Card>
                )}

                {/* Only for somebody who has already liked you, and only until it lands:
                  liking back an admirer is a match by definition, so there is no version
                  of this button that leaves the gamer waiting to find out. */}
                {likedYou && !isMatched && (
                  <View className="gap-1">
                    <Button
                      label={match.isPending ? t.profile.matching : t.profile.matchBack}
                      loading={match.isPending}
                      disabled={busy}
                      onPress={() => match.mutate()}
                    />
                    <Text variant="caption">{t.profile.matchBackCaption}</Text>
                  </View>
                )}
              </View>
            )}
          </View>
        )}
      </ScrollView>

      {/* Siblings of the ScrollView, not children of it — see the note on Screen above.
          The report sheet takes precedence: it is opened *from* the menu, and both being
          up at once would stack two dimmed layers. */}
      {menuOpen && !reporting && (
        <ActionSheet
          title={gamer.data?.username ?? undefined}
          onCancel={() => setMenuOpen(false)}
          actions={[
            {
              label: t.profile.reportProfile,
              destructive: true,
              onPress: () => {
                setMenuOpen(false);
                setReporting(true);
              },
            },
            {
              label: t.profile.block,
              destructive: true,
              onPress: () => {
                setMenuOpen(false);
                confirmBlock();
              },
            },
          ]}
        />
      )}

      {/* Above the sheets: it is a question that has to be answered before anything else,
          and it is only ever opened from one of them. */}
      {confirm && (
        <ConfirmDialog
          request={confirm}
          busy={remove.isPending || block.isPending || withdrawRequest.isPending}
          onCancel={() => setConfirm(null)}
        />
      )}

      {reporting && (
        <ReportSheet
          subject="profile"
          onCancel={() => setReporting(false)}
          onPick={(reason) => report.mutate(reason)}
        />
      )}
    </Screen>
  );
}

/** Where the friendship stands, which is the only thing the icon has to say. */
type FriendState = 'locked' | 'none' | 'sent' | 'asked' | 'friends';

/**
 * The friendship, as one tappable glyph beside the name.
 *
 * Four things it has to do at once: say what the state is, be obviously pressable in the
 * two states where a tap does something, be obviously *not* pressable before a match, and
 * never change size — it sits in a row with a name that is already fighting for width.
 *
 * `locked` is drawn rather than hidden. An icon that appears only after matching leaves
 * nothing to explain why you cannot add somebody, and the greyed outline plus its
 * accessibility label is that explanation.
 */
function FriendIcon({
  state,
  busy,
  onPress,
}: {
  state: FriendState;
  busy: boolean;
  onPress: () => void;
}) {
  const t = useT();
  const colors = useThemeColors();

  const spec = {
    locked: { icon: UserPlus, tone: 'muted', filled: false, label: t.profile.addFriendCaption },
    none: { icon: UserPlus, tone: 'inverse', filled: true, label: t.profile.addFriend },
    sent: { icon: Clock, tone: 'muted', filled: false, label: t.profile.withdrawRequest },
    asked: { icon: Check, tone: 'inverse', filled: true, label: t.profile.acceptRequest },
    friends: { icon: UserCheck, tone: 'primary', filled: false, label: t.profile.removeFriend },
  }[state] as { icon: typeof UserPlus; tone: 'muted' | 'inverse' | 'primary'; filled: boolean; label: string };

  const inert = state === 'locked' || busy;

  return (
    <Pressable
      onPress={inert ? undefined : onPress}
      disabled={inert}
      accessibilityRole="button"
      accessibilityLabel={spec.label}
      accessibilityState={{ disabled: inert }}
      hitSlop={8}
      className={cn(
        'h-11 w-11 shrink-0 items-center justify-center rounded-full border',
        spec.filled ? 'border-transparent bg-primary' : 'border-line bg-transparent',
        inert ? 'opacity-40' : 'active:opacity-70',
      )}
    >
      {busy ? (
        <ActivityIndicator color={spec.filled ? colors.onBrand : colors.primary} />
      ) : (
        <Icon as={spec.icon} size={20} tone={spec.tone} strokeWidth={2.5} />
      )}
    </Pressable>
  );
}

function Tags({
  title,
  items,
  accent = false,
}: {
  title: string;
  items: string[];
  accent?: boolean;
}) {
  return (
    <View className="gap-2">
      <Text variant="label" className="text-muted">
        {title}
      </Text>

      {items.length === 0 ? (
        <Text variant="body" className="text-muted">
          —
        </Text>
      ) : (
        <View className="flex-row flex-wrap gap-2">
          {items.map((item) => (
            <View
              key={item}
              className={
                accent
                  ? 'rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5'
                  : 'rounded-full bg-raised px-3 py-1.5'
              }
            >
              <Text variant="caption" className={accent ? 'text-primary' : 'text-content'}>
                {item}
              </Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}
