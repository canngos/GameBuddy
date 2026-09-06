import { create } from 'zustand';
import { secureStorage } from '../session/storage';

const SEEN_KEY = 'gamebuddy.hintsSeen';

/**
 * The first-use hints, each shown once, where the thing they describe actually is.
 *
 * These are not a second tutorial. The five-tab tour (`src/tutorial/`) answers "what is in
 * this app"; these answer "what does this control do", at the moment somebody is looking at
 * it. A tour cannot teach a swipe — the deck is not on screen while it is running, and a
 * gesture read about three screens earlier is a gesture nobody remembers.
 *
 * Ordered as somebody meets them, which is also the order they are allowed to interrupt in:
 * the deck first, because that is where the app opens.
 */
export const HINT_KEYS = [
  'deck.swipe',
  'deck.filter',
  'deck.superLike',
  'lobby.create',
  'chat.composer',
] as const;

export type HintKey = (typeof HINT_KEYS)[number];

type HintState = {
  /** Which hints have been dismissed. Persisted as a JSON array. */
  seen: HintKey[];
  /** False until storage has been read, so nothing flashes on a cold start. */
  hydrated: boolean;
  /**
   * The one hint currently on screen, or null.
   *
   * Held centrally rather than per screen because two of these live on the same screen and
   * become eligible within a second of each other. Two spotlights over one deck is not
   * twice the guidance; it is a screen nobody can use.
   */
  active: HintKey | null;

  load: () => Promise<void>;
  /** Takes the single slot, if it is free. Returns whether this hint may now draw. */
  claim: (key: HintKey) => boolean;
  /** Gives the slot back without marking the hint seen — for a screen unmounting. */
  release: (key: HintKey) => void;
  /** Marks it seen and frees the slot. This is what a tap on "Got it" does. */
  dismiss: (key: HintKey) => void;
  /** Shows them all again. Paired with replaying the tutorial from Settings. */
  resetAll: () => void;
};

/**
 * Device-scoped, like `gamebuddy.tutorialSeen` and the theme: the key carries no user id and
 * signing out does not clear it (`clearAccountState` leaves both alone).
 *
 * That is the right trade for this kind of state. What these teach is how the app works, not
 * anything about an account — somebody who has already learned to swipe has learned it on
 * this phone, and re-teaching them because they signed into a second account would be the
 * more annoying of the two mistakes.
 */
export const useHints = create<HintState>((set, get) => ({
  seen: [],
  hydrated: false,
  active: null,

  load: async () => {
    const stored = await secureStorage.get(SEEN_KEY);
    set({ seen: parse(stored), hydrated: true });
  },

  claim: (key) => {
    const { active, seen } = get();
    if (seen.includes(key)) return false;
    if (active === key) return true;
    if (active !== null) return false;
    set({ active: key });
    return true;
  },

  release: (key) => {
    if (get().active === key) set({ active: null });
  },

  dismiss: (key) => {
    const { seen, active } = get();
    const next = seen.includes(key) ? seen : [...seen, key];
    set({ seen: next, active: active === key ? null : active });
    // Written after the state change, not before: the overlay should go the instant
    // somebody taps, and a slow keychain write is not a reason to leave it up.
    void secureStorage.set(SEEN_KEY, JSON.stringify(next));
  },

  resetAll: () => {
    set({ seen: [], active: null });
    void secureStorage.set(SEEN_KEY, JSON.stringify([]));
  },
}));

/**
 * Anything unreadable is treated as "nothing seen yet".
 *
 * The stored value is JSON this app wrote, so a parse failure means corruption rather than a
 * format to migrate. Showing the hints again is the harmless direction; throwing here would
 * take the whole tab layout down on launch, because this runs inside its effect.
 *
 * Unknown strings are dropped rather than kept, so a hint renamed in a later version does
 * not sit in the array forever suppressing a key that no longer exists.
 */
function parse(stored: string | null): HintKey[] {
  if (!stored) return [];
  try {
    const value: unknown = JSON.parse(stored);
    if (!Array.isArray(value)) return [];
    return value.filter((k): k is HintKey => HINT_KEYS.includes(k as HintKey));
  } catch {
    return [];
  }
}
