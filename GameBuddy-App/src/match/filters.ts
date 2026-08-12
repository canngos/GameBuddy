import type { PlatformId } from '../profile/platforms';

/**
 * What the deck has been narrowed to.
 *
 * Mirrors `FeedFilters` on the backend field for field, including the rule that decides
 * whether a request costs an entitlement. That rule is duplicated here on purpose and it
 * has to stay in step: the client uses it to decide whether to show the lock, the server
 * uses it to decide whether to refuse. The server is the one that matters — this copy only
 * exists so a free gamer is told before the request rather than by a rejected one.
 *
 * `country` is a code, and today it is only ever the gamer's own. The backend compares it
 * to a candidate's country as an exact (case-insensitive) string, so anything else would
 * need a picker of every country in the catalogue plus agreement about how they are
 * spelled. "Near me" is the thing people actually want and it is one toggle.
 */
export type FeedFilters = {
  gameId: string | null;
  country: string | null;
  onlineNow: boolean;
  /**
   * A single `PlatformId`, even though a gamer holds several.
   *
   * Asking "who can I play with on my Switch tonight" is one platform at a time — a
   * multi-select here would mean "on any of these", which is close enough to no filter
   * that it would not be worth the taps.
   *
   * The server matches it against the candidate's whole set, so somebody on PC and Switch
   * appears under both. Candidates who never said are never excluded; see `FeedFilters`
   * on the backend for why silence must not be read as a no.
   */
  platform: PlatformId | null;
};

export const NO_FILTERS: FeedFilters = {
  gameId: null,
  country: null,
  onlineNow: false,
  platform: null,
};

/**
 * Whether this asks for anything at all. Only a narrowed feed needs Gold.
 *
 * `onlineNow: false` is the absence of a filter, not a filter — matching
 * `FeedFilters.narrowing()` server-side. Getting this wrong in the stricter direction
 * would put a lock on the ordinary feed that every free account is entitled to.
 */
export function isNarrowing(filters: FeedFilters): boolean {
  return (
    filters.gameId !== null ||
    filters.country !== null ||
    filters.onlineNow ||
    filters.platform !== null
  );
}

/** How many filters are on, for the badge on the deck's filter button. */
export function activeCount(filters: FeedFilters): number {
  return (
    (filters.gameId !== null ? 1 : 0) +
    (filters.country !== null ? 1 : 0) +
    (filters.onlineNow ? 1 : 0) +
    (filters.platform !== null ? 1 : 0)
  );
}
