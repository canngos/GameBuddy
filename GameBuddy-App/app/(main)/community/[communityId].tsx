import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, View } from 'react-native';
import { communityApi, PAGE_SIZE } from '../../../src/api/community';
import type { Post } from '../../../src/api/types';
import { PostCard } from '../../../src/community/PostCard';
import { usePostLike } from '../../../src/community/usePostLike';
import { useSession } from '../../../src/session/store';
import { useThemeColors } from '../../../src/theme';
import { Avatar, BackHeader, Button, Card, ErrorNotice, Screen, Text } from '../../../src/ui';

/**
 * One community: what it is, who runs it, and everything posted in it.
 *
 * Only reachable as a member. The directory refuses to open a community you have not
 * joined, because posts, comments and the member list are all 403 from the outside —
 * the page would render as three error notices.
 */
export default function CommunityPage() {
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const myId = useSession((s) => s.userId);
  const { communityId } = useLocalSearchParams<{ communityId: string }>();

  const [menuOpen, setMenuOpen] = useState(false);

  // The directory is already cached from the tab root; this reads the one entry out of
  // it rather than adding a per-community endpoint that does not exist.
  const communities = useQuery({ queryKey: ['communities'], queryFn: communityApi.all });
  const community = (communities.data ?? []).find((c) => c.communityId === communityId);

  const members = useQuery({
    queryKey: ['communityMembers', communityId],
    queryFn: () => communityApi.members(communityId),
  });
  const isOwner = (members.data ?? []).some((m) => m.isOwner && m.userId === myId);

  const postsKey = ['communityPosts', communityId];
  const posts = useInfiniteQuery({
    queryKey: postsKey,
    queryFn: ({ pageParam }) => communityApi.posts(communityId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last, all) => (last.length < PAGE_SIZE ? undefined : all.length),
  });

  const like = usePostLike(postsKey);

  const leave = useMutation({
    mutationFn: () => communityApi.leave(communityId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communities'] });
      void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
      router.back();
    },
  });

  const remove = useMutation({
    mutationFn: () => communityApi.remove(communityId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communities'] });
      void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
      router.back();
    },
  });

  function confirmLeave() {
    setMenuOpen(false);
    Alert.alert(
      `Leave ${community?.name ?? 'this community'}?`,
      isOwner
        ? 'You own this community. Leaving hands it to the longest-standing member — and if there is nobody left, it closes and its posts go with it.'
        : 'You can join again later, but you will stop seeing its posts.',
      [
        { text: 'Stay', style: 'cancel' },
        { text: 'Leave', style: 'destructive', onPress: () => leave.mutate() },
      ],
    );
  }

  function confirmDelete() {
    setMenuOpen(false);
    Alert.alert(
      `Delete ${community?.name ?? 'this community'}?`,
      'Every post and comment in it goes too. This cannot be undone.',
      [
        { text: 'Keep it', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => remove.mutate() },
      ],
    );
  }

  const feed = posts.data?.pages.flat() ?? [];
  const busy = leave.isPending || remove.isPending;

  return (
    <Screen edges={['top']} padded={false}>
      <View className="px-6">
        <BackHeader
          title={community?.name ?? 'Community'}
          subtitle={
            members.data
              ? `${members.data.length} member${members.data.length === 1 ? '' : 's'}`
              : undefined
          }
          right={
            <Pressable
              onPress={() => setMenuOpen((open) => !open)}
              accessibilityRole="button"
              accessibilityLabel="Community options"
              hitSlop={10}
              className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
            >
              <View className="gap-1">
                {[0, 1, 2].map((i) => (
                  <View key={i} className="h-1 w-1 rounded-full bg-content" />
                ))}
              </View>
            </Pressable>
          }
        />

        {menuOpen && (
          <Card className="mb-4 gap-2">
            <Button
              label="Members"
              variant="ghost"
              size="md"
              onPress={() => {
                setMenuOpen(false);
                router.push({
                  pathname: '/community/members',
                  params: { communityId, name: community?.name ?? '' },
                } as never);
              }}
            />
            <Button
              label="Leave community"
              variant="ghost"
              size="md"
              disabled={busy}
              onPress={confirmLeave}
            />
            {isOwner && (
              <Button
                label="Delete community"
                variant="danger"
                size="md"
                disabled={busy}
                onPress={confirmDelete}
              />
            )}
          </Card>
        )}

        {(!!leave.error || !!remove.error) && (
          <View className="pb-4">
            <ErrorNotice error={leave.error ?? remove.error} />
          </View>
        )}
      </View>

      <FlatList
        data={feed}
        keyExtractor={(post) => post.postId}
        contentContainerClassName="gap-3 px-6 pb-8"
        showsVerticalScrollIndicator={false}
        onEndReachedThreshold={0.5}
        onEndReached={() => {
          if (posts.hasNextPage && !posts.isFetchingNextPage) void posts.fetchNextPage();
        }}
        refreshing={posts.isRefetching && !posts.isFetchingNextPage}
        onRefresh={() => void posts.refetch()}
        ListHeaderComponent={
          <View className="gap-4 pb-2">
            {!!community?.description && (
              <View className="flex-row items-center gap-3">
                <Avatar
                  source={community.communityAvatar}
                  name={community.name}
                  colorSeed={community.communityId}
                  size={44}
                />
                <Text variant="caption" className="flex-1">
                  {community.description}
                </Text>
              </View>
            )}

            <Button
              label="Write a post"
              onPress={() =>
                router.push({
                  pathname: '/community/compose',
                  params: { communityId, name: community?.name ?? '' },
                } as never)
              }
            />

            {posts.isPending && <ActivityIndicator color={colors.brand} />}
            {posts.error && <ErrorNotice error={posts.error} onRetry={() => posts.refetch()} />}
          </View>
        }
        ListEmptyComponent={
          posts.isPending || posts.error ? null : (
            <Card className="gap-1">
              <Text variant="bodyStrong">Nothing here yet</Text>
              <Text variant="caption">No posts in this community. Write the first one.</Text>
            </Card>
          )
        }
        ListFooterComponent={
          posts.isFetchingNextPage ? (
            <View className="pt-4">
              <ActivityIndicator color={colors.brand} />
            </View>
          ) : null
        }
        renderItem={({ item }: { item: Post }) => (
          <PostCard
            post={item}
            showCommunity={false}
            onToggleLike={() => like.mutate({ postId: item.postId, liked: item.isLiked })}
          />
        )}
      />
    </Screen>
  );
}
