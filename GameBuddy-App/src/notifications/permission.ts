import * as Notifications from 'expo-notifications';
import { secureStorage } from '../session/storage';
import { ensureNotificationChannels } from './channel';

/**
 * Whether we have already made our case for notifications.
 *
 * <p>Stored on the device rather than the server, because it is about this installation:
 * the operating system's permission lives here, so the record of having asked belongs
 * here too. Reinstalling resets both together, which is right — a fresh install gets a
 * fresh ask.
 */
const ASKED_KEY = 'notifications.primed';

export type PermissionState =
  /** Never asked. The OS prompt is still available and worth spending. */
  | 'undetermined'
  /** They said yes. */
  | 'granted'
  /** They said no — to us, or to the system prompt. Only Settings can change it now. */
  | 'denied';

export async function permissionState(): Promise<PermissionState> {
  const status = await Notifications.getPermissionsAsync();
  if (status.granted) return 'granted';
  // `canAskAgain` false means the system prompt is spent: on Android 13+ a second
  // dismissal makes it permanent. Treating that as "undetermined" would show our
  // priming screen to somebody whose answer nothing can change from inside the app.
  return status.canAskAgain ? 'undetermined' : 'denied';
}

/** Whether the priming screen has already been shown and answered. */
export async function hasBeenPrimed(): Promise<boolean> {
  return (await secureStorage.get(ASKED_KEY)) === 'true';
}

export async function markPrimed(): Promise<void> {
  await secureStorage.set(ASKED_KEY, 'true');
}

/**
 * Requests the operating system's permission.
 *
 * <p>Only ever called from the priming screen, after somebody has said yes to us in
 * plain language. That ordering is the whole point: the system prompt can be answered
 * once and the answer is close to permanent, so spending it on somebody who has not been
 * told what it is for is how an app loses notifications for good.
 *
 * <p>The channel is created first because Android 13+ will not show anything from an app
 * with no channel, and expo-notifications documents that it must exist before a token is
 * requested. It is created here <em>and</em> on every launch — see
 * {@link ensureNotificationChannels} — because permission can also be granted from Android's
 * own settings, which never runs this function.
 */
export async function requestSystemPermission(): Promise<boolean> {
  await ensureNotificationChannels();
  const result = await Notifications.requestPermissionsAsync();
  await markPrimed();
  return result.granted;
}
