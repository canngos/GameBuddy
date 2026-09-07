import { useQueryClient } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';
import { profileApi } from '../api/catalogue';
import { tNow } from '../i18n/useT';
import type { QueryKeyRoot } from '../query/keys';
import { useCelebration } from '../match/celebration';

/**
 * Turns a match push that arrives while the app is open into the celebration.
 *
 * **This is the half of a match that was never delivered in-app.** The backend sends a
 * `MATCH` push to *both* gamers — `DefaultMatchService.notifyMatched` is called twice, once
 * per side — but the client only ever celebrated the swiper, from the accept response. The
 * other person, who liked first and may have been on another screen for days, got a system
 * banner reading "You matched with X" and nothing else.
 *
 * `addNotificationReceivedListener` fires only while the app is foregrounded. Backgrounded
 * and cold-start taps are already handled by `useNotificationRouting`, which sends them to
 * the conversation — that is the right destination for a notification acted on later, and
 * this deliberately does not change it. A celebration is for the moment it happens; opening
 * one an hour after the fact would be a jump-scare, not a delight.
 *
 * The swiper receives this push too, which is why {@link useCelebration} dedupes on
 * `userId`: their overlay is already up from the accept response by the time it lands.
 */
export function useMatchNotifications(enabled: boolean) {
  const celebrate = useCelebration((s) => s.celebrate);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;

    const subscription = Notifications.addNotificationReceivedListener((notification) => {
      const data = notification.request.content.data as
        | { kind?: string; targetId?: string }
        | undefined;

      if (data?.kind !== 'MATCH' || !data.targetId) return;

      const otherId = data.targetId;

      // A match changes the deck's allowance and adds a conversation. Both are cheap to
      // refresh and both are wrong until they are.
      //
      // The first was `['conversations']`, a key nothing fetches, so the new match did not
      // appear until the inbox's own poll came round. `['matches']` matters too: a match
      // with no messages yet is a row that comes from there, not from the inbox.
      void queryClient.invalidateQueries({ queryKey: ['inbox'] satisfies QueryKeyRoot[] });
      void queryClient.invalidateQueries({ queryKey: ['matches'] satisfies QueryKeyRoot[] });
      void queryClient.invalidateQueries({ queryKey: ['allowance'] satisfies QueryKeyRoot[] });

      /*
       * The push carries an id and a line of copy, not a profile — so the avatar and the
       * frame have to be fetched before there is anything worth celebrating with.
       *
       * Failure is swallowed on purpose. The notification banner has already been
       * suppressed for this kind (see `usePushRegistration`), so an error here would mean
       * the gamer learns nothing at all — but they still have the conversation waiting in
       * Messages, and an error toast about a *match* would be a strange way to deliver good
       * news. The refetches above run either way.
       */
      profileApi
        .byId(otherId)
        .then((profile) => {
          celebrate({
            userId: otherId,
            username: profile.username ?? tNow().deck.match.someone,
            avatar: profile.avatar,
            frame: profile.frame,
          });
        })
        .catch(() => {});
    });

    return () => subscription.remove();
  }, [enabled, celebrate, queryClient]);
}
