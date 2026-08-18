import { NO_FILTERS, type FeedFilters } from '../match/filters';
import { api } from './client';
import type { Candidate, Consumables, LikedYou, Rewind, SwipeAllowance } from './types';

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
    // partial polyfill, and this is four optional values.
    const query = [
      filters.gameId ? `gameId=${encodeURIComponent(filters.gameId)}` : null,
      filters.country ? `country=${encodeURIComponent(filters.country)}` : null,
      filters.onlineNow ? 'onlineNow=true' : null,
      filters.platform ? `platform=${encodeURIComponent(filters.platform)}` : null,
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
  accept: (userId: string, superLike = false) =>
    api.post<{ matched: boolean; message: string }>('/match/accept', { userId, superLike }),

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

  /**
   * Takes back the last swipe and returns whoever came back.
   *
   * The candidate comes back in full so the deck can put them straight on top without
   * refetching the feed — which would also record a second impression for somebody who
   * was already shown once, quietly teaching the model about a view that never happened.
   *
   * Refused with 171 when there is nothing to undo, 172 when the like was already
   * answered (that match is not only yours to reverse), and 129 when the coins are short.
   */
  rewind: () => api.post<Rewind>('/match/rewind'),

  /**
   * Buys a consumable with coins.
   *
   * Not UNLOCK_ADMIRER — that one is bought against a person, so it has its own call.
   */
  buyConsumable: (item: 'SUPER_LIKE' | 'EXTRA_LIKES') =>
    api.post<Consumables>(`/match/consumable/${item}`),

  /**
   * Pays coins to reveal one admirer, and returns the refreshed list.
   *
   * Refused with 165 when it was already bought and 148 on a tier that already shows
   * everybody — neither charges. The count stays honest either way: revealing one of four
   * still reports four.
   */
  unlockAdmirer: (userId: string) => api.post<LikedYou>(`/match/liked-you/${userId}/unlock`),

  /**
   * Reveals one hidden admirer, chosen by the server.
   *
   * The client cannot name one: a locked admirer arrives with no id at all, and sending
   * ids so the client could choose would give the paid feature away — an id is enough to
   * fetch a public profile.
   */
  unlockNextAdmirer: () => api.post<LikedYou>('/match/liked-you/unlock-next'),
};
