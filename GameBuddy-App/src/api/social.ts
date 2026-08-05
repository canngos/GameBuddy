import { api } from './client';
import type { GamerSummary } from './types';

/**
 * Friends, requests and blocking.
 *
 * Every one of these takes `{ userId }` in the body rather than a path parameter,
 * including the ones that read as a path — that is the backend's shape (`FriendRequest`),
 * not a choice made here.
 */
export const socialApi = {
  friends: () =>
    api.get<{ friends: GamerSummary[] }>('/application/get/friends').then((d) => d.friends ?? []),

  /** Requests waiting on this gamer's answer. */
  pendingRequests: () =>
    api
      .get<{ friends: GamerSummary[] }>('/application/get/requests/friends')
      .then((d) => d.friends ?? []),

  /**
   * Requests this gamer has sent and nobody has answered yet.
   *
   * Without it there is no way to tell "you have not asked" from "you asked and they
   * have not replied" — the two states a friend button has to render differently, and
   * the second one is where tapping again returns ALREADY_SENT_REQUEST (120).
   */
  sentRequests: () =>
    api
      .get<{ friends: GamerSummary[] }>('/application/get/sent/friends')
      .then((d) => d.friends ?? []),

  /**
   * Blocked gamers. Note the field is `friends` — all of these endpoints share one
   * `FriendsResponse`, so the blocked list arrives under that name too.
   */
  blocked: () =>
    api
      .get<{ friends: GamerSummary[] }>('/application/get/blocked/friends')
      .then((d) => d.friends ?? []),

  accept: (userId: string) => api.post<void>('/application/accept/friend', { userId }),
  reject: (userId: string) => api.post<void>('/application/reject/friend', { userId }),
  remove: (userId: string) => api.post<void>('/application/remove/friend', { userId }),

  /**
   * Only a matched gamer can be added — the backend refuses anything else with
   * FRIEND_REQUIRES_MATCH (155). Friendship is the tier above a match, not a parallel
   * one.
   */
  sendRequest: (userId: string) => api.post<void>('/application/send/friend', { userId }),

  /**
   * Blocking is mutual and total: a blocked gamer stops appearing in the feed, cannot
   * be paired, and cannot send messages, in both directions.
   */
  block: (userId: string) => api.post<void>('/application/block/friend', { userId }),
  unblock: (userId: string) => api.post<void>('/application/unblock/friend', { userId }),
};
