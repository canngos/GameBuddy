import { useEffect, useRef, useState } from 'react';
import { Text } from './Text';

/**
 * A number that travels to its new value instead of jumping to it.
 *
 * Built for the coin balance, which is the one number in the app that changes as a *result*
 * of something the user just did. A balance that silently reads 1,240 where it read 1,440
 * a frame ago is the whole of UI_NOTE §5.1's complaint: the transaction happened, and the
 * only evidence is a figure that was never seen moving. Two hundred coins counting down
 * makes the price visible after the fact.
 *
 * **Deliberately not Reanimated.** Every other animation in this app is, and this one is
 * not, for a specific reason: Reanimated animates *styles* on the UI thread, and a digit is
 * text content, not a style. Driving it would mean `useAnimatedProps` on an animated `Text`
 * — which lands straight in this project's documented trap about `className` never going on
 * an animated component, for a single number that changes a handful of times per session.
 * `requestAnimationFrame` into React state costs one cheap re-render per frame of one leaf
 * node, and stays entirely inside the rules everything else here follows.
 *
 * Renders `variant="numeral"`, which exists precisely for "counts, balances… anything whose
 * value changes while it is on screen" (see `Text.tsx`) and carries no size of its own — so
 * the caller supplies the size classes, as it must everywhere `numeral` is used.
 */

type CountUpProps = {
  value: number;
  /** Milliseconds for the whole run. Kept short: this is feedback, not a slot machine. */
  duration?: number;
  className?: string;
  /** Announced instead of the ticking digits, which are meaningless to a screen reader. */
  accessibilityLabel?: string;
};

export function CountUp({ value, duration = 600, className, accessibilityLabel }: CountUpProps) {
  const [display, setDisplay] = useState(value);

  // What is currently on screen, tracked outside React state so an interrupted run can be
  // resumed from where it actually is. Reading `display` in the effect would give whatever
  // it was when the effect was created, which on a fast second change is a stale figure and
  // makes the number visibly jump backwards before it moves on.
  const shown = useRef(value);
  const first = useRef(true);

  useEffect(() => {
    // The first value is not a change, it is the initial state. Counting up from zero on
    // mount would mean the balance animates every single time the Market is opened, which
    // turns a meaningful signal into a screen transition.
    if (first.current) {
      first.current = false;
      shown.current = value;
      setDisplay(value);
      return;
    }

    const from = shown.current;
    if (from === value) return;

    const started = Date.now();
    let frame: number | null = null;

    const step = () => {
      const t = Math.min((Date.now() - started) / duration, 1);
      // Ease-out cubic, matching `Burst`: quick off the mark so the change is noticed, slow
      // at the end so the final figure is readable rather than snapping into place.
      const eased = 1 - Math.pow(1 - t, 3);
      const next = Math.round(from + (value - from) * eased);

      shown.current = next;
      setDisplay(next);

      if (t < 1) frame = requestAnimationFrame(step);
    };

    frame = requestAnimationFrame(step);
    return () => {
      if (frame !== null) cancelAnimationFrame(frame);
    };
  }, [value, duration]);

  return (
    <Text
      variant="numeral"
      className={className}
      accessibilityLabel={accessibilityLabel}
      // The digits change up to sixty times a second. Without this, a screen reader either
      // reads a number that is already wrong or interrupts itself continuously.
      accessibilityLiveRegion="none"
    >
      {display}
    </Text>
  );
}
