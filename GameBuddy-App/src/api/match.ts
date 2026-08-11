import { NO_FILTERS, type FeedFilters } from '../match/filters';
import { api } from './client';
import type { Candidate, LikedYou, SwipeAllowance } from './types';

export const matchApi = {
  /**
   * The ranked feed. Up to 50 candidates, already filtered server-side for age band,
   * blocks, banned accounts and anyone this gamer has decided on recently.
   *
   * Slow by comparison with everything else: the backend calls the Python model
   * before it can answer.
   *
   * Narrowing filters are a Gold entitlement and the server enforces it: a request that
   * asks for any of them from a free account is refused with 159 SUBSCRIPTION_REQUIRED
   * rather than quietly returning an unfiltered deck. An unset filter is omitted from the
   * query string entirely — sending `onlineNow=false` would read as a filter to nobody's
   * benefit and, on the backend's own rule, is not one.
   */
  recommendations: (filters: FeedFilters = NO_FILTERS) => {
    // Built by hand rather than with URLSearchParams: React Native's implementation is a
    // partial polyfill, and this is three optional values.
    const query = [
      filters.gameId ? `gameId=${encodeURIComponent(filters.gameId)}` : null,
      filters.country ? `country=${encodeURIComponent(filters.country)}` : null,
      filters.onlineNow ? 'onlineNow=true' : null,
    ]
      .filter(Boolean)
      .join('&');

    return api
      .get<{ recommendedGamers: Candidate[] }>(
        `/match/get/recommendations${query ? `?${query}` : ''}`,
      )
      .then((d) => d.recommendedGamers ?? []);
  },

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
