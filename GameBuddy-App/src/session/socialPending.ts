import { create } from 'zustand';

/** What the consent step will retry with, kept together with which call to retry. */
export type HeldCredential =
  | { kind: 'google'; idToken: string }
  | { kind: 'discord'; ticket: string };

type SocialPendingState = {
  credential: HeldCredential | null;
  hold: (credential: HeldCredential) => void;
  release: () => void;
};

/**
 * The credential waiting on the consent step.
 *
 * The step is its own route, so the credential has to outlive the screen that obtained it.
 * A store rather than a route param, because one of the two things it holds is a Google ID
 * token, and a token in a URL is a token in the navigation history, in logs, and in every
 * deep-link handler that ever sees it. Not persisted for the same reason: it lives exactly
 * as long as the process and the step need it, and is released the moment a session exists.
 */
export const useSocialPending = create<SocialPendingState>((set) => ({
  credential: null,
  hold: (credential) => set({ credential }),
  release: () => set({ credential: null }),
}));
