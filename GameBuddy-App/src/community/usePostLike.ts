import {
  useMutation,
  useQueryClient,
  type InfiniteData,
  type QueryKey,
} from '@tanstack/react-query';
import { communityApi } from '../api/community';
import { ApiError, Code } from '../api/envelope';
import type { Post } from '../api/types';

/**
 * Liking a post, applied optimistically to whichever list the post is being shown in.
 *
 * A like has to feel instant — it is a tap with no other feedback — so the cached page
 * is patched first and only rolled back if the server disagrees. Nothing is invalidated
 * afterwards on purpose: refetching would pull down every page of an infinite list to
 * correct one integer, and the count is already right locally.
 *
 * `ALREADY_LIKED` is treated as success rather than as an error. It means the server
 * already had the like and our copy was the stale one, so the optimistic state was
 * correct and rolling it back would put the wrong thing on screen.
 */
export function usePostLike(queryKey: QueryKey) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ postId, liked }: { postId: string; liked: boolean }) => {
      try {
        await (liked ? communityApi.unlikePost(postId) : communityApi.likePost(postId));
      } catch (error) {
        if (error instanceof ApiError && error.is(Code.ALREADY_LIKED)) return;
        throw error;
      }
    },

    onMutate: async ({ postId, liked }) => {
      // Stop an in-flight fetch from landing on top of the patch below.
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<InfiniteData<Post[]>>(queryKey);

      queryClient.setQueryData<InfiniteData<Post[]>>(queryKey, (data) =>
        data === undefined
          ? data
          : {
              ...data,
              pages: data.pages.map((page) =>
                page.map((post) =>
                  post.postId === postId
                    ? {
                        ...post,
                        isLiked: !liked,
                        likeCount: Math.max(0, post.likeCount + (liked ? -1 : 1)),
                      }
                    : post,
                ),
              ),
            },
      );

      return { previous };
    },

    onError: (_error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
    },
  });
}
