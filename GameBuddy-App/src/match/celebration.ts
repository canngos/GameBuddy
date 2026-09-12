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
  /**
   * Everyone celebrated since sign-in, by id. Kept after dismissal — see the header on why
   * "is the overlay up right now" was not a wide enough net.
   */
  shown: ReadonlySet<string>;
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
 *
 * **"A moment later" is not bounded, which is why `shown` outlives the overlay.** The
 * dedupe used to be "is this person's overlay up right now", and that only holds if the
 * push lands while the gamer is still looking at it. Tap "Send a message" straight away
 * and the overlay is gone before FCM has delivered anything — so the push then raised a
 * second celebration on top of the conversation that was opened *from the first one*,
 * seconds into typing. The set remembers everyone celebrated since sign-in instead; a
 * push for a match already shown is dropped whenever it turns up.
 */
export const useCelebration = create<CelebrationState>((set, get) => ({
  matched: null,
  onDismiss: null,
  shown: new Set(),

  celebrate: (gamer, onDismiss) => {
    const { matched, onDismiss: current, shown } = get();

    // Already celebrating this person. Keep the first one on screen, but never strand the
    // cleanup: the deck's raise carries the callback that unfreezes it, and if the push
    // got here first (no callback) the deck's has to be adopted or the gesture stays dead
    // after dismissal.
    if (matched?.userId === gamer.userId) {
      if (onDismiss && !current) set({ onDismiss });
      return;
    }

    // Already celebrated and dismissed. Nothing to show — but whatever the caller wanted
    // undone on dismissal is undone now, for the same reason as above.
    if (shown.has(gamer.userId)) {
      onDismiss?.();
      return;
    }

    set({
      matched: gamer,
      onDismiss: onDismiss ?? null,
      shown: new Set(shown).add(gamer.userId),
    });
  },

  dismiss: () => {
    get().onDismiss?.();
    set({ matched: null, onDismiss: null });
  },
}));
