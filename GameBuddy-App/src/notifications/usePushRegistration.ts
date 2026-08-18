import * as Notifications from 'expo-notifications';
import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/auth';
import { ensureNotificationChannels } from './channel';
import { hasBeenPrimed, permissionState } from './permission';
import { isKnownKind } from './useNotificationRouting';

/**
 * Registers this device for push, and decides whether to ask first.
 *
 * <p>The backend already speaks FCM directly — it holds a service account and calls the
 * Firebase Admin SDK — so this registers the *device* token rather than an Expo push
 * token. Going through Expo's push service would put a third party in the delivery path
 * for no gain, and the backend would have to learn a second protocol.
 *
 * <p><b>This never triggers the system prompt itself.</b> It reports whether the primer
 * should be shown and leaves the asking to {@code NotificationPrimer}, after somebody has
 * agreed in plain language. An earlier version called {@code requestPermissionsAsync}
 * straight from the layout, so the operating system's dialog appeared the instant a gamer
 * first reached the app — with no context, on a screen they had not looked at yet. That
 * is the single question the platform gives you, and that is the worst possible moment to
 * spend it.
 */

/**
 * How a notification behaves while the app is open.
 *
 * <p><b>The app draws its own.</b> A kind this build recognises is presented in-app — a
 * match raises the full-screen celebration (see {@link useMatchNotifications}), everything
 * else raises a toast from the top (see {@link useInAppNotifications}) — so the system
 * banner is suppressed for it. Letting Android draw one as well would be the same news
 * twice, the second time in the OS's voice instead of ours, sliding down over the thing it
 * is announcing.
 *
 * <p><b>An unrecognised kind still gets the system banner</b>, and that fallback is the
 * important half of this rule. An installed app will meet kinds added to the backend after
 * it shipped, and it has no in-app treatment for them; suppressing those would swallow the
 * notification entirely. Better the OS's voice than silence. This is why the kind list is
 * exported data in {@link useNotificationRouting} rather than a condition written out here
 * — a new kind is added in one place or it is inconsistent everywhere.
 *
 * <p>Everything reaches the notification list either way, so nothing is lost if a toast or
 * a celebration is dismissed without being read.
 *
 * <p><b>No sound from the OS.</b> The app plays its own cue, paired with its own haptic, at
 * the moment the toast appears — see {@code src/ui/feedback.ts}. Leaving this true would
 * play two sounds for one event.
 *
 * <p>This only affects the foreground. Anything arriving while the app is backgrounded or
 * closed is untouched and behaves like any other push.
 */
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    const kind = (notification.request.content.data as { kind?: string } | undefined)?.kind;
    const drawnInApp = isKnownKind(kind);

    return {
      shouldShowBanner: !drawnInApp,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    };
  },
});

/**
 * Sends this device's token to the backend.
 *
 * <p>Only when permission is already granted — asking here would be the very thing this
 * file exists to avoid. Quiet about failure: a device with no Play Services, a revoked
 * permission, an offline launch. None of those are worth putting in front of somebody,
 * and none should stop the app working.
 *
 * <p>Exported because permission can be granted from two places: the primer at first
 * launch, and the notification settings screen after somebody turned it off in Android's
 * settings and came back. Both have to register, or the second one leaves an account with
 * permission and no token — silent in exactly the way that looks like a broken feature.
 */
export async function registerDeviceToken(): Promise<void> {
  try {
    if ((await permissionState()) !== 'granted') return;

    // Before the token, every time. The channel is what decides whether a notification
    // appears over the screen or only in the shade, and an install that predates the
    // channel — or one whose permission was granted from Android's settings — has none.
    await ensureNotificationChannels();

    const token = await Notifications.getDevicePushTokenAsync();
    if (!token?.data) return;

    // Sent on every launch, not only when it changes. FCM rotates tokens on its own
    // schedule and the old one goes dead silently; a device that re-registers each
    // time it starts cannot drift out of contact for longer than one session.
    await authApi.updateFcmToken(String(token.data));
  } catch (error) {
    if (__DEV__) console.warn('[push] registration skipped', error);
  }
}

export function usePushRegistration(enabled: boolean) {
  const [shouldPrime, setShouldPrime] = useState(false);

  const register = useCallback(() => registerDeviceToken(), []);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;

    (async () => {
      try {
        const state = await permissionState();
        if (cancelled) return;

        if (state === 'granted') {
          await register();
          return;
        }

        // Only somebody who has never been asked sees the primer. 'denied' means the
        // system prompt is spent and only Settings can change it, so showing our case
        // would be asking for something we cannot deliver.
        if (state === 'undetermined' && !(await hasBeenPrimed()) && !cancelled) {
          setShouldPrime(true);
        }
      } catch (error) {
        if (__DEV__) console.warn('[push] could not read permission state', error);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [enabled, register]);

  /** Called by the primer once it has an answer, whichever way it went. */
  const onPrimerDone = useCallback(() => {
    setShouldPrime(false);
    void register();
  }, [register]);

  return { shouldPrime, onPrimerDone, register };
}
