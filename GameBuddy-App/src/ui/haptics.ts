import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';
import { create } from 'zustand';
import { secureStorage } from '../session/storage';

/**
 * Touch feedback, named for what happened rather than how strong it is.
 *
 * Call sites should say `commit()` and not `impactAsync(Medium)`, because the second one
 * invites everybody to pick their own intensity and the result is an app where a swipe and
 * a purchase feel the same.
 *
 * **Android goes through the vibrator, not `performAndroidHapticsAsync`.** That is a
 * reversal, and the reason is that the previous route did nothing at all on a real device.
 * `performAndroidHapticsAsync` lands on this, in expo-haptics' `HapticsModule.kt`:
 *
 *     val view = appContext.currentActivity?.findViewById<View>(android.R.id.content)
 *     view?.performHapticFeedback(type.toHapticFeedbackType())
 *
 * which has three ways of doing nothing, none of them observable from JS:
 *
 * 1. `view?.` is null-safe. A null activity resolves the promise having done nothing.
 * 2. One-argument `performHapticFeedback` is *advisory*. It respects the View's own flag
 *    and the system "touch feedback" setting, returns `false` when either is off — and
 *    that `Boolean` is discarded before it reaches JS.
 * 3. Constants are resolved by reflection. `CONFIRM` and `REJECT` are API 30+; below that
 *    the module throws `HapticsNotSupportedException`, so on Android 8–10 `commit()`,
 *    `celebrate()` and `reject()` were dead while `tapLight()` still worked.
 *
 * Because (2) never surfaces, "try the system route and fall back if nothing happened" is
 * not implementable from JS — the failure is invisible. So the vibrator is the default.
 * `VIBRATE` has been in the manifest since expo-haptics was installed, so this costs no
 * permission work.
 *
 * The original intent — don't buzz somebody who has asked not to be buzzed — is kept, but
 * through {@link useHapticsEnabled}, a switch the app owns and the user can find, rather
 * than an OS setting the app cannot read and most people set for their keyboard.
 */

const KEY = 'gamebuddy.hapticsEnabled';

type HapticsState = {
  enabled: boolean;
  /** False until storage has been read. Nothing gates on it — see `load`. */
  hydrated: boolean;
  load: () => Promise<void>;
  setEnabled: (enabled: boolean) => void;
};

/**
 * On by default, and deliberately not blocked on hydration — the same trade `sound.ts`
 * makes, for the same reason: a stray buzz in the first few hundred milliseconds is
 * cheaper than swallowing the first real one of the session.
 */
export const useHapticsEnabled = create<HapticsState>((set) => ({
  enabled: true,
  hydrated: false,

  load: async () => {
    const stored = await secureStorage.get(KEY);
    set({ enabled: stored !== 'off', hydrated: true });
  },

  setEnabled: (enabled) => {
    set({ enabled });
    void secureStorage.set(KEY, enabled ? 'on' : 'off');
  },
}));

const isAndroid = Platform.OS === 'android';

function fire(make: () => Promise<unknown>): void {
  if (!useHapticsEnabled.getState().enabled) return;

  try {
    // Rejections are swallowed in production on purpose: no haptic motor, haptics off in
    // system settings, or an OS that declines. None are worth telling the user about.
    //
    // But they are surfaced in development, because a silent `.catch(() => {})` is exactly
    // what hid the bug above for the whole life of the feature — every call site looked
    // correct and nothing ever reported that the buzz had not happened.
    void make().catch((error: unknown) => {
      if (__DEV__) console.warn('[haptics] rejected', error);
    });
  } catch (error) {
    // A missing native module throws synchronously rather than rejecting.
    if (__DEV__) console.warn('[haptics] threw', error);
  }
}

/** A control was pressed. The lightest thing available. */
export function tapLight(): void {
  fire(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light));
}

/** A decision was made and cannot be taken back cheaply — a swipe past the threshold. */
export function commit(): void {
  fire(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium));
}

/**
 * Something good and rare. A match, a purchase, a badge claimed.
 *
 * The notification pattern rather than a single impact: it is two pulses, which is what
 * makes it read as an event rather than as another tap.
 */
export function celebrate(): void {
  fire(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success));
}

/** The app said no — a limit hit, a refused purchase, a validation failure. */
export function reject(): void {
  fire(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error));
}

/**
 * Every primitive, unwrapped, for the diagnostic in the dev gallery.
 *
 * Exported from here rather than rebuilt there so the gallery exercises the same
 * `expo-haptics` calls the app makes. These deliberately do **not** go through `fire`:
 * they ignore the preference and they let the promise reject, because the whole point is
 * to see which ones fail and how.
 *
 * `Vibration.vibrate` is not included — the gallery adds it separately as ground truth for
 * "does this device have a working motor at all", and it comes from react-native rather
 * than from here.
 */
export const probes: { name: string; run: () => Promise<unknown> }[] = [
  { name: 'impact:Light', run: () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light) },
  { name: 'impact:Medium', run: () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium) },
  { name: 'impact:Heavy', run: () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy) },
  {
    name: 'notify:Success',
    run: () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
  },
  {
    name: 'notify:Error',
    run: () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
  },
  { name: 'selection', run: () => Haptics.selectionAsync() },
  ...(isAndroid
    ? [
        // The old route. Kept only so the diagnostic can show it resolving while nothing
        // is felt, which is the fingerprint of failure path (2).
        {
          name: 'android:Context_Click',
          run: () => Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Context_Click),
        },
        {
          name: 'android:Confirm',
          run: () => Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm),
        },
        {
          name: 'android:Reject',
          run: () => Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Reject),
        },
      ]
    : []),
];
