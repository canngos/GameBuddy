import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
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
        void queryClient.invalidateQueries({ queryKey: ['conversations'] });
        router.push({
          pathname: '/messages/[friendId]',
          params: { friendId: gamer.userId, username: gamer.username },
        } as never);
      }}
    />
  );
}
