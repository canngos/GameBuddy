import { create } from 'zustand';

type PasswordResetState = {
  /** The address the code was mailed to, carried between the three screens. */
  email: string;
  /**
   * The ticket returned by `/auth/reset/verify`, spendable once within ten minutes.
   * Empty until the code has been verified.
   */
  ticket: string;
  begin: (email: string) => void;
  hold: (ticket: string) => void;
  clear: () => void;
};

/**
 * The state of a forgotten-password reset, while it is in progress.
 *
 * **Not route params, because the ticket is a credential.** Whoever holds it can set the
 * account's password without the code and without the old one, so it does not belong in a
 * URL that the router logs, restores from history, and hands to every screen in the stack.
 *
 * **Not the session store either**, and this one is structural rather than cautious: the
 * `(auth)` group is wrapped in `RouteGuard allow={(s) => s === 'signedOut'}`, so anything
 * that looks like a session redirects the user out of the flow they are halfway through.
 * The reset deliberately never holds a token — it ends at a card that sends the user to
 * sign in with the password they just chose.
 *
 * Cleared on success and whenever the flow is restarted, so a spent ticket is not left
 * lying in memory behind a screen nobody is looking at.
 */
export const usePasswordReset = create<PasswordResetState>((set) => ({
  email: '',
  ticket: '',
  // Starting over drops any ticket from a previous attempt: the code that minted it has
  // been invalidated by the new one, so keeping it would only allow a confusing failure.
  begin: (email) => set({ email, ticket: '' }),
  hold: (ticket) => set({ ticket }),
  clear: () => set({ email: '', ticket: '' }),
}));
