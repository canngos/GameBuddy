import * as haptics from './haptics';
import * as sound from './sound';

/**
 * What the app does when something happens: the buzz and the cue, together.
 *
 * This exists because they are two halves of one signal and they were always going to
 * drift. `haptics.ts` had `celebrate()` from the day the match overlay shipped; when sound
 * arrived, every call site that wanted both would have had to remember both, and the ones
 * added later would have remembered one. A single named intent per event makes that
 * impossible — you cannot forget the sound if you never mention the sound.
 *
 * **Prefer this over `haptics` and `sound` directly.** The two exceptions are deliberate:
 *
 * - `Button` calls `haptics.tapLight()` on its own, because a click on every button in the
 *   app is the fastest way to make people turn sound off entirely — and once it is off,
 *   the match and purchase cues go with it.
 * - Anything that wants a cue with no buzz, or the reverse, should say so by reaching for
 *   the specific module rather than adding a half-empty intent here.
 *
 * The intents are named after events, not intensities, for the reason `haptics.ts` gives:
 * naming them by strength invites every call site to pick its own, and you end up with an
 * app where a swipe and a purchase feel the same.
 */

/** A mutual match. The rarest good thing in the product, and the only full-screen one. */
export function celebrate(): void {
  haptics.celebrate();
  sound.match();
}

/** Coins left the balance and something came back — a frame, a consumable, a membership. */
export function purchase(): void {
  haptics.celebrate();
  sound.purchase();
}

/**
 * Coins or a badge arrived: a daily claim, a quest, an advert watched, a stipend.
 *
 * Shares `celebrate()`'s haptic with `purchase()` — Android has no third confirm-shaped
 * constant worth using, and the two events are told apart by their cues, which is enough.
 * If a heavier distinction is ever wanted, it belongs in `haptics.ts`, not here.
 */
export function reward(): void {
  haptics.celebrate();
  sound.reward();
}

/** Something arrived from outside: a message, a friend request, community activity. */
export function receive(): void {
  haptics.tapLight();
  sound.message();
}

/** A decision was made and cannot be taken back cheaply. Silent on purpose. */
export function commit(): void {
  haptics.commit();
}

/** A control was pressed. Silent on purpose — see the note about `Button` above. */
export function tapLight(): void {
  haptics.tapLight();
}

/** The app said no: a limit hit, a refused purchase, a validation failure. Silent. */
export function reject(): void {
  haptics.reject();
}
