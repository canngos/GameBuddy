import { api } from './client';
import type { Candidate, LikedYou, SwipeAllowance } from './types';

export const matchApi = {
  /**
   * The ranked feed. Up to 50 candidates, already filtered server-side for age band,
   * blocks, banned accounts and anyone this gamer has decided on recently.
   *
   * Slow by comparison with everything else: the backend calls the Python model
   * before it can answer.
   */
  recommendations: () =>
    api
      .get<{ recommendedGamers: Candidate[] }>('/match/get/recommendations')
      .then((d) => d.recommendedGamers ?? []),

  /**
   * Swipe right. `matched` is true when the other side had already accepted, so a
   * conversation is now open.
   */
  accept: (userId: string) =>
    api.post<{ matched: boolean; message: string }>('/match/accept', { userId }),

  /**
   * Swipe left. Not permanent — a decline stops hiding its target after thirty days,
   * so the pool refills rather than shrinking to nothing.
   */
  decline: (userId: string) => api.post<void>('/match/decline', { userId }),

  /** Today's remaining budget. Fetched up front so the limit can be shown, not hit. */
  allowance: () => api.get<SwipeAllowance>('/match/get/accept-allowance'),

  /** Count is always returned; identities only on a paid tier. */
  likedYou: () => api.get<LikedYou>('/match/get/liked-you'),

  /** Mutual matches — the people who can be chatted with. */
  matches: () =>
    api
      .get<{ recommendedGamers: Candidate[] }>('/match/get/matches')
      .then((d) => d.recommendedGamers ?? []),
};
