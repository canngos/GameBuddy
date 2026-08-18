import { create } from 'zustand';
import { secureStorage } from '../session/storage';

const SEEN_KEY = 'gamebuddy.tutorialSeen';

/**
 * The five tabs, in the order the tutorial walks them.
 *
 * The deck first because it is where the app opens and what it is for; settings last
 * because it is the one somebody will find on their own. Structure only: the copy lives
 * in the dictionary under `tutorial.steps`, keyed by `key`, and is resolved at render —
 * a module-level array of sentences would be evaluated before a language is chosen.
 */
export const TUTORIAL_STEPS = [
  { route: '/home', key: 'home' },
  { route: '/messages', key: 'messages' },
  { route: '/lobby', key: 'lobby' },
  { route: '/market', key: 'market' },
  { route: '/profile', key: 'profile' },
] as const satisfies readonly {
  route: string;
  key: keyof import('../i18n/dictionaries/en').Dictionary['tutorial']['steps'];
}[];

type TutorialState = {
  /** Which step is showing, or null when the tutorial is not running. */
  step: number | null;
  /** False until storage has been read, so the tutorial cannot flash on a cold start. */
  hydrated: boolean;
  /** True once it has been finished or skipped. Persisted. */
  seen: boolean;

  load: () => Promise<void>;
  /** Runs it from the beginning, whether or not it has been seen before. */
  start: () => void;
  next: () => void;
  /** Ends it and records that it has been seen. Used by both Skip and the last Done. */
  finish: () => Promise<void>;
};

export const useTutorial = create<TutorialState>((set, get) => ({
  step: null,
  hydrated: false,
  seen: false,

  load: async () => {
    const stored = await secureStorage.get(SEEN_KEY);
    set({ seen: stored === 'true', hydrated: true });
  },

  start: () => set({ step: 0 }),

  next: () => {
    const current = get().step;
    if (current === null) return;
    if (current + 1 >= TUTORIAL_STEPS.length) {
      void get().finish();
      return;
    }
    set({ step: current + 1 });
  },

  finish: async () => {
    set({ step: null, seen: true });
    // Written after the state change, not before: the overlay should close the instant
    // somebody taps, and a slow keychain write is not a reason to leave it on screen.
    await secureStorage.set(SEEN_KEY, 'true');
  },
}));
