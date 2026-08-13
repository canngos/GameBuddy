import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, View } from 'react-native';
import { profileApi } from '../../../../src/api/catalogue';
import { communityApi } from '../../../../src/api/community';
import { ApiError, Code } from '../../../../src/api/envelope';
import type { Comment } from '../../../../src/api/types';
import { useThemeColors } from '../../../../src/theme';
import { shortAgo } from '../../../../src/time';
import {
  Avatar,
  BackHeader,
  Button,
  Card,
  cn,
  ErrorNotice,
  ReportSheet,
  Screen,
  Text,
  TextField,
} from '../../../../src/ui';

/** What the report sheet is currently aimed at, if anything. */
type ReportTarget = { kind: 'post' | 'comment'; id: string };

/**
 * A post and its comments.
 *
 * "Is this mine" is decided by comparing usernames, not ids: the post and comment
 * payloads carry an author name and no author id. Usernames are unique — the backend
 * refuses a duplicate — so the comparison is sound, but it is also why nothing here
 * links through to the author's profile.
 */
export default function PostScreen() {
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const { postId } = useLocalSearchParams<{ postId: string }>();

  const [draft, setDraft] = useState('');
  const [reporting, setReporting] = useState<ReportTarget | null>(null);
  const [reported, setReported] = useState(false);

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const post = useQuery({ queryKey: ['post', postId], queryFn: () => communityApi.post(postId) });
  const comments = useQuery({
    queryKey: ['comments', postId],
    queryFn: () => communityApi.comments(postId),
  });

  /** Everything that changes a count somewhere else, refreshed in one place. */
  function refreshLists() {
    void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
    void queryClient.invalidateQueries({ queryKey: ['communityPosts'] });
  }

  const likePost = useMutation({
    mutationFn: async (liked: boolean) => {
      try {
        await (liked ? communityApi.unlikePost(postId) : communityApi.likePost(postId));
      } catch (error) {
        // The server already had the like; our copy was the stale one.
        if (error instanceof ApiError && error.is(Code.ALREADY_LIKED)) return;
        throw error;
      }
    },
    onSuccess: () => void post.refetch(),
  });

  const likeComment = useMutation({
    mutationFn: async ({ commentId, liked }: { commentId: string; liked: boolean }) => {
      try {
        await (liked
          ? communityApi.unlikeComment(commentId)
          : communityApi.likeComment(commentId));
      } catch (error) {
        if (error instanceof ApiError && error.is(Code.ALREADY_LIKED)) return;
        throw error;
      }
    },
    onSuccess: () => void comments.refetch(),
  });

  const comment = useMutation({
    mutationFn: (message: string) => communityApi.createComment(postId, message),
    onSuccess: () => {
      setDraft('');
      void comments.refetch();
      void post.refetch();
      refreshLists();
    },
  });

  const removePost = useMutation({
    mutationFn: () => communityApi.deletePost(postId),
    onSuccess: () => {
      refreshLists();
      router.back();
    },
  });

  const removeComment = useMutation({
    mutationFn: (commentId: string) => communityApi.deleteComment(commentId),
    onSuccess: () => {
      void comments.refetch();
      void post.refetch();
      refreshLists();
    },
  });

  const report = useMutation({
    mutationFn: ({ kind, id, reason }: ReportTarget & { reason: string }) =>
      kind === 'post'
        ? communityApi.reportPost(id, reason)
        : communityApi.reportComment(id, reason),
    onSuccess: () => setReported(true),
    onSettled: () => setReporting(null),
  });

  function confirmDeletePost() {
    Alert.alert('Delete this post?', 'Its comments go with it. This cannot be undone.', [
      { text: 'Keep it', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => removePost.mutate() },
    ]);
  }

  const mine = (username: string) => !!me.data && me.data.username === username;

  return (
    <Screen edges={['top']} padded={false}>
      <View className="px-6">
        <BackHeader title={post.data?.communityName ?? 'Post'} />
      </View>

      {post.isPending && <ActivityIndicator color={colors.primary} />}
      {post.error && (
        <View className="px-6">
          <ErrorNotice error={post.error} onRetry={() => post.refetch()} />
        </View>
      )}
      {!post.isPending && !post.error && !post.data && (
        <View className="px-6">
          <Card className="gap-1">
            <Text variant="bodyStrong">This post is gone</Text>
            <Text variant="caption">Its author or a moderator removed it.</Text>
          </Card>
        </View>
      )}

      {post.data && (
        <FlatList
          data={comments.data ?? []}
          keyExtractor={(item) => item.commentId}
          contentContainerClassName="gap-3 px-6 pb-6"
          showsVerticalScrollIndicator={false}
          ListHeaderComponent={
            <View className="gap-4 pb-2">
              <Card className="gap-3">
                <View className="flex-row items-center gap-3">
                  <Avatar
                    source={post.data.avatar}
                    name={post.data.username}
                    colorSeed={post.data.username}
                    size={40}
                  />
                  <View className="flex-1">
                    <Text variant="bodyStrong">{post.data.username}</Text>
                    <Text variant="caption">{shortAgo(post.data.updatedDate)}</Text>
                  </View>
                </View>

                <View className="gap-1">
                  <Text variant="heading">{post.data.title}</Text>
                  {!!post.data.body && <Text variant="body">{post.data.body}</Text>}
                </View>

                <View className="flex-row items-center gap-2">
                  <Button
                    label={`${post.data.isLiked ? 'Liked' : 'Like'} · ${post.data.likeCount}`}
                    variant={post.data.isLiked ? 'primary' : 'secondary'}
                    size="md"
                    className="flex-1"
                    disabled={likePost.isPending}
                    onPress={() => likePost.mutate(post.data!.isLiked)}
                  />
                  {mine(post.data.username) ? (
                    <Button
                      label="Delete"
                      variant="danger"
                      size="md"
                      loading={removePost.isPending}
                      onPress={confirmDeletePost}
                    />
                  ) : (
                    <Button
                      label="Report"
                      variant="ghost"
                      size="md"
                      onPress={() => setReporting({ kind: 'post', id: postId })}
                    />
                  )}
                </View>
              </Card>

              {!!likePost.error && <ErrorNotice error={likePost.error} />}
              {!!removePost.error && <ErrorNotice error={removePost.error} />}
              {!!report.error && <ErrorNotice error={report.error} />}
              {reported && (
                <Text variant="caption">Reported. A moderator will look at it.</Text>
              )}

              <Text variant="overline">
                {post.data.commentCount === 1 ? '1 COMMENT' : `${post.data.commentCount} COMMENTS`}
              </Text>

              {comments.isPending && <ActivityIndicator color={colors.primary} />}
              {comments.error && (
                <ErrorNotice error={comments.error} onRetry={() => comments.refetch()} />
              )}
            </View>
          }
          ListEmptyComponent={
            comments.isPending || comments.error ? null : (
              <Text variant="caption">Nobody has said anything yet.</Text>
            )
          }
          renderItem={({ item }) => (
            <CommentRow
              comment={item}
              mine={mine(item.username)}
              onToggleLike={() =>
                likeComment.mutate({ commentId: item.commentId, liked: item.isLiked })
              }
              onDelete={() => removeComment.mutate(item.commentId)}
              onReport={() => setReporting({ kind: 'comment', id: item.commentId })}
            />
          )}
        />
      )}

      {post.data && (
        <View className="gap-2 border-t border-line p-3">
          {!!comment.error && <ErrorNotice error={comment.error} />}
          <View className="flex-row items-end gap-2">
            <View className="flex-1">
              <TextField
                value={draft}
                onChangeText={setDraft}
                placeholder="Add a comment"
                multiline
                maxLength={2000}
              />
            </View>
            <Button
              label="Send"
              size="md"
              loading={comment.isPending}
              disabled={draft.trim().length === 0}
              onPress={() => comment.mutate(draft.trim())}
            />
          </View>
        </View>
      )}

      {reporting && (
        <ReportSheet
          what={reporting.kind}
          onCancel={() => setReporting(null)}
          onPick={(reason) => report.mutate({ ...reporting, reason })}
        />
      )}
    </Screen>
  );
}

function CommentRow({
  comment,
  mine,
  onToggleLike,
  onDelete,
  onReport,
}: {
  comment: Comment;
  mine: boolean;
  onToggleLike: () => void;
  onDelete: () => void;
  onReport: () => void;
}) {
  return (
    <View className="flex-row gap-3">
      <Avatar
        source={comment.avatar}
        name={comment.username}
        colorSeed={comment.username}
        size={32}
      />
      <View className="flex-1 gap-1">
        <View className="rounded-card bg-raised px-3 py-2">
          <Text variant="caption" className="text-muted">
            {comment.username} · {shortAgo(comment.updatedDate)}
          </Text>
          <Text variant="body">{comment.message}</Text>
        </View>

        <View className="flex-row items-center gap-4 pl-1">
          <Pressable
            onPress={onToggleLike}
            accessibilityRole="button"
            accessibilityLabel={comment.isLiked ? 'Unlike comment' : 'Like comment'}
            accessibilityState={{ selected: comment.isLiked }}
            hitSlop={10}
            className="active:opacity-60"
          >
            <Text variant="caption" className={cn(comment.isLiked && 'text-accent')}>
              {comment.isLiked ? 'Liked' : 'Like'}
              {comment.likeCount > 0 ? ` · ${comment.likeCount}` : ''}
            </Text>
          </Pressable>

          <Pressable
            onPress={mine ? onDelete : onReport}
            accessibilityRole="button"
            hitSlop={10}
            className="active:opacity-60"
          >
            <Text variant="caption" className={cn(mine && 'text-danger')}>
              {mine ? 'Delete' : 'Report'}
            </Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}
