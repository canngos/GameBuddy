import { useCallback, useEffect, useRef } from 'react';
import { Dimensions, type View } from 'react-native';
import { create } from 'zustand';
import { useHint } from './useHint';
import type { HintKey } from './store';

/** Where the highlighted control is, in window coordinates. */
export type TargetRect = { x: number; y: number; width: number; height: number };

type CoachmarkState = {
  key: HintKey | null;
  rect: TargetRect | null;
  show: (key: HintKey, rect: TargetRect) => void;
  hide: (key: HintKey) => void;
};

/**
 * What the single coach-mark host is currently pointing at.
 *
 * A store rather than props because the thing being pointed at and the thing doing the
 * pointing are on opposite sides of the navigator: the target is a button inside a tab
 * screen, and the overlay has to be mounted above the tab bar to cover it. Passing a
 * measured rectangle up through five screens would be the same store with more steps.
 */
export const useCoachmark = create<CoachmarkState>((set, get) => ({
  key: null,
  rect: null,
  show: (key, rect) => set({ key, rect }),
  hide: (key) => {
    if (get().key === key) set({ key: null, rect: null });
  },
}));

/**
 * Points the coach mark at a control, once, the first time somebody could use it.
 *
 * Give the returned `attach` to a plain `View`'s `ref`, wrapping the control, with
 * `collapsable={false}` — Android flattens away a wrapper that has no style of its own, and
 * a flattened view cannot be measured. `enabled` is the screen's own answer to "is this on
 * screen and usable right now"; everything app-wide is decided by {@link useHint}.
 *
 * **A callback ref, not a ref object, and not called `ref`.** Reading `someRef.current`
 * while rendering is a lint error and a real hazard, and handing a `RefObject` out of a
 * hook invites exactly that at every call site; `react-hooks/refs` refuses even to see a
 * property called `ref` read during render. The callback also removes the guesswork about
 * when the node exists: it is called with the node on attach and with null on detach.
 *
 * **Measured, not guessed.** `measureInWindow` is the only thing that knows where a control
 * ended up after safe-area insets, a font-scale bump and the three screen tiers have had
 * their say — and the hole has to land on the real control, or the overlay is pointing at
 * empty space. Window coordinates, because the host draws at the window root.
 */
export function useCoachmarkTarget(key: HintKey, enabled: boolean) {
  const node = useRef<View | null>(null);
  const { due, dismiss } = useHint(key, enabled);
  const show = useCoachmark((s) => s.show);
  const hide = useCoachmark((s) => s.hide);

  const attach = useCallback((instance: View | null) => {
    node.current = instance;
  }, []);

  useEffect(() => {
    if (!due) {
      hide(key);
      return;
    }

    let cancelled = false;
    const measure = () => {
      node.current?.measureInWindow((x, y, width, height) => {
        // A control that measures to nothing is one that is not really laid out yet. Better
        // to skip the hint this time than to dim the screen around a hole with no control
        // in it; it stays unseen, so the next visit shows it.
        if (cancelled || width === 0 || height === 0) return;
        show(key, { x, y, width, height });
      });
    };

    // A frame late on purpose: a node measured in the commit that mounted it reports zeros,
    // and a zero-sized hole is a fully dimmed screen with a caption floating on it.
    const frame = requestAnimationFrame(measure);
    // A rotation or a split-screen resize moves the control out from under the hole.
    const subscription = Dimensions.addEventListener('change', measure);

    return () => {
      cancelled = true;
      cancelAnimationFrame(frame);
      subscription.remove();
      hide(key);
    };
  }, [due, key, show, hide]);

  return { attach, dismiss };
}
