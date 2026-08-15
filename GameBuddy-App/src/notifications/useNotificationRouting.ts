import * as Notifications from 'expo-notifications';
import { useRouter } from 'expo-router';
import { useEffect } from 'react';

/**
 * Every kind this build knows about. Mirrors `NotificationKind` on the backend.
 *
 * <p>Declared as data rather than left implicit in the switch below because three separate
 * things now need to ask "do we recognise this?": the router, the in-app toast, and
 * {@code usePushRegistration}, which suppresses the system banner for exactly the kinds the
 * app draws itself. Re-listing them in three places is how one of them ends up a kind
 * behind, and the symptom of that is a notification which shows twice or not at all.
 */
export const NOTIFICATION_KINDS = [
  'MESSAGE',
  'MATCH',
  'FRIEND_REQUEST',
  'FRIEND_ACCEPTED',
  'BADGE',
  // The four COMMUNITY_* kinds retired with the Community feature. A late-arriving push
  // or an old stored notification with one of them falls to the default '/home' branch,
  // which is the designed behaviour for any kind a build does not know.
  'LOBBY_JOIN_REQUEST',
  'LOBBY_REQUEST_ACCEPTED',
  'LOBBY_MESSAGE',
  'LOBBY_CANCELLED',
  'RETURN',
] as const;

export type NotificationKind = (typeof NOTIFICATION_KINDS)[number];

export function isKnownKind(kind: string | undefined): kind is NotificationKind {
  return !!kind && (NOTIFICATION_KINDS as readonly string[]).includes(kind);
}

/**
 * Where each kind of notification goes when it is tapped.
 *
 * <p>A kind this build does not recognise falls through to the deck rather than doing
 * nothing — an installed app will meet kinds added after it shipped, and a notification
 * that opens nothing looks broken.
 *
 * <p><b>Exported so the in-app toast can reuse it.</b> Tapping a toast must land in exactly
 * the same place as tapping the notification it replaced; a second mapping written next to
 * this one would agree on the day it was written and not for much longer.
 */
export function routeFor(kind: string | undefined, targetId: string | undefined, name?: string) {
  // The chat screen takes the username as a parameter so it can show it immediately,
  // before the conversation has loaded. Arriving from a notification without it left the
  // header reading "Conversation" — and the sender's name was sitting right there in the
  // notification title, which for MESSAGE and MATCH is exactly what it is.
  const conversation = (id: string) => ({
    pathname: '/messages/[friendId]',
    params: name ? { friendId: id, username: name } : { friendId: id },
  });

  switch (kind) {
    case 'MESSAGE':
      return targetId ? conversation(targetId) : '/messages';

    case 'MATCH':
      // The conversation, not their profile. A match is an invitation to say something,
      // and the screen that lets you do it is the one worth opening.
      return targetId ? conversation(targetId) : '/messages';

    case 'FRIEND_REQUEST':
      // The profile tab, where requests are answered.
      return '/profile';

    case 'FRIEND_ACCEPTED':
      return targetId
        ? { pathname: '/messages/gamer/[userId]', params: { userId: targetId } }
        : '/profile';

    case 'BADGE':
      return '/badges';

    case 'LOBBY_JOIN_REQUEST':
    case 'LOBBY_REQUEST_ACCEPTED':
    case 'LOBBY_MESSAGE':
      // The lobby screen answers all three: the owner's pending inbox, the accepted
      // member's new team, and the chat are all on it. LOBBY_CANCELLED deliberately does
      // not go there — the lobby is gone; the list shows what remains.
      return targetId
        ? { pathname: '/lobby/[lobbyId]', params: { lobbyId: targetId } }
        : '/lobby';

    case 'LOBBY_CANCELLED':
      return '/lobby';

    case 'RETURN':
      // Whatever was waiting is what the copy promised, and both of the things it can
      // promise — people who liked you, friend requests — are answered from here.
      return '/profile';

    default:
      return '/home';
  }
}

/**
 * Sends the gamer where a tapped notification points.
 *
 * <p>Handles both directions: a tap while the app is running, and a tap that launched it
 * from cold. The second is the one that is easy to miss and the more common in practice —
 * a notification usually arrives when the app is closed.
 */
export function useNotificationRouting(enabled: boolean) {
  const router = useRouter();

  useEffect(() => {
    if (!enabled) return;
    let handled = false;

    const go = (response: Notifications.NotificationResponse | null) => {
      if (!response) return;
      const data = response.notification.request.content.data as
        { kind?: string; targetId?: string } | undefined;
      // The title is the sender's name for the kinds that have one.
      const title = response.notification.request.content.title ?? undefined;
      router.push(routeFor(data?.kind, data?.targetId, title) as never);
    };

    // A tap that started the app. Read once — asking again later would re-navigate on
    // every remount, dragging somebody back out of wherever they had moved to.
    Notifications.getLastNotificationResponseAsync().then((response) => {
      if (!handled) {
        handled = true;
        go(response);
      }
    });

    const subscription = Notifications.addNotificationResponseReceivedListener(go);
    return () => subscription.remove();
  }, [enabled, router]);
}
