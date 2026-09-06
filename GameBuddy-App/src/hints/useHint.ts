import { useCallback, useEffect } from 'react';
import { useCelebration } from '../match/celebration';
import { useTutorial } from '../tutorial/store';
import { useHints, type HintKey } from './store';

/**
 * Whether a hint may show right now, and how to put it away.
 *
 * `enabled` is the screen's own half of the answer — is the thing being pointed at actually
 * on screen, is a sheet covering it, is the deck still loading. Everything that is true app
 * wide is decided here instead, so five call sites cannot each get it slightly wrong.
 *
 * **What outranks a hint.** The tutorial, because it is mounted above the tabs and would
 * simply cover this. A match celebration, because it is the best thing that happens in the
 * app and nothing should talk over it. The notification primer needs no rule: it renders
 * instead of the tabs, so nothing under it is mounted at all.
 *
 * The slot is claimed in an effect rather than during render — `claim` writes to the store,
 * and a store write inside a render body is a re-render inside a render.
 */
export function useHint(key: HintKey, enabled: boolean): { due: boolean; dismiss: () => void } {
  const hydrated = useHints((s) => s.hydrated);
  const seen = useHints((s) => s.seen.includes(key));
  const active = useHints((s) => s.active);
  const claim = useHints((s) => s.claim);
  const release = useHints((s) => s.release);
  const dismissHint = useHints((s) => s.dismiss);

  const tutorialStep = useTutorial((s) => s.step);
  const celebrating = useCelebration((s) => s.matched !== null);

  const eligible = hydrated && enabled && !seen && tutorialStep === null && !celebrating;

  // `active` is a dependency on purpose. Without it the effect only runs when *this* hint's
  // eligibility changes — so a hint that was eligible while another one held the slot never
  // tried again once that one was dismissed, and the second and third hints on a screen
  // simply never appeared until the screen was remounted.
  //
  // It cannot loop: claiming sets `active` to this key, the effect re-runs, and `claim`
  // returns early without writing because the slot is already ours.
  useEffect(() => {
    if (!eligible) {
      // Only ever gives back its own claim, so a screen going away cannot close somebody
      // else's hint.
      release(key);
      return;
    }
    claim(key);
  }, [eligible, active, key, claim, release]);

  // Release on unmount as well: leaving the slot held would silently suppress every later
  // hint for the rest of the session.
  useEffect(() => () => release(key), [key, release]);

  const dismiss = useCallback(() => dismissHint(key), [dismissHint, key]);

  return { due: eligible && active === key, dismiss };
}
