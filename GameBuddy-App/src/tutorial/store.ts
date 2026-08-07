import { create } from 'zustand';
import { secureStorage } from '../session/storage';

const SEEN_KEY = 'gamebuddy.tutorialSeen';

/**
 * The five tabs, in the order the tutorial walks them.
 *
 * The deck first because it is where the app opens and what it is for; settings last
 * because it is the one somebody will find on their own.
 */
export const TUTORIAL_STEPS = [
  {
    route: '/home',
    title: 'Find someone to play with',
    body: 'Swipe through gamers who play what you play. Right if you want to play together, left if not. When you both swipe right, you match.',
  },
  {
    route: '/messages',
    title: 'Talk to your matches',
    body: 'Matching opens a private chat. Nobody can message you unless you both agreed to it, and you can block or report anyone from inside a conversation.',
  },
  {
    route: '/community',
    title: 'Join the conversation',
    body: 'Communities are built around games. Post, comment, and find people who are already talking about what you play.',
  },
  {
    route: '/market',
    title: 'Make your profile yours',
    body: 'Frames and banners for your profile, plus extra daily likes if you run out. Everything here is optional — the app works without spending anything.',
  },
  {
    route: '/profile',
    title: 'Your profile, and everything else',
    body: 'Your games, your keywords, your friends and your badges. Settings live behind the gear, including this tutorial if you want it again.',
  },
] as const;

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
