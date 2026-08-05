import { api } from './client';
import type { Comment, Community, CommunityMember, Post } from './types';

/** What the backend's `@PageableDefault` uses, and what "there is another page" is measured against. */
export const PAGE_SIZE = 20;

/**
 * Communities, posts and comments.
 *
 * Two things shape every screen built on this:
 *
 * 1. **Membership gates reading, not just writing.** The directory is public, but a
 *    community's posts, its comments and its member list all answer 403 (NOT_MEMBER,
 *    132) to a non-member. Joining is therefore not a preference, it is the price of
 *    admission, and the UI has to offer it before it can show anything.
 * 2. **Only the feeds are paged.** Comments, members and the directory all come back
 *    whole. That is the backend's shape; nothing here pretends otherwise.
 */
export const communityApi = {
  /** Every community, with `isJoined` resolved for the caller. Not paged, not searchable. */
  all: () =>
    api
      .get<{ communities: Community[] }>('/community/get/communities')
      .then((d) => d.communities ?? []),

  /** The home feed: posts from every community you have joined, newest first. */
  feed: (page: number) =>
    api
      .get<{ posts: Post[] }>(`/community/get/posts?page=${page}&size=${PAGE_SIZE}`)
      .then((d) => d.posts ?? []),

  /**
   * One post.
   *
   * Comes back as a one-element list, sharing `PostResponse` with the feeds so the
   * `isLiked` flag and the avatar resolution are the same code. Undefined when the post
   * has been deleted between listing it and opening it — a POST_NOT_FOUND (133) would be
   * an error banner for something that is simply gone.
   */
  post: (postId: string) =>
    api
      .get<{ posts: Post[] }>(`/community/get/post/${postId}`)
      .then((d) => d.posts?.[0]),

  /** One community's posts. Members only. */
  posts: (communityId: string, page: number) =>
    api
      .get<{
        posts: Post[];
      }>(`/community/get/posts/${communityId}?page=${page}&size=${PAGE_SIZE}`)
      .then((d) => d.posts ?? []),

  /** Members only. The response field is `members`, shared with the two likes endpoints. */
  members: (communityId: string) =>
    api
      .get<{ members: CommunityMember[] }>(`/community/get/members/${communityId}`)
      .then((d) => d.members ?? []),

  comments: (postId: string) =>
    api
      .get<{ comments: Comment[] }>(`/community/get/comments/${postId}`)
      .then((d) => d.comments ?? []),

  create: (name: string, description: string) =>
    api.post<void>('/community/create/community', { name, description }),

  /** Refuses with ALREADY_MEMBER (136) rather than being idempotent. */
  join: (communityId: string) => api.post<void>('/community/join/community', { communityId }),

  /**
   * An owner may leave too — ownership passes to the longest-standing member left, and
   * a community with nobody left is closed and its posts go with it. Worth warning
   * about before the tap, because neither outcome is undoable.
   */
  leave: (communityId: string) => api.post<void>('/community/leave/community', { communityId }),

  /** Owner only — NOT_OWNER (134) otherwise. Takes its id in the body, not the path. */
  remove: (communityId: string) =>
    api.delete<void>('/community/delete/community', { communityId }),

  transferOwnership: (communityId: string, newOwnerId: string) =>
    api.post<void>(`/community/transfer/community/${communityId}/to/${newOwnerId}`),

  createPost: (communityId: string, title: string, body: string) =>
    api.post<void>('/community/create/post', { communityId, title, body }),

  deletePost: (postId: string) => api.delete<void>(`/community/delete/post/${postId}`),

  createComment: (postId: string, message: string) =>
    api.post<void>('/community/create/comment', { postId, message }),

  deleteComment: (commentId: string) =>
    api.delete<void>(`/community/delete/comment/${commentId}`),

  /** Both refuse a second like with ALREADY_LIKED (139), so the caller tracks state. */
  likePost: (postId: string) => api.post<void>(`/community/like/post/${postId}`),
  unlikePost: (postId: string) => api.post<void>(`/community/unlike/post/${postId}`),
  likeComment: (commentId: string) => api.post<void>(`/community/like/comment/${commentId}`),
  unlikeComment: (commentId: string) =>
    api.post<void>(`/community/unlike/comment/${commentId}`),

  /** Reporting is once per person per item: a second one is ALREADY_REPORTED (157). */
  /**
   * Reports a gamer's profile — the picture, the name, what they wrote about themselves.
   *
   * Under `/community` despite not being community content: that is where the moderation
   * queue is, and one queue a moderator checks beats two, one of which they forget.
   * Refused with ALREADY_REPORTED (157) the second time, so nobody can inflate the count
   * against someone they dislike.
   */
  reportProfile: (userId: string, reason: string) =>
    api.post<void>(`/community/report/profile/${userId}`, { reason }),

  reportPost: (postId: string, reason: string) =>
    api.post<void>(`/community/report/post/${postId}`, { reason }),
  reportComment: (commentId: string, reason: string) =>
    api.post<void>(`/community/report/comment/${commentId}`, { reason }),
};
