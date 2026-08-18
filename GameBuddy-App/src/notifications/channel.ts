import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

/**
 * The Android channels GameBuddy delivers on, one per notification category.
 *
 * **These ids are a contract with the backend.** `FCMService.channelIdFor` names them when
 * it sends, and `NotificationCategory` on the server is what decides which one a given kind
 * belongs to. Renaming one here without renaming it there means the server asks for a
 * channel the device does not have.
 *
 * `messages` is also the manifest default — the `defaultChannel` prop on the
 * expo-notifications plugin in `app.json` writes it into
 * `com.google.firebase.messaging.default_notification_channel_id`. That is the fallback for
 * anything the server does not classify, so it has to stay one of these three.
 *
 * **Why three rather than one.** A channel is the only notification control Android gives
 * the user, and it is per channel: with a single channel, somebody who wants to be
 * interrupted by a message but not by a "come back and play" nudge has no way to say so
 * except to silence the app entirely. Three is also as far as it is worth going — the
 * server already groups nine kinds into these categories for its own settings screen, and
 * a channel per kind would be a settings page nobody reads.
 */
export const NOTIFICATION_CHANNELS = {
  /** Chat and lobby chat. Interrupts: somebody is talking to you and waiting. */
  MESSAGES: 'messages',
  /** Matches, super likes, friend requests and answers, badges, lobby activity. */
  SOCIAL: 'social',
  /** "You have not played in a while." The only kind nobody asked for. */
  REMINDERS: 'reminders',
} as const;

/**
 * The channel this replaced. Deleted on sight so it does not linger in the system's
 * notification settings as a dead entry with our name on it.
 */
const LEGACY_CHANNEL_ID = 'default';

/**
 * What each channel is called and how loudly it arrives.
 *
 * The names are what a person reads in Android's notification settings, so they describe
 * the traffic rather than naming our enum — "Reminders" means something to somebody
 * deciding whether to keep it; "REMINDERS" does not.
 */
const CHANNELS = [
  {
    id: NOTIFICATION_CHANNELS.MESSAGES,
    name: 'Messages',
    importance: Notifications.AndroidImportance.HIGH,
  },
  {
    id: NOTIFICATION_CHANNELS.SOCIAL,
    name: 'Matches and friends',
    importance: Notifications.AndroidImportance.HIGH,
  },
  {
    /*
     * The one deliberate step down. A re-engagement nudge is the only notification the
     * gamer did not cause, and one that pops over whatever they are doing is how an app
     * gets its notifications switched off wholesale — which would take the messages with
     * it. DEFAULT still makes a sound and still lands in the shade; it just does not
     * interrupt.
     */
    id: NOTIFICATION_CHANNELS.REMINDERS,
    name: 'Reminders',
    importance: Notifications.AndroidImportance.DEFAULT,
  },
] as const;

/**
 * Creates the notification channels, at the importance that decides whether each one
 * appears over the screen.
 *
 * **This is the fix for "notifications only appear in the tray".** There was one channel,
 * created at `IMPORTANCE_DEFAULT`, which on Android 8+ means exactly what testers
 * described: posted, audible, in the shade, never over what you are doing. Heads-up display
 * starts at `IMPORTANCE_HIGH`, which is what every messaging app uses for a message from a
 * person.
 *
 * **New ids, not a higher importance on the old channel.** Android lets an app lower a
 * channel's importance after creation and pointedly does not let it raise one — once a user
 * has seen a channel, how much it may interrupt them is their decision. Editing the old
 * channel would therefore have fixed nothing for anybody who already had the app, which is
 * every tester who reported this.
 *
 * **Called on every launch, not just when permission is requested.** It used to be created
 * only inside `requestSystemPermission`, so an account that granted permission through
 * Android's settings rather than the in-app primer never had a channel at all. Doing it on
 * launch is also the upgrade path: an existing install has all three before any notification
 * can arrive for them.
 *
 * Silent on failure. A device that refuses a channel is not one the app can fix, and it must
 * not stop the launch.
 */
export async function ensureNotificationChannels(): Promise<void> {
  if (Platform.OS !== 'android') return;

  try {
    for (const channel of CHANNELS) {
      await Notifications.setNotificationChannelAsync(channel.id, {
        name: channel.name,
        importance: channel.importance,
        // Sound and vibration left at the platform defaults for the importance, because
        // that is what somebody expects. The app's own cue is foreground-only, where the
        // OS is told to stay quiet — see `usePushRegistration`.
        lightColor: '#FF4D67',
      });
    }

    await Notifications.deleteNotificationChannelAsync(LEGACY_CHANNEL_ID);
  } catch {
    // Nothing here is worth interrupting a launch for.
  }
}
