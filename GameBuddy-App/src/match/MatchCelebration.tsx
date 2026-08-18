import { useQueryClient } from '@tanstack/react-query';
import { usePathname, useRouter } from 'expo-router';
import type { QueryKeyRoot } from '../query/keys';
import { useCelebration } from './celebration';
import { MatchOverlay } from './MatchOverlay';

/**
 * The match celebration, mounted once for the whole app.
 *
 * Lives in `app/(main)/_layout.tsx` rather than on the deck, because a match can now be
 * raised from two places: the accept response the swiper gets, and a push landing on the
 * other party while they are somewhere else entirely. See `celebration.ts`.
 *
 * Above the tabs and inside the route guard, so it can never appear over onboarding or for
 * an account that is not signed in.
 */
export function MatchCelebration() {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const matched = useCelebration((s) => s.matched);
  const dismiss = useCelebration((s) => s.dismiss);

  return (
    <MatchOverlay
      candidate={matched}
      onDismiss={dismiss}
      onMessage={(gamer) => {
        // Dismiss before navigating. The overlay is mounted above the whole tab navigator
        // now, so leaving it up would follow the gamer into the conversation and sit on
        // top of it.
        dismiss();
        // The inbox has a new thread in it that it does not know about yet.
        //
        // `'inbox'`, not `'conversations'`. The latter was the key here for the whole life
        // of this component and no query has ever used it, so this line did nothing.
        void queryClient.invalidateQueries({ queryKey: ['inbox'] satisfies QueryKeyRoot[] });
        const conversation = {
          pathname: '/messages/[friendId]',
          params: { friendId: gamer.userId, username: gamer.username },
        } as never;
        // Where back-from-the-chat lands depends on where the match was raised. Matched
        // on their profile inside the Messages stack: the chat *replaces* the profile,
        // so back goes to the inbox rather than bouncing off the profile of somebody
        // just messaged. Matched anywhere else (the deck, mostly): a cross-tab push,
        // anchored so the inbox sits underneath the new conversation.
        if (pathname.startsWith('/messages/gamer/')) {
          router.replace(conversation);
        } else {
          router.push(conversation, { withAnchor: true });
        }
      }}
    />
  );
}
