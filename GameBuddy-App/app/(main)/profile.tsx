import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, View } from 'react-native';
import { profileApi } from '../../src/api/catalogue';
import { socialApi } from '../../src/api/social';
import type { GamerSummary } from '../../src/api/types';
import { useThemeColors } from '../../src/theme';
import {
  Button,
  Card,
  ErrorNotice,
  FramedAvatar,
  ProfileBanner,
  Screen,
  Text,
} from '../../src/ui';

export default function Profile() {
  const router = useRouter();
  const colors = useThemeColors();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const requests = useQuery({ queryKey: ['friendRequests'], queryFn: socialApi.pendingRequests });
  const friends = useQuery({ queryKey: ['friends'], queryFn: socialApi.friends });

  return (
    <Screen scroll edges={['top']}>
      <View className="flex-row items-center justify-between pb-6 pt-8">
        <View>
          <Text variant="overline">YOU</Text>
          <Text variant="title">Profile</Text>
        </View>
        <Pressable
          onPress={() => router.push('/settings')}
          accessibilityRole="button"
          accessibilityLabel="Settings"
          className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
        >
          <View className="gap-1">
            {[0, 1, 2].map((i) => (
              <View key={i} className="h-1 w-1 rounded-full bg-content" />
            ))}
          </View>
        </Pressable>
      </View>

      {me.isPending && <ActivityIndicator color={colors.brand} />}
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
                <Text variant="heading">{me.data.username}</Text>
                <Text variant="caption">
                  {[me.data.age, me.data.country].filter(Boolean).join(' · ')}
                </Text>
              </View>
            </View>

            <View className="flex-row gap-3">
              <Stat label="Friends" value={friends.data?.length ?? 0} />
              <Stat label="Coins" value={me.data.coin ?? 0} />
              {/* The only stat that goes somewhere. It is a score with a screen behind
                  it, where the other two are just counts of what is already below. */}
              <Stat
                label="Badges"
                value={me.data.badgeCount ?? 0}
                onPress={() => router.push('/badges')}
              />
            </View>

            <Showcase badges={me.data.badges ?? []} onPress={() => router.push('/badges')} />

            <View className="h-px bg-line" />

            <Tags title="Games" items={me.data.games.map((g) => g.gameName)} accent />
            {/* Directly under games, because it is the second half of the same question:
                what you play, and what you play it on. */}
            <Tags title="Plays on" items={me.data.platforms ?? []} />
            <Tags title="Keywords" items={me.data.keywords.map((k) => k.keywordName)} />
          </Card>
        )}

        {/* Requests first when there are any: this is the one thing on the screen that
            is waiting on the gamer rather than describing them. It was the best part
            of the old home screen and it keeps that priority here. */}
        <FriendRequests query={requests} />

        <FriendList query={friends} />
      </View>
    </Screen>
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
      <Text className="font-bold text-[20px] leading-[26px] text-brand">{value}</Text>
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
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="Badges"
      className="flex-row items-center gap-3 active:opacity-70"
    >
      {badges.length === 0 ? (
        <Text variant="caption">No badges on show yet — tap to pick some</Text>
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
                  ? 'rounded-full border border-brand/30 bg-brand/10 px-3 py-1.5'
                  : 'rounded-full bg-raised px-3 py-1.5'
              }
            >
              <Text variant="caption" className={accent ? 'text-brand' : 'text-content'}>
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
  const queryClient = useQueryClient();

  const answer = useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      accept ? socialApi.accept(userId) : socialApi.reject(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['friendRequests'] });
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
    },
  });

  const requests = query.data ?? [];
  if (requests.length === 0) return null;

  return (
    <View className="gap-3">
      <Text variant="overline">
        FRIEND REQUESTS · {requests.length}
      </Text>

      <View className="gap-2">
        {requests.map((person) => (
          <Card key={person.userId} className="flex-row items-center gap-3 p-4">
            <FramedAvatar
              frame={person.frame}
              source={person.avatar}
              name={person.username}
              colorSeed={person.userId}
              size={44}
            />
            <View className="flex-1 gap-0.5">
              <Text variant="bodyStrong">{person.username}</Text>
              <Text variant="caption">
                {[person.age, person.country].filter(Boolean).join(' · ')}
              </Text>
            </View>

            <Button
              label="Accept"
              size="md"
              className="px-4"
              disabled={answer.isPending}
              onPress={() => answer.mutate({ userId: person.userId, accept: true })}
            />
            <Button
              label="No"
              variant="ghost"
              size="md"
              className="px-3"
              disabled={answer.isPending}
              onPress={() => answer.mutate({ userId: person.userId, accept: false })}
            />
          </Card>
        ))}
      </View>

      {answer.error && <ErrorNotice error={answer.error} />}
    </View>
  );
}

function FriendList({ query }: { query: ReturnType<typeof useQuery<GamerSummary[]>> }) {
  const queryClient = useQueryClient();
  const friends = query.data ?? [];

  /**
   * Removing a friend drops them back to being a match.
   *
   * The endpoint has existed since the friend tiers were built and nothing called it,
   * so a friendship could be made but never undone — which is the half of the pair that
   * actually matters to somebody who wants out of it.
   *
   * The match survives on purpose: unfriending is not blocking, and silently severing a
   * mutual match as well would be a bigger action than the button says.
   */
  const remove = useMutation({
    mutationFn: (userId: string) => socialApi.remove(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
      // The messages screen splits on friendship, so this row moves from one section to
      // the other. Without this it stays under Friends until the next poll.
      void queryClient.invalidateQueries({ queryKey: ['inbox'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const confirmRemove = (person: GamerSummary) =>
    Alert.alert(
      `Remove ${person.username}?`,
      'They go back to being a match — you can still message each other, and either of you can send a new friend request.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => remove.mutate(person.userId),
        },
      ],
    );

  return (
    <View className="gap-3 pb-4">
      <Text variant="overline">FRIENDS</Text>

      {query.error && <ErrorNotice error={query.error} onRetry={() => query.refetch()} />}
      {remove.error && <ErrorNotice error={remove.error} />}

      {!query.isPending && friends.length === 0 && (
        <Card className="gap-1">
          <Text variant="bodyStrong">No friends yet</Text>
          <Text variant="caption">
            You can add someone as a friend once you have matched with them.
          </Text>
        </Card>
      )}

      {friends.map((person) => (
        <Card key={person.userId} className="flex-row items-center gap-3 p-4">
          <FramedAvatar
            frame={person.frame}
            source={person.avatar}
            name={person.username}
            colorSeed={person.userId}
            size={44}
          />
          <View className="flex-1 gap-0.5">
            <Text variant="bodyStrong">{person.username}</Text>
            <Text variant="caption">
              {[person.age, person.country].filter(Boolean).join(' · ')}
            </Text>
          </View>

          {/* Ghost, and it asks first. Removing a friend is not dangerous — they stay a
              match — but it is not undoable in one tap either, and a solid button next
              to somebody's name reads as the point of the row. */}
          <Button
            label="Remove"
            variant="ghost"
            size="md"
            className="px-3"
            disabled={remove.isPending}
            onPress={() => confirmRemove(person)}
          />
        </Card>
      ))}
    </View>
  );
}
