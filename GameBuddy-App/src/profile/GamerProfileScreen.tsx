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
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, View } from 'react-native';
import { profileApi } from '../api/catalogue';
import { moderationApi } from '../api/moderation';
import { socialApi } from '../api/social';
import { useUpper } from '../i18n/case';
import { useCountryName } from '../i18n/countryNames';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import {
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  FramedAvatar,
  ProfileBanner,
  ReportSheet,
  Screen,
  Text,
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
  const upper = useUpper();
  const localize = useCountryName();
  const queryClient = useQueryClient();
  const { userId, username } = useLocalSearchParams<{
    userId: string;
    username?: string;
  }>();

  const [reporting, setReporting] = useState(false);
  const [reported, setReported] = useState(false);

  const gamer = useQuery({
    queryKey: ['gamer', userId],
    queryFn: () => profileApi.byId(userId),
  });

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const friends = useQuery({
    queryKey: ['friends'],
    queryFn: socialApi.friends,
  });

  const isSelf = me.data?.userId === userId;
  const isFriend = (friends.data ?? []).some((friend) => friend.userId === userId);

  /** Everything that changes who this person is to you touches the same three lists. */
  const refreshRelationship = () => {
    void queryClient.invalidateQueries({ queryKey: ['friends'] });
    void queryClient.invalidateQueries({ queryKey: ['inbox'] });
    void queryClient.invalidateQueries({ queryKey: ['matches'] });
  };

  const remove = useMutation({
    mutationFn: () => socialApi.remove(userId),
    onSuccess: refreshRelationship,
  });

  const block = useMutation({
    mutationFn: () => socialApi.block(userId),
    onSuccess: () => {
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

  const confirmRemove = () =>
    Alert.alert(
      t.profile.removeConfirmTitle(gamer.data?.username ?? t.profile.thisGamer),
      t.profile.removeConfirmBody,
      [
        { text: t.common.cancel, style: 'cancel' },
        {
          text: t.common.remove,
          style: 'destructive',
          onPress: () => remove.mutate(),
        },
      ],
    );

  const confirmBlock = () =>
    Alert.alert(
      t.profile.blockConfirmTitle(gamer.data?.username ?? t.profile.thisGamer),
      t.profile.blockConfirmBody,
      [
        { text: t.common.cancel, style: 'cancel' },
        { text: t.profile.block, style: 'destructive', onPress: () => block.mutate() },
      ],
    );

  const busy = remove.isPending || block.isPending || report.isPending;

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
        <BackHeader title={gamer.data?.username ?? username ?? t.profile.fallbackTitle} />

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
                <View className="flex-1 gap-0.5 pb-1">
                  <Text variant="heading">{gamer.data.username}</Text>
                  <Text variant="caption">
                    {[gamer.data.age, localize(gamer.data.country)].filter(Boolean).join(' · ')}
                  </Text>
                </View>
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

            {/* Nothing to do to yourself. Reachable if a member list ever links here for
              the signed-in gamer, and a "Block yourself" button would be absurd. */}
            {!isSelf && (
              <View className="gap-2 pb-8">
                <Text variant="overline">{upper(t.profile.actions)}</Text>

                {remove.error && <ErrorNotice error={remove.error} />}
                {block.error && <ErrorNotice error={block.error} />}
                {report.error && <ErrorNotice error={report.error} />}
                {reported && (
                  <Card>
                    <Text variant="caption">{t.profile.reported}</Text>
                  </Card>
                )}

                {isFriend && (
                  <Button
                    label={t.profile.removeFriend}
                    variant="ghost"
                    disabled={busy}
                    onPress={confirmRemove}
                  />
                )}

                {/* Both destructive, and ordered by how final they are. Reporting asks a
                  moderator to look; blocking is a decision the gamer makes alone and
                  takes effect immediately, so it sits closest to the bottom. */}
                <Button
                  label={t.profile.reportProfile}
                  variant="danger"
                  disabled={busy}
                  onPress={() => setReporting(true)}
                />
                <Button
                  label={t.profile.block}
                  variant="danger"
                  loading={block.isPending}
                  disabled={busy}
                  onPress={confirmBlock}
                />
              </View>
            )}
          </View>
        )}
      </ScrollView>

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
