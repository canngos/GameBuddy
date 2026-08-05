import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, ScrollView, View } from 'react-native';
import { communityApi, PAGE_SIZE } from '../../../src/api/community';
import type { Community, Post } from '../../../src/api/types';
import { PostCard } from '../../../src/community/PostCard';
import { usePostLike } from '../../../src/community/usePostLike';
import { useThemeColors } from '../../../src/theme';
import { Avatar, Button, Card, ErrorNotice, Screen, Text } from '../../../src/ui';

const FEED_KEY = ['communityFeed'];

/**
 * The Community tab: what the communities you have joined are talking about.
 *
 * The feed is empty until you join something, and joining is the only way to see any
 * post at all — the backend refuses a non-member's read with 403. So an account with no
 * communities gets the directory instead of an empty feed with nothing to act on.
 */
export default function CommunityHome() {
  const router = useRouter();
  const colors = useThemeColors();

  const communities = useQuery({ queryKey: ['communities'], queryFn: communityApi.all });

  const feed = useInfiniteQuery({
    queryKey: FEED_KEY,
    queryFn: ({ pageParam }) => communityApi.feed(pageParam),
    initialPageParam: 0,
    // There is no total in the response, so a short page is the only end-of-list signal
    // there is. A full last page costs one extra request that comes back empty.
    getNextPageParam: (last, all) => (last.length < PAGE_SIZE ? undefined : all.length),
  });

  const like = usePostLike(FEED_KEY);
  const joined = (communities.data ?? []).filter((c) => c.isJoined);
  const posts = feed.data?.pages.flat() ?? [];

  return (
    <Screen edges={['top']} padded={false}>
      <FlatList
        data={posts}
        keyExtractor={(post) => post.postId}
        contentContainerClassName="gap-3 px-6 pb-8"
        showsVerticalScrollIndicator={false}
        onEndReachedThreshold={0.5}
        onEndReached={() => {
          if (feed.hasNextPage && !feed.isFetchingNextPage) void feed.fetchNextPage();
        }}
        refreshing={feed.isRefetching && !feed.isFetchingNextPage}
        onRefresh={() => {
          void feed.refetch();
          void communities.refetch();
        }}
        ListHeaderComponent={
          <View className="gap-5 pb-2">
            <View className="flex-row items-end justify-between pt-8">
              <View className="gap-1">
                <Text variant="overline">COMMUNITY</Text>
                <Text variant="title">What's going on</Text>
              </View>
              <Pressable
                onPress={() => router.push('/community/new' as never)}
                accessibilityRole="button"
                accessibilityLabel="New community"
                className="h-11 w-11 items-center justify-center rounded-full bg-brand active:opacity-80"
              >
                <View className="h-4 w-0.5 rounded-full bg-white" />
                <View className="absolute h-0.5 w-4 rounded-full bg-white" />
              </Pressable>
            </View>

            {communities.error && (
              <ErrorNotice error={communities.error} onRetry={() => communities.refetch()} />
            )}

            {joined.length > 0 && <JoinedStrip communities={joined} />}

            {feed.error && <ErrorNotice error={feed.error} onRetry={() => feed.refetch()} />}
            {(feed.isPending || communities.isPending) && (
              <ActivityIndicator color={colors.brand} />
            )}
          </View>
        }
        ListEmptyComponent={
          feed.isPending || communities.isPending || !!feed.error ? null : (
            <EmptyFeed hasJoined={joined.length > 0} />
          )
        }
        ListFooterComponent={
          <View className="gap-4 pt-4">
            {feed.isFetchingNextPage && <ActivityIndicator color={colors.brand} />}
            {posts.length > 0 && (
              <Button
                label="Find more communities"
                variant="secondary"
                onPress={() => router.push('/community/browse' as never)}
              />
            )}
          </View>
        }
        renderItem={({ item }: { item: Post }) => (
          <PostCard
            post={item}
            onToggleLike={() => like.mutate({ postId: item.postId, liked: item.isLiked })}
          />
        )}
      />
    </Screen>
  );
}

/** The communities you are in, as a row you can scroll sideways. Tapping one opens it. */
function JoinedStrip({ communities }: { communities: Community[] }) {
  const router = useRouter();

  return (
    <View className="gap-2">
      <Text variant="overline">YOUR COMMUNITIES</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerClassName="gap-4 pr-6"
      >
        {communities.map((community) => (
          <Pressable
            key={community.communityId}
            onPress={() =>
              router.push({
                pathname: '/community/[communityId]',
                params: { communityId: community.communityId },
              } as never)
            }
            accessibilityRole="button"
            accessibilityLabel={community.name}
            className="w-16 items-center gap-1.5 active:opacity-70"
          >
            <Avatar
              source={community.communityAvatar}
              name={community.name}
              colorSeed={community.communityId}
              size={52}
            />
            <Text variant="caption" numberOfLines={1} className="text-center">
              {community.name}
            </Text>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

function EmptyFeed({ hasJoined }: { hasJoined: boolean }) {
  const router = useRouter();

  return (
    <Card className="gap-3">
      <Text variant="bodyStrong">
        {hasJoined ? 'Nothing posted yet' : 'You have not joined anything'}
      </Text>
      <Text variant="caption">
        {hasJoined
          ? 'Your communities are quiet. You could be the one to break the silence.'
          : 'Communities are where more than two people talk at once. Join one to see its posts — they are only readable from the inside.'}
      </Text>
      <Button
        label={hasJoined ? 'Browse communities' : 'Find a community'}
        variant="secondary"
        onPress={() => router.push('/community/browse' as never)}
      />
    </Card>
  );
}
