import { Linking } from 'react-native';

/**
 * Where the terms and the privacy policy live, and the version being agreed to.
 *
 * Both stores require a privacy policy reachable from a URL on the listing page, and Apple
 * requires the terms to be reachable from inside the app as well — not only from the
 * listing, which somebody who already installed the app will never see again. Hence
 * {@link openTerms} and {@link openPrivacy}, wired into both the registration screen and
 * Settings.
 *
 * `TERMS_VERSION` mirrors `TermsPolicy.CURRENT_VERSION` on the server. It is not sent —
 * the server stamps its own value, because a client is not a trustworthy witness to what
 * it displayed. It is here so the two can be compared when they drift.
 *
 * The source documents are in `documentation/legal/`, and `GameBuddy-Web` renders those exact
 * files at the URLs below — imported, not copied, so the app, the website and the version the
 * server records cannot drift apart.
 *
 * These defaults used to point at `gamebuddy.app`, a domain nobody involved owned, so both
 * links resolved to nothing. That was the launch blocker this file's earlier note described;
 * it is closed now that `findgamebuddy.com` exists.
 */
export const TERMS_URL = process.env.EXPO_PUBLIC_TERMS_URL ?? 'https://findgamebuddy.com/terms';
export const PRIVACY_URL =
  process.env.EXPO_PUBLIC_PRIVACY_URL ?? 'https://findgamebuddy.com/privacy';

export const TERMS_VERSION = '2026-08-07';

export function openTerms() {
  return Linking.openURL(TERMS_URL);
}

export function openPrivacy() {
  return Linking.openURL(PRIVACY_URL);
}
