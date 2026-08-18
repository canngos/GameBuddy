import { useQueryClient } from '@tanstack/react-query';
import type { QueryKeyRoot } from '../query/keys';
import * as Notifications from 'expo-notifications';
import { useRouter } from 'expo-router';
import {
  Award,
  Bell,
  MessageCircle,
  UserPlus,
  Users,
  type LucideIcon,
} from 'lucide-react-native';
import { useEffect } from 'react';
import { useActiveConversation } from '../chat/activeConversation';
import { receive } from '../ui/feedback';
import type { Tone } from '../ui/Icon';
import { showToast } from '../ui/toast';
import { isKnownKind, routeFor, type NotificationKind } from './useNotificationRouting';

/**
 * Turns a foreground push into an in-app toast.
 *
 * **The sibling of {@link useMatchNotifications}, and deliberately not merged with it.**
 * A match gets the full-screen celebration; it is the rarest and biggest thing that happens
 * in this product and it earns the whole screen. Everything else — messages, badges, friend
 * requests, community activity — is toast-sized, and drawing them the same way would make
 * the match moment ordinary. `MATCH` is skipped here for exactly that reason.
 *
 * `addNotificationReceivedListener` fires only while the app is foregrounded, which is the
 * only case that needs this: a notification tapped from the tray or from cold start is
 * already handled by {@link useNotificationRouting}, and it navigates rather than toasting.
 *
 * A kind this build does not recognise is left alone here **and** keeps its system banner
 * (see {@code usePushRegistration}), so it is announced by the OS rather than swallowed.
 */
export function useInAppNotifications(enabled: boolean) {
  const router = useRouter();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;

    const subscription = Notifications.addNotificationReceivedListener((notification) => {
      const content = notification.request.content;
      const data = content.data as { kind?: string; targetId?: string } | undefined;
      const kind = data?.kind;

      // `MATCH` is the celebration's, not ours. Unknown kinds keep the system banner.
      if (!isKnownKind(kind) || kind === 'MATCH') return;

      // The lists these change are wrong the moment the push lands, and refreshing them
      // here means tapping the toast arrives at a screen that is already right rather than
      // one that fills in a beat later.
      for (const key of REFRESH_ON[kind]) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }

      // Nothing to announce about a conversation the gamer is reading. The backend sends
      // the push either way and says why — it cannot see which screen is open — so this
      // is the half that was missing: a reply used to arrive as a bubble and, a moment
      // later, as a banner covering the thread it had just been added to. The refresh
      // above still runs, so the inbox behind them is right when they leave.
      if (kind === 'MESSAGE' && data?.targetId === useActiveConversation.getState().friendId) {
        return;
      }

      const { icon, tone } = PRESENTATION[kind];

      showToast({
        // Keyed on kind and target, so the same news arriving twice — a retry, or a push
        // racing the websocket — is shown once. Falls back to the notification's own id
        // when there is no target, which still collapses a duplicate delivery.
        id: `${kind}:${data?.targetId ?? notification.request.identifier}`,
        // The backend writes both, and they are already the right words in the right
        // language. Rewriting them here would be a second copy of the product's voice that
        // drifts from the one people see on the lock screen.
        title: content.title ?? 'GameBuddy',
        body: content.body ?? undefined,
        icon,
        tone,
        // `withAnchor` for the same reason as useNotificationRouting: a deep screen
        // opened from a toast needs its list loaded underneath for back to make sense.
        onPress: () =>
          router.push(routeFor(kind, data?.targetId, content.title ?? undefined) as never, {
            withAnchor: true,
          }),
      });

      // One cue and one light buzz, at the moment the toast appears. The OS sound is off
      // for every kind the app draws itself, so this is the only thing that plays.
      receive();
    });

    return () => subscription.remove();
  }, [enabled, router, queryClient]);
}

/**
 * The glyph and colour each kind wears.
 *
 * Tones follow the palette's existing meanings rather than inventing new ones: `accent` is
 * reserved for like/match/admirer, so community activity — which is about other people's
 * posts, not about anyone liking *you* — takes `primary` alongside messages and friends.
 * `gold` is coins, membership and badge tiers, which is exactly what a `BADGE` is.
 */
const PRESENTATION: Record<NotificationKind, { icon: LucideIcon; tone: Tone }> = {
  MESSAGE: { icon: MessageCircle, tone: 'primary' },
  // Present for completeness of the record, never used — a match is celebrated, not toasted.
  MATCH: { icon: Users, tone: 'accent' },
  FRIEND_REQUEST: { icon: UserPlus, tone: 'primary' },
  FRIEND_ACCEPTED: { icon: UserPlus, tone: 'primary' },
  BADGE: { icon: Award, tone: 'gold' },
  // Lobby traffic is about other people wanting to play with you, not about being liked,
  // so it sits with messages and friends on `primary`.
  LOBBY_JOIN_REQUEST: { icon: Users, tone: 'primary' },
  LOBBY_REQUEST_ACCEPTED: { icon: Users, tone: 'primary' },
  LOBBY_MESSAGE: { icon: Users, tone: 'primary' },
  LOBBY_CANCELLED: { icon: Users, tone: 'muted' },
  RETURN: { icon: Bell, tone: 'muted' },
};

/**
 * Query keys each kind invalidates. Empty where the destination fetches fresh anyway.
 *
 * Typed against {@link QueryKeyRoot} rather than `string`, because three of these were
 * wrong and nothing said so: `'conversations'` is not a key this app fetches — the inbox is
 * `'inbox'` — so a foreground MESSAGE push refreshed nothing for as long as the feature had
 * existed. `invalidateQueries` on a key nobody reads succeeds silently.
 */
const REFRESH_ON: Record<NotificationKind, readonly QueryKeyRoot[]> = {
  MESSAGE: ['inbox'],
  MATCH: ['inbox', 'matches', 'allowance'],
  // Was `['me']`, which refetched the whole profile and left the list this notification is
  // actually about untouched.
  FRIEND_REQUEST: ['friendRequests'],
  FRIEND_ACCEPTED: ['friends', 'inbox'],
  BADGE: ['badges', 'cosmetics'],
  // The lobby screens refetch on focus and on socket frames; the list is the one thing a
  // push should freshen so the tab is right when they get there.
  LOBBY_JOIN_REQUEST: ['my-lobbies'],
  LOBBY_REQUEST_ACCEPTED: ['my-lobbies'],
  LOBBY_MESSAGE: [],
  LOBBY_CANCELLED: ['my-lobbies'],
  RETURN: [],
};
