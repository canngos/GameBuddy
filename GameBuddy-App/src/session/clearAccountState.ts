import { useActiveConversation } from '../chat/activeConversation';
import { useCelebration } from '../match/celebration';
import { queryClient } from '../query';
import { useToasts } from '../ui/toast';

/**
 * Forgets everything the signed-in account left in memory.
 *
 * **This is the fix for signing in as somebody else and still seeing the first account.**
 * Signing out cleared the token, the stored user id and the keychain — and nothing else.
 * The React Query cache is a module-level singleton that outlives any screen, so every
 * answer fetched for the previous account was still sitting in it: `['me']`, `['inbox']`,
 * `['friends']`, `['matches']`, `['cosmetics']`, every `['gamer', id]`. The next account
 * mounted the same screens, React Query served the cached data first as it is designed to,
 * and the app showed the previous person's profile, conversations and coins.
 *
 * It was not even self-correcting. The default `staleTime` is five minutes, so a refetch
 * was not due; and a key like `['gamer', id]` is never re-requested at all unless something
 * asks for that id again. Somebody could sign in and read the wrong inbox indefinitely.
 *
 * `clear()` rather than `invalidateQueries`: invalidation marks data stale but *keeps it*
 * and hands it to the next observer while it refetches, which is exactly the frame where
 * the wrong account's data is on screen. Clearing removes it, so a screen renders its
 * loading state and then the truth.
 *
 * The three stores below hold the same kind of leftovers outside the query cache: a match
 * celebration waiting to be shown, the conversation the previous account had open (which
 * would suppress the new account's notifications for that person), and any queued toasts.
 *
 * The chat socket needs nothing here — it is keyed on the token and disconnects by itself
 * when that goes null. See `ChatSocketProvider`.
 */
export function clearAccountState() {
  queryClient.clear();
  // `setState`, not the stores' own actions: `dismiss()` runs the deck's unfreeze callback
  // and `close(id)` only clears a matching id. Neither is meaningful once the account is
  // gone — what is wanted here is the empty state, unconditionally.
  useCelebration.setState({ matched: null, onDismiss: null });
  useActiveConversation.setState({ friendId: null });
  useToasts.getState().clear();
}
