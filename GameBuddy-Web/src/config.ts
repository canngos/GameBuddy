/**
 * Everything about GameBuddy that the site needs and does not own.
 *
 * One file so that publishing the app, or changing an address, is one edit rather than a
 * search. Nothing here is secret — it is all published on the pages themselves.
 */

export const SITE = {
  domain: 'findgamebuddy.com',
  url: 'https://findgamebuddy.com',
  name: 'GameBuddy',
  /** Used as the `<title>` suffix and in the JSON-LD. */
  tagline: 'Find people who actually play what you play',
} as const;

/**
 * Where to reach a person, split by what the message is.
 *
 * The two addresses are not interchangeable and the split is deliberate:
 *
 * - **support@** is day-to-day help — the address someone uses when the app misbehaves.
 * - **contact@** is legal and data protection. A GDPR access or erasure request carries a
 *   one-month statutory deadline, so it must not land in a help queue behind feature
 *   questions. The terms and the privacy policy both name this one.
 */
export const CONTACT = {
  support: 'support@findgamebuddy.com',
  legal: 'contact@findgamebuddy.com',
} as const;

/**
 * Store links, empty until the listings exist.
 *
 * **Empty on purpose, and the components read them rather than assuming.** A made-up store
 * URL would be a dead link on the first page a visitor sees, which is worse than an honest
 * "coming soon". `StoreButtons` renders the waiting state while these are blank and becomes
 * a real link the moment either is filled in — no other file changes.
 */
export const STORES = {
  play: '',
  appStore: '',
} as const;

/** The operator, as named in the terms and the privacy policy. */
export const OPERATOR = {
  name: 'Can Baturlar',
  country: 'Finland',
} as const;

/** Minimum age. Enforced by the backend from a date of birth; stated here so it is public. */
export const MIN_AGE = 18;
