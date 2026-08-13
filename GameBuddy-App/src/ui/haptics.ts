import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';

/**
 * Touch feedback, named for what happened rather than how strong it is.
 *
 * Call sites should say `commit()` and not `impactAsync(Medium)`, because the second one
 * invites everybody to pick their own intensity and the result is an app where a swipe and
 * a purchase feel the same.
 *
 * **Android goes through `performAndroidHapticsAsync`, deliberately.** The `impactAsync`
 * family maps to the vibrator and needs the `VIBRATE` permission; the Android-native
 * constants are system haptic feedback, which does not. Installing `expo-haptics` merges
 * `VIBRATE` into the manifest either way — that is a packaging fact, not a runtime one —
 * but routing through the system constants means the app still behaves correctly for
 * someone whose device or ROM has vibration disabled, and it matches what every other
 * Android app feels like.
 *
 * Everything here is fire-and-forget. Haptics failing is never worth surfacing, and never
 * worth making a caller `await` — the visual result of a tap must not wait on the buzz.
 */

const isAndroid = Platform.OS === 'android';

function fire(promise: Promise<unknown>): void {
  // Rejections are swallowed on purpose: no haptic motor, haptics disabled in system
  // settings, or an OS that declines. None of those are errors the user needs to hear
  // about, and an unhandled rejection warning in the console is noise that trains people
  // to ignore the console.
  void promise.catch(() => {});
}

/** A control was pressed. The lightest thing available. */
export function tapLight(): void {
  fire(
    isAndroid
      ? Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Context_Click)
      : Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
  );
}

/** A decision was made and cannot be taken back cheaply — a swipe past the threshold. */
export function commit(): void {
  fire(
    isAndroid
      ? Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm)
      : Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium),
  );
}

/** Something good and rare. A match, a purchase, a badge claimed. */
export function celebrate(): void {
  fire(
    isAndroid
      ? Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm)
      : Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
  );
}

/** The app said no — a limit hit, a refused purchase, a validation failure. */
export function reject(): void {
  fire(
    isAndroid
      ? Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Reject)
      : Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
  );
}
