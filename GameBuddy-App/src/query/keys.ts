/**
 * Every query key root the app actually reads.
 *
 * **This exists because of a bug that shipped and was invisible.** Five places invalidated
 * `['conversations']` — the match celebration, the match push handler, and three entries in
 * the in-app notification table — and no `useQuery` anywhere used that key. The inbox is
 * `['inbox']`. So a foreground MESSAGE push refreshed nothing at all, and there was no way
 * to notice: `invalidateQueries` on a key nothing reads is silent and successful.
 *
 * A typo in a string cannot be caught. A typo in a member of this union is a compile error,
 * which is the only reason this file is worth its own existence.
 *
 * **Adding a query means adding its root here.** The list is the contract; the point is
 * that it is impossible to invalidate something nobody fetches.
 */
export const QUERY_KEYS = [
  'admin',
  'admirers',
  'allowance',
  'avatars',
  'badges',
  'blocked',
  'conversation',
  'cosmetics',
  'earn',
  'friendRequests',
  'friends',
  'gamer',
  'games',
  'inbox',
  'keywords',
  'lobbies',
  'lobby',
  'lobby-messages',
  'matches',
  'me',
  'my-lobbies',
  'notificationPreferences',
  'presence',
  'recommendations',
  'sentRequests',
  'subscription',
] as const;

export type QueryKeyRoot = (typeof QUERY_KEYS)[number];
