import * as Notifications from 'expo-notifications';
import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/auth';
import { hasBeenPrimed, permissionState } from './permission';

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
 * <p>Banners are shown in the foreground on purpose. The alternative is a notification
 * that silently does nothing because the app happens to be open on a different screen,
 * which is how people conclude notifications are broken.
 */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
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
