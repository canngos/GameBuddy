import { create } from 'zustand';

/** The minimum needed to celebrate someone. */
export type MatchedGamer = {
  userId: string;
  username: string;
  avatar: string | null;
  frame: string | null;
};

type CelebrationState = {
  matched: MatchedGamer | null;
  /**
   * Runs on dismiss, once, then is dropped.
   *
   * The deck holds its own `matchedWith` to freeze the swipe gesture while the overlay is
   * up, and that has to be cleared too. Rather than have the overlay know what a deck is,
   * whoever raises the celebration hands over whatever else needs undoing.
   */
  onDismiss: (() => void) | null;
  celebrate: (gamer: MatchedGamer, onDismiss?: () => void) => void;
  dismiss: () => void;
};

/**
 * The match celebration, hoisted out of the deck screen.
 *
 * **A match has two sides and only one of them was being told.** The gamer who swipes last
 * gets the accept response with `matched: true` and sees the overlay. The other one — who
 * liked first, possibly days ago — got nothing in-app at all: the backend has always sent
 * them a push (`DefaultMatchService.notifyMatched` fires for both parties), but with the
 * app open that arrived as a grey system banner while the person who happened to swipe
 * second got fireworks.
 *
 * Putting the state here instead of in `app/(main)/home.tsx` means the celebration can be
 * raised from anywhere — the deck, or a push that lands while somebody is reading their
 * messages — and rendered once, above the tabs.
 *
 * **Deduplication is the reason this is keyed by `userId`.** The swiper gets a push for
 * their own match too, so without it they would see the overlay twice: once from the
 * accept response and once from the notification arriving a moment later.
 */
export const useCelebration = create<CelebrationState>((set, get) => ({
  matched: null,
  onDismiss: null,

  celebrate: (gamer, onDismiss) => {
    // Already celebrating this person. Keep the first one — it owns the `onDismiss` that
    // unfreezes the deck, and replacing it would strand that.
    if (get().matched?.userId === gamer.userId) return;
    set({ matched: gamer, onDismiss: onDismiss ?? null });
  },

  dismiss: () => {
    get().onDismiss?.();
    set({ matched: null, onDismiss: null });
  },
}));
