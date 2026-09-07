import type { QueryClient } from '@tanstack/react-query';
import * as StoreReview from 'expo-store-review';
import { engagementApi } from '../api/engagement';
import { trackFunnel } from '../api/funnel';
import type { Subscription } from '../api/types';
import { useTutorial } from '../tutorial/store';
import { useHints } from '../hints/store';

/**
 * Asks Google Play for the in-app review card, at the one moment worth spending it.
 *
 * Called when a match celebration is dismissed. That is the only point in this app where
 * somebody has just been given exactly what they came for and is not in the middle of
 * anything — and a review asked for then is a review asked in good faith rather than a
 * pop-up that turned up on a Tuesday.
 *
 * **Never on the way into a conversation.** The celebration has two exits: "Message", which
 * goes to the chat, and everything else. Asking on the first would interrupt the thing the
 * match was for, so only the second one calls this.
 *
 * **No pre-prompt.** Play forbids asking "enjoying GameBuddy?" before showing the card, and
 * it is a poor practice besides: the card itself is one tap to dismiss, and a question in
 * front of it is a second interruption to filter the first.
 *
 * **Everything here fails silently.** A dropped review is worth nothing; an error toast
 * after a match is worth less than nothing.
 */
export async function maybeRequestReview(queryClient: QueryClient): Promise<void> {
  try {
    // Two things outrank it locally, before the server is even asked — a granted claim is
    // spent whether or not anything is shown, so it must not be spent underneath something
    // else. The tutorial is mounted above this and would simply cover the card. The upgrade
    // prompt is the same kind of interruption aimed at the same quiet moment, and one ask
    // per moment is the limit; money goes first because it is the rarer chance.
    if (useTutorial.getState().step !== null) return;
    if (useHints.getState().active !== null) return;

    const subscription = queryClient.getQueryData<Subscription>(['subscription']);
    if (subscription?.upgradePromptDue) return;

    // Asked before the round trip, because a device that cannot show the card should not
    // burn the server's ninety-day claim. `hasAction` is false on a build with no Play
    // Store behind it — an emulator image without Play Services, most usefully.
    if (!(await StoreReview.isAvailableAsync())) return;
    if (!(await StoreReview.hasAction())) return;

    const { due } = await engagementApi.claimReviewPrompt();
    if (!due) return;

    // Long enough for the celebration's exit animation to finish. The card arrives as a
    // system sheet over whatever is on screen, and over a half-faded overlay it reads as
    // two things fighting rather than one thing following another.
    await new Promise((resolve) => setTimeout(resolve, 600));

    trackFunnel('REVIEW_PROMPTED');
    await StoreReview.requestReview();
  } catch (error) {
    // Includes the case where the native module is missing entirely, which is what a build
    // made before this shipped looks like from here.
    if (__DEV__) console.warn('[review] could not ask', error);
  }
}
