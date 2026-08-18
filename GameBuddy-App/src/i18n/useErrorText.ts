import { ApiError } from '../api/envelope';
import { useT } from './useT';

/**
 * A failure, in the reader's language.
 *
 * **The backend answers in English and always will**, so the translation happens here,
 * keyed by the numeric code the envelope already carries. Three reasons this beats
 * teaching the API `Accept-Language`:
 *
 * - The app already branches on those codes (`Code.SUBSCRIPTION_REQUIRED` and friends in
 *   `src/api/envelope.ts`), so the mapping is a table next to logic that already exists.
 * - Transport failures — no network, DNS, a timeout — are invented *here*, by
 *   `src/api/client.ts`. The server cannot localise a message it never sent, so
 *   server-side translation would leave half the errors in English anyway. One mechanism
 *   for all of them beats two that disagree.
 * - Adding a language stays a client release. A translation is not a reason to deploy
 *   the backend.
 *
 * **Unmapped codes fall back to the server's own text.** That is deliberate: a new
 * backend error appearing in English is a small blemish, whereas swallowing it into a
 * generic "something went wrong" would throw away the one sentence explaining what
 * actually happened. Detail messages the server composes at runtime (`"the team already
 * has 3 players"`) reach the reader the same way.
 */
export function useErrorText(): (error: unknown) => string {
  const t = useT();

  return (error: unknown) => {
    // Matched by name rather than instanceof, so this file does not import the billing
    // module (which lazy-loads native store code) just to recognise its error type.
    if (error instanceof Error && error.name === 'StoreUnavailableError') {
      const reason = (error as { reason?: string }).reason;
      if (reason === 'notInBuild') return t.billing.storeNotInBuild;
      if (reason === 'productMissing') return t.billing.storePlanMissing;
      return t.billing.storeUnavailable;
    }

    if (!(error instanceof ApiError)) return t.errors.generic;

    if (error.code === ApiError.NETWORK) return t.errors.network;
    if (error.isSessionExpired) return t.errors.sessionExpired;

    const translated = (t.errors.byCode as Record<string, string | undefined>)[error.code];
    if (translated) return translated;

    // Whatever the server said, which is at least specific — see the note above.
    return error.message || t.errors.generic;
  };
}
