import { useRouter } from 'expo-router';
import { Pressable, View } from 'react-native';
import type { Post } from '../api/types';
import { useThemeColors } from '../theme';
import { shortAgo } from '../time';
import { Avatar, Card, cn, Text } from '../ui';

/**
 * Filled or hollow, as one style rather than two class sets.
 *
 * Both keys are always present and only the values move. A class that appears and
 * disappears on tap is what blanked the avatar picker; see `src/ui/Avatar.tsx`.
 */
function fill(liked: boolean, accent: string, outline: string) {
  return {
    backgroundColor: liked ? accent : 'transparent',
    borderWidth: liked ? 0 : 2,
    borderColor: liked ? 'transparent' : outline,
  };
}

type PostCardProps = {
  post: Post;
  /** Hidden on a community's own page, where every post is from the same place. */
  showCommunity?: boolean;
  onToggleLike: () => void;
};

/**
 * One post in a list.
 *
 * The whole card opens the post; only the like control is separately tappable. Comments
 * live on the detail screen rather than being previewed here — a feed that shows two
 * comments per post is a feed you cannot scan.
 */
export function PostCard({ post, showCommunity = true, onToggleLike }: PostCardProps) {
  const router = useRouter();

  return (
    <Pressable
      onPress={() =>
        router.push({
          pathname: '/community/post/[postId]',
          params: { postId: post.postId },
        } as never)
      }
      accessibilityRole="button"
      accessibilityLabel={`Post: ${post.title}`}
      className="active:opacity-70"
    >
      <Card className="gap-3">
        <View className="flex-row items-center gap-3">
          <Avatar source={post.avatar} name={post.username} colorSeed={post.username} size={36} />
          <View className="flex-1">
            <Text variant="bodyStrong">{post.username}</Text>
            <Text variant="caption" numberOfLines={1}>
              {[showCommunity ? post.communityName : null, shortAgo(post.updatedDate)]
                .filter(Boolean)
                .join(' · ')}
            </Text>
          </View>
        </View>

        <View className="gap-1">
          <Text variant="bodyStrong">{post.title}</Text>
          {!!post.body && (
            <Text variant="caption" numberOfLines={3}>
              {post.body}
            </Text>
          )}
        </View>

        <View className="flex-row items-center gap-5">
          <LikeButton liked={post.isLiked} count={post.likeCount} onPress={onToggleLike} />
          <View className="flex-row items-center gap-1.5">
            <CommentGlyph />
            <Text variant="caption">{post.commentCount}</Text>
          </View>
        </View>
      </Card>
    </Pressable>
  );
}

function LikeButton({
  liked,
  count,
  onPress,
}: {
  liked: boolean;
  count: number;
  onPress: () => void;
}) {
  const colors = useThemeColors();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={liked ? 'Unlike' : 'Like'}
      accessibilityState={{ selected: liked }}
      hitSlop={10}
      className="flex-row items-center gap-1.5 active:opacity-60"
    >
      {/* A heart from a rotated square and two discs: no icon font, and it reads
          correctly at this size in both themes.

          The filled and hollow states are one style with constant keys rather than two
          class sets, because a border that appears and disappears on tap is exactly what
          blanked the avatar picker — see src/ui/Avatar.tsx. This is the other tap target
          in the app shaped the same way. */}
      <View className="h-4 w-4 items-center justify-center">
        <View className="h-2.5 w-2.5 rotate-45" style={fill(liked, colors.accent, colors.muted)} />
        <View
          className="absolute left-0 top-0.5 h-2 w-2 rounded-full"
          style={fill(liked, colors.accent, colors.muted)}
        />
        <View
          className="absolute right-0 top-0.5 h-2 w-2 rounded-full"
          style={fill(liked, colors.accent, colors.muted)}
        />
      </View>
      <Text variant="caption" style={{ color: liked ? colors.accent : colors.muted }}>
        {count}
      </Text>
    </Pressable>
  );
}

function CommentGlyph() {
  return (
    <View className="h-4 w-4 justify-center">
      <View className="h-3 w-4 rounded-[3px] border-2 border-muted" />
      <View className="absolute -bottom-0.5 left-1 h-1.5 w-1.5 rotate-45 border-b-2 border-l-2 border-muted bg-surface" />
    </View>
  );
}
