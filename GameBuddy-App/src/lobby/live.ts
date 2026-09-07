/**
 * Query options for anything that describes a lobby right now.
 *
 * **The app-wide default is `staleTime: 5 * 60 * 1000`, and for a lobby that is wrong.**
 * Five minutes is the right answer for the catalogue it was written for — games and
 * keywords change when we deploy, not while somebody scrolls them. A lobby is the opposite:
 * its roster, its status and its chat change while you are looking at the screen, and
 * usually because somebody else did something.
 *
 * Served from that five-minute cache, the lobby produced exactly the reports we got from
 * testers: joining and still being shown "withdraw request", messages that never arrived,
 * an unread badge that would not clear. Backgrounding the app did not help either —
 * `focusManager` is wired, but refetch-on-focus only refetches queries that are *stale*, so
 * a query fetched two minutes ago stays put. Only a cold start fixed it, because that is
 * the one thing that empties the cache.
 *
 * Zero does not mean "poll". It means the cached answer is shown immediately and checked
 * in the background whenever a screen mounts or the app comes forward — which for live
 * data is the least surprising behaviour there is. The socket still does the fast path;
 * this is what catches everything the socket missed while the screen was closed.
 */
export const LIVE_QUERY = { staleTime: 0 } as const;
