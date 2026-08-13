import type { LucideIcon } from 'lucide-react-native';
import { create } from 'zustand';
import type { Tone } from './Icon';

/**
 * The queue behind the in-app toast.
 *
 * Modelled on `src/match/celebration.ts` — an app-wide zustand store driving a single
 * overlay mounted once in `app/(main)/_layout.tsx` — because that pattern already works and
 * a second shape for the same job would be a coin toss over which one to reach for.
 *
 * **Why this lives in `src/ui/` and not `src/notifications/`.** Notifications are its first
 * caller, not its only one: the Market raises a toast when a frame is bought, and having
 * `src/market/` import from `src/notifications/` to announce a purchase would be a lie about
 * what depends on what. This is a UI primitive that notifications happen to use.
 *
 * **A queue, not a slot.** Two events genuinely do land together — a match and a message
 * arriving in the same second is ordinary, and so is buying a frame that completes a quest.
 * The alternative, a single slot, means the second event silently replaces the first before
 * it has been read.
 */

export type Toast = {
  /**
   * Identity, and the dedupe key.
   *
   * Push notifications get delivered twice more often than you would like — a retry, or the
   * same news arriving down two paths. Keying on something stable and meaningful
   * (`message:<friendId>`, `bought:<cosmeticId>`) means a repeat updates nothing and
   * enqueues nothing, rather than showing the same sentence twice in a row.
   */
  id: string;
  title: string;
  body?: string;
  icon: LucideIcon;
  tone: Tone;
  /** Run when tapped. For notifications this navigates exactly where a tap on the OS one would. */
  onPress?: () => void;
};

/**
 * Beyond this, the oldest waiting toast is dropped.
 *
 * Toasts are shown one at a time for about four seconds, so a queue of four is already
 * sixteen seconds of backlog — long enough that the tail has stopped being news. A burst of
 * community activity should not put the app into a slideshow it cannot be interrupted out
 * of. The *head* is never dropped; it is already on screen.
 */
const MAX_QUEUED = 4;

type ToastState = {
  queue: Toast[];
  push: (toast: Toast) => void;
  dismiss: (id: string) => void;
  clear: () => void;
};

export const useToasts = create<ToastState>((set, get) => ({
  queue: [],

  push: (toast) => {
    if (get().queue.some((queued) => queued.id === toast.id)) return;

    set((state) => {
      const next = [...state.queue, toast];
      // Trims from index 1, never index 0 — dropping the head would yank a toast off the
      // screen mid-read, which is the one thing an overflow policy must not do.
      return { queue: next.length > MAX_QUEUED ? [next[0], ...next.slice(-(MAX_QUEUED - 1))] : next };
    });
  },

  // By id rather than "shift the head", so a toast that was already dismissed by hand
  // cannot be dismissed a second time by its own timer and take its successor with it.
  dismiss: (id) => set((state) => ({ queue: state.queue.filter((queued) => queued.id !== id) })),

  clear: () => set({ queue: [] }),
}));

/**
 * Raise a toast from anywhere, including outside a component.
 *
 * Most callers are inside a react-query `onSuccess`, which is a plain callback rather than a
 * render, so a hook would be the wrong tool and `useToasts.getState().push` at every call
 * site would be noise.
 */
export function showToast(toast: Toast): void {
  useToasts.getState().push(toast);
}
