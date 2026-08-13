import { useQueryClient } from '@tanstack/react-query';
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
        onPress: () => router.push(routeFor(kind, data?.targetId, content.title ?? undefined) as never),
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
  COMMUNITY_POST: { icon: Users, tone: 'primary' },
  POST_LIKE: { icon: Users, tone: 'primary' },
  POST_COMMENT: { icon: Users, tone: 'primary' },
  COMMENT_LIKE: { icon: Users, tone: 'primary' },
  RETURN: { icon: Bell, tone: 'muted' },
};

/** Query keys each kind invalidates. Empty where the destination fetches fresh anyway. */
const REFRESH_ON: Record<NotificationKind, readonly string[]> = {
  MESSAGE: ['conversations'],
  MATCH: ['conversations', 'allowance'],
  FRIEND_REQUEST: ['me'],
  FRIEND_ACCEPTED: ['me', 'conversations'],
  BADGE: ['badges', 'cosmetics'],
  COMMUNITY_POST: [],
  POST_LIKE: [],
  POST_COMMENT: [],
  COMMENT_LIKE: [],
  RETURN: [],
};
