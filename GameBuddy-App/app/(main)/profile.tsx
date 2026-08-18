import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Crown, Settings, Shirt } from 'lucide-react-native';
import { memo, useCallback, useMemo } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { billingApi } from '../../src/api/billing';
import { profileApi } from '../../src/api/catalogue';
import { socialApi } from '../../src/api/social';
import type { GamerSummary } from '../../src/api/types';
import { useUpper } from '../../src/i18n/case';
import { useCountryName } from '../../src/i18n/countryNames';
import { useT } from '../../src/i18n/useT';
import { useThemeColors } from '../../src/theme';
import {
  Button,
  Card,
  ErrorNotice,
  FramedAvatar,
  Icon,
  ProfileBanner,
  Screen,
  Text,
} from '../../src/ui';

export default function Profile() {
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const upper = useUpper();
  const localize = useCountryName();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const requests = useQuery({ queryKey: ['friendRequests'], queryFn: socialApi.pendingRequests });
  const friends = useQuery({ queryKey: ['friends'], queryFn: socialApi.friends });

  // Same key the Market, the deck and the paywall use, so react-query serves all of them
  // from one request rather than this adding a fourth call on a screen that already makes
  // three.
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: billingApi.subscription });
  const isGold = subscription.data?.tier === 'GOLD';

  return (
    <Screen scroll edges={['top']}>
      <View className="flex-row items-center justify-between pb-6 pt-8">
        <View>
          <Text variant="overline">{upper(t.profile.header)}</Text>
          <Text variant="title">{t.profile.title}</Text>
        </View>
        <View className="flex-row items-center gap-2">
          {/* Inventory before Settings, because it is the one people come back to. A
              wardrobe is visited whenever something new is bought; settings are visited
              roughly once. */}
          <Pressable
            onPress={() => router.push('/inventory')}
            accessibilityRole="button"
            accessibilityLabel={t.profile.inventoryA11y}
            className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
          >
            <Icon as={Shirt} size={20} tone="content" />
          </Pressable>

          <Pressable
            onPress={() => router.push('/settings')}
            accessibilityRole="button"
            accessibilityLabel={t.profile.settingsA11y}
            className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
          >
            {/* A cog, not the three dots that used to be here. The dots said "there is a
                menu behind this", and there is not — it goes straight to Settings. */}
            <Icon as={Settings} size={20} tone="content" />
          </Pressable>
        </View>
      </View>

      {me.isPending && <ActivityIndicator color={colors.primary} />}
      {me.error && <ErrorNotice error={me.error} onRetry={() => me.refetch()} />}

      <View className="gap-6">
        {me.data && (
          <Card className="gap-5">
            <ProfileBanner source={me.data.banner} />

            {/* Pulled up over the banner's lower edge, which is the arrangement every
                profile header uses: it ties the two together instead of stacking a
                picture on top of an unrelated strip of art. */}
            <View className="-mt-9 flex-row items-end gap-4">
              <FramedAvatar
                frame={me.data.frame}
                source={me.data.avatar}
                name={me.data.username}
                colorSeed={me.data.userId}
                size={72}
              />
              <View className="flex-1 gap-0.5 pb-1">
                {/* The name shrinks and truncates so the tag beside it is never pushed off
                    the row. A long username losing its tail is a smaller loss than a
                    membership badge that silently disappears for exactly the people who
                    have one. */}
                <View className="flex-row items-center gap-2">
                  <Text variant="heading" numberOfLines={1} className="shrink">
                    {me.data.username}
                  </Text>
                  {isGold && <GoldTag />}
                </View>
                <Text variant="caption">
                  {[me.data.age, localize(me.data.country)].filter(Boolean).join(' · ')}
                </Text>
              </View>
            </View>

            <View className="flex-row gap-3">
              {/* Two of the three go somewhere, and the count is the way in: a number
                  you can tap beats the same number printed above a list of the thing it
                  counts. Coins is the odd one out on purpose — spending them is the
                  Market's job, and sending people there from here would be a shop
                  doorway in the middle of a profile. */}
              <Stat
                label={t.profile.friends}
                value={friends.data?.length ?? 0}
                onPress={() => router.push('/friends')}
              />
              <Stat label={t.profile.coins} value={me.data.coin ?? 0} />
              <Stat
                label={t.profile.badges}
                value={me.data.badgeCount ?? 0}
                onPress={() => router.push('/badges')}
              />
            </View>

            <Showcase badges={me.data.badges ?? []} onPress={() => router.push('/badges')} />

            <View className="h-px bg-line" />

            <Tags title={t.profile.games} items={me.data.games.map((g) => g.gameName)} accent />
            {/* Directly under games, because it is the second half of the same question:
                what you play, and what you play it on. */}
            <Tags title={t.profile.playsOn} items={me.data.platforms ?? []} />
            <Tags title={t.profile.keywords} items={me.data.keywords.map((k) => k.keywordName)} />
          </Card>
        )}

        {/* The one thing on this screen waiting on the gamer rather than describing
            them, so it stays here rather than moving to the friends list with the
            friends themselves — an answerable prompt one tap deeper is one that gets
            missed. It renders nothing when there is nothing to answer. */}
        <FriendRequests query={requests} />
      </View>
    </Screen>
  );
}

/**
 * The membership badge that sits beside your own name.
 *
 * Small on purpose. It is a status marker, not an advert — the person reading it has already
 * paid, so it only has to be recognisable at a glance, and a tag competing with the username
 * for the top of the screen would read as the app still selling to somebody who has bought.
 *
 * The shape is the saving badge from the paywall's plan rows (`app/(main)/gold.tsx`): a
 * gold-tinted pill with 11px semibold gold text. Reused rather than reinvented so the app
 * has one "small gold badge" instead of two that nearly match.
 *
 * A filled crown rather than an outlined one: at 12px an outline closes up into a blob, and
 * the crown is the mark the whole Gold screen is built around.
 */
function GoldTag() {
  const colors = useThemeColors();
  const t = useT();

  return (
    <View
      className="shrink-0 flex-row items-center gap-1 rounded-full bg-gold/20 px-2 py-0.5"
      accessibilityRole="text"
      accessibilityLabel={t.profile.goldMemberA11y}
    >
      <Icon as={Crown} size={12} tone="gold" fill={colors.gold} strokeWidth={2} />
      <Text className="font-semibold text-[11px] leading-[15px] text-gold">Gold</Text>
    </View>
  );
}

function Stat({
  label,
  value,
  onPress,
}: {
  label: string;
  value: number;
  onPress?: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : undefined}
      // Both branches carry the same class keys; only the values move. A class present
      // on one and absent on the other stops NativeWind painting the subtree — see
      // `src/ui/hairline.ts`.
      className={
        onPress
          ? 'flex-1 items-center gap-0.5 rounded-card bg-raised py-3 active:opacity-70'
          : 'flex-1 items-center gap-0.5 rounded-card bg-raised py-3 active:opacity-100'
      }
    >
      <Text className="font-bold text-[20px] leading-[26px] text-primary">{value}</Text>
      <Text variant="caption">{label}</Text>
    </Pressable>
  );
}

/**
 * The three badges this gamer chose to display.
 *
 * Always rendered, even with nothing on show — the empty state is an invitation to go
 * and earn one, and a row that appears out of nowhere the first time you showcase
 * something makes the card jump.
 */
function Showcase({
  badges,
  onPress,
}: {
  badges: { code: string; title: string; icon: string }[];
  onPress: () => void;
}) {
  const t = useT();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={t.profile.badgesA11y}
      className="flex-row items-center gap-3 active:opacity-70"
    >
      {badges.length === 0 ? (
        <Text variant="caption">{t.profile.noShowcase}</Text>
      ) : (
        badges.map((badge) => (
          // Wide enough for two lines of the longest title in the set. At one line
          // every name but the shortest ended in an ellipsis, which is a worse way to
          // spend the space than simply wrapping.
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
        ))
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

/** Pending requests, each answerable in place. */
function FriendRequests({ query }: { query: ReturnType<typeof useQuery<GamerSummary[]>> }) {
  const t = useT();
  const upper = useUpper();
  const queryClient = useQueryClient();

  const answer = useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      accept ? socialApi.accept(userId) : socialApi.reject(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
    },
  });

  // Stable, so the rows below can actually bail out of re-rendering.
  const onAnswer = useCallback(
    (userId: string, accept: boolean) => answer.mutate({ userId, accept }),
    [answer],
  );

  const requests = query.data ?? [];
  if (requests.length === 0) return null;

  return (
    <View className="gap-3">
      <Text variant="overline">{upper(t.profile.friendRequests(requests.length))}</Text>

      {/* Left as a `map` rather than virtualized, deliberately: this is a *section* inside
          the profile's ScrollView, and a FlatList nested in one is the layout React Native
          warns about — it would un-virtualize itself and gain nothing. Pending requests are
          also self-limiting, because answering them is the whole point of the section. The
          per-render cost is what mattered here, and that is what the memo below removes. */}
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

      {answer.error && <ErrorNotice error={answer.error} />}
    </View>
  );
}

/**
 * One pending friend request.
 *
 * Memoised on primitives and a stable `onAnswer`, so answering one request does not
 * re-render the rest of them — and neither does anything else on the profile, which is
 * the screen this section happens to live on.
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
    <Card className="flex-row items-center gap-3 p-4">
      <FramedAvatar
        frame={person.frame}
        source={person.avatar}
        name={person.username}
        colorSeed={person.userId}
        size={44}
      />
      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{person.username}</Text>
        <Text variant="caption">{meta}</Text>
      </View>

      <Button label={t.profile.accept} size="md" className="px-4" disabled={busy} onPress={accept} />
      <Button
        label={t.profile.no}
        variant="ghost"
        size="md"
        className="px-3"
        disabled={busy}
        onPress={decline}
      />
    </Card>
  );
});
