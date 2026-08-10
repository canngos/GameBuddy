import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { matchApi } from '../../src/api/match';
import type { Candidate } from '../../src/api/types';
import { AdmirerCard } from '../../src/match/AdmirerCard';
import { useThemeColors } from '../../src/theme';
import { BackHeader, Button, ErrorNotice, Screen, Text } from '../../src/ui';

/**
 * Who liked you.
 *
 * The backend was built for this screen and then nothing called it: `getWhoLikedYou`
 * returns the count to everybody and the identities only on a paid tier, which is exactly
 * the shape this needs, and `matchApi.likedYou` has existed with no call sites since.
 *
 * The count is free on purpose. "Eleven people liked you" is true, useful, and the honest
 * version of the pitch — it is not a number invented to sell a subscription, it is the
 * number, and anybody can check it against their matches over time. Withholding only the
 * faces is the line that keeps this side of manipulative.
 */
export default function Admirers() {
  const router = useRouter();
  const colors = useThemeColors();

  const admirers = useQuery({
    queryKey: ['admirers'],
    queryFn: matchApi.likedYou,
    // Short, because acting on a stale list means tapping a face that has since
    // unmatched — and this screen is entered from a badge that claimed a number.
    staleTime: 30_000,
  });

  const data = admirers.data;
  const count = data?.count ?? 0;
  const locked = data?.locked ?? true;

  return (
    <Screen edges={['top']} padded={false}>
      <View className="px-6">
        <BackHeader title="Who liked you" />
      </View>

      {admirers.isPending && (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color={colors.brand} />
        </View>
      )}

      {admirers.error && (
        <View className="px-6">
          <ErrorNotice error={admirers.error} onRetry={() => void admirers.refetch()} />
        </View>
      )}

      {data && count === 0 && <Empty />}

      {data && count > 0 && (
        <View className="flex-1">
          <View className="gap-1 px-6 pb-4">
            <Text variant="display" className="text-brand">
              {count}
            </Text>
            <Text variant="body" className="text-muted">
              {count === 1 ? 'person likes you' : 'people like you'}
              {locked ? ' — upgrade to see who' : ''}
            </Text>
          </View>

          <View className="flex-1 flex-row flex-wrap gap-3 px-6">
            {locked
              ? // One blurred card per admirer, up to a sensible number of tiles. The
                // count above is the real number; these are placeholders for faces we
                // are deliberately not sending, so there is nothing to fetch per tile.
                Array.from({ length: Math.min(count, 6) }, (_, index) => (
                  <AdmirerCard key={`locked-${index}`} locked />
                ))
              : data.likedYou.map((candidate: Candidate) => (
                  <AdmirerCard
                    key={candidate.userId}
                    candidate={candidate}
                    onPress={() => router.push(`/messages/gamer/${candidate.userId}`)}
                  />
                ))}
          </View>

          {locked && (
            <View className="gap-3 border-t border-line bg-canvas px-6 pb-2 pt-4">
              <Text variant="caption" className="text-center">
                Gold shows you every face, and removes the daily like limit.
              </Text>
              <Button label="See who likes you" onPress={() => router.push('/gold')} />
            </View>
          )}
        </View>
      )}
    </Screen>
  );
}

function Empty() {
  return (
    <View className="flex-1 items-center justify-center gap-2 px-10">
      <Text variant="heading" className="text-center">
        Nobody yet
      </Text>
      <Text variant="body" className="text-center text-muted">
        When somebody likes you they show up here, whether or not you have liked them back.
      </Text>
    </View>
  );
}

/**
 * The badge that gets people here, for the deck header.
 *
 * Exported from this file so the count query is defined once — the badge and the screen
 * read the same cache entry, so opening the screen never shows a different number from
 * the one that was tapped.
 */
export function AdmirersBadge() {
  const router = useRouter();
  const admirers = useQuery({
    queryKey: ['admirers'],
    queryFn: matchApi.likedYou,
    staleTime: 30_000,
  });

  const count = admirers.data?.count ?? 0;
  if (count === 0) return null;

  return (
    <Pressable
      onPress={() => router.push('/admirers')}
      accessibilityRole="button"
      accessibilityLabel={`${count} people liked you`}
      hitSlop={8}
      className="items-center active:opacity-70"
    >
      <View className="min-w-6 items-center justify-center rounded-full bg-brand px-1.5 py-0.5">
        <Text className="font-semibold text-[13px] leading-[17px] text-white">{count}</Text>
      </View>
      <Text variant="caption">liked you</Text>
    </Pressable>
  );
}
