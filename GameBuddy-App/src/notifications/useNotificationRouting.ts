import * as Notifications from 'expo-notifications';
import { useRouter } from 'expo-router';
import { useEffect } from 'react';

/**
 * Where each kind of notification goes when it is tapped.
 *
 * <p>Mirrors `NotificationKind` on the backend. A kind this build does not recognise
 * falls through to the deck rather than doing nothing — an installed app will meet kinds
 * added after it shipped, and a notification that opens nothing looks broken.
 */
function routeFor(kind: string | undefined, targetId: string | undefined, name?: string) {
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

    case 'COMMUNITY_POST':
    case 'POST_LIKE':
    case 'POST_COMMENT':
    case 'COMMENT_LIKE':
      return targetId
        ? { pathname: '/community/post/[postId]', params: { postId: targetId } }
        : '/community';

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
