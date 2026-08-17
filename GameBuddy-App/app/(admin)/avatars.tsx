import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { memo, useCallback, useMemo } from 'react';
import { FlatList, Image, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import type { PendingAvatar } from '../../src/api/types';
import { Button, Card, ErrorNotice, Screen, Text } from '../../src/ui';

/**
 * Uploads the classifier was not sure about.
 *
 * The model answers APPROVE, REJECT or REVIEW, and REVIEW exists precisely because an
 * automated call should not be final on a person's photograph. Nothing used to be behind
 * it: the upload was marked pending, left in the private bucket, and no screen ever
 * listed it — so a picture the model hesitated over was refused forever, silently, while
 * its owner was told a human would look.
 *
 * The images have no public URL by design — they sit in the private bucket — so each one
 * comes from the backend rather than the CDN.
 */
export default function AvatarsScreen() {
  const queryClient = useQueryClient();

  const queue = useQuery({
    queryKey: ['admin', 'avatars'],
    queryFn: () => adminApi.pendingAvatars(),
    staleTime: 30_000,
  });

  const decide = useMutation({
    mutationFn: ({ userId, approve }: { userId: string; approve: boolean }) =>
      approve ? adminApi.approveAvatar(userId) : adminApi.rejectAvatar(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'avatars'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'analytics'] });
    },
  });

  const pending = queue.data?.pending ?? [];

  const keyExtractor = useCallback((item: PendingAvatar) => item.userId, []);
  const onDecide = useCallback(
    (userId: string, approve: boolean) => decide.mutate({ userId, approve }),
    [decide],
  );
  const renderRow = useCallback(
    ({ item }: { item: PendingAvatar }) => (
      <PendingRow item={item} busy={decide.isPending} onDecide={onDecide} />
    ),
    [decide.isPending, onDecide],
  );

  return (
    <Screen edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Moderation</Text>
        <Text variant="title">Avatars</Text>
      </View>

      {queue.error && <ErrorNotice error={queue.error} onRetry={() => void queue.refetch()} />}
      {decide.error && <ErrorNotice error={decide.error} />}

      <FlatList
        data={pending}
        keyExtractor={keyExtractor}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-8"
        onRefresh={() => void queue.refetch()}
        refreshing={queue.isFetching}
        ListEmptyComponent={
          queue.isLoading ? null : (
            <Text variant="body" className="mt-8 text-center text-muted">
              Nothing to review. Uploads land here only when the classifier is unsure.
            </Text>
          )
        }
        renderItem={renderRow}
      />
    </Screen>
  );
}

/** How long this person has been waiting on an answer. */
function waiting(iso: string | null) {
  // Uploads from before the column existed have no arrival time. "Waiting" with no
  // number is honest; inventing 1970 would not be.
  if (!iso) return 'waiting';
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 60) return `${Math.max(minutes, 1)}m waiting`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h waiting`;
  return `${Math.floor(hours / 24)}d waiting`;
}

type ReviewImageProps = {
  userId: string;
};

/**
 * The pending upload.
 *
 * Not `<Image source={{ uri, headers }}>`. That is the obvious way to do this and it
 * fails silently on Android: the header is dropped, the backend answers 401, and the
 * card renders a blank square with nothing in the log to say why. Measured, not guessed
 * — the 401s were in the backend's access log. The server inlines the image instead, so
 * this is an ordinary authenticated request. See `adminApi.avatarImage`.
 */
function ReviewImage({ userId }: ReviewImageProps) {
  const image = useQuery({
    queryKey: ['admin', 'avatar-image', userId],
    queryFn: () => adminApi.avatarImage(userId),
    // The key is the gamer, but the bytes behind it change when they upload again, and
    // an approved image should not linger in memory once it has left the queue.
    staleTime: 5 * 60_000,
    gcTime: 5 * 60_000,
    retry: 1,
  });

  if (image.data?.image) {
    return (
      // The last React Native `Image` in the app, and deliberately so. Every other site
      // moved to `expo-image` for its disk cache — but this `uri` is a base64 data URI
      // that arrived inside the JSON of the query above, so the bytes are already in
      // memory and there is no fetch to cache. `expo-image` would buy nothing here and
      // would cost the `className` sizing below, which it does not support.
      <Image
        source={{ uri: image.data.image }}
        className="mt-3 w-full rounded-card"
        style={{ aspectRatio: 1 }}
        resizeMode="cover"
      />
    );
  }

  return (
    <View
      className="mt-3 w-full items-center justify-center rounded-card bg-canvas"
      style={{ aspectRatio: 1 }}
    >
      <Text variant="caption">
        {image.isError ? 'The image could not be loaded.' : 'Loading the image…'}
      </Text>
    </View>
  );
}

/**
 * One upload awaiting a verdict.
 *
 * Extracted so it can be memoised — it was inline JSX inside `renderItem`. `waiting()`
 * reads the clock and builds a `Date`, so it ran for every row on every render; it is
 * memoised on the timestamp, which is the only input that can change the answer.
 *
 * The image itself still fetches per row, and that is inherent: each is a separate
 * moderation asset behind its own authenticated request. Virtualization is what keeps
 * that bounded to the rows actually on screen.
 */
const PendingRow = memo(function PendingRow({
  item,
  busy,
  onDecide,
}: {
  item: PendingAvatar;
  busy: boolean;
  onDecide: (userId: string, approve: boolean) => void;
}) {
  const reject = useCallback(() => onDecide(item.userId, false), [onDecide, item.userId]);
  const approve = useCallback(() => onDecide(item.userId, true), [onDecide, item.userId]);
  const age = useMemo(() => waiting(item.uploadedAt), [item.uploadedAt]);

  return (
    <Card className="mb-3">
      <View className="flex-row items-center justify-between">
        <Text variant="bodyStrong">{item.username ?? 'No username'}</Text>
        <Text variant="caption">{age}</Text>
      </View>

      {/* Why this one is here, which is not the same question for every row. A
          score means the model looked and was unsure; no score means it never
          answered, and the re-screen job will pick it up without you. */}
      <Text variant="caption" className="mt-1">
        {item.score == null
          ? 'Not yet screened — the classifier was unreachable. It will be retried automatically.'
          : `Classifier: ${(item.score * 100).toFixed(1)}% likely sexual content`}
      </Text>

      <ReviewImage userId={item.userId} />

      <View className="mt-4 flex-row gap-3">
        <View className="flex-1">
          <Button label="Reject" variant="danger" onPress={reject} disabled={busy} />
        </View>
        <View className="flex-1">
          <Button label="Approve" onPress={approve} disabled={busy} />
        </View>
      </View>

      <Text variant="caption" className="mt-3">
        Approving publishes it. Rejecting keeps it private and leaves the account on
        its monogram. Left alone, an already-screened image publishes itself after
        three days and stays reportable.
      </Text>
    </Card>
  );
});
