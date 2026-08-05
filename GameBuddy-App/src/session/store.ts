import { create } from 'zustand';
import { profileApi } from '../api/catalogue';
import { setSessionExpiredHandler, setTokenProvider } from '../api/client';
import type { UserInfo } from '../api/types';
import { secureStorage } from './storage';

const TOKEN_KEY = 'gamebuddy.accessToken';
const USER_ID_KEY = 'gamebuddy.userId';

/**
 * Where the user is, as far as navigation is concerned.
 *
 * Onboarding is two server-side steps, and a token is issued *before* either of them
 * — verification hands one out, and `/auth/username` needs it. So "has a token" does
 * not mean "can use the app", and the guard has to distinguish the halfway states or
 * a user who closes the app between the two steps reopens it into a broken home screen.
 */
export type SessionStatus =
  /** Reading storage. Nothing should be rendered yet. */
  | 'loading'
  | 'signedOut'
  | 'needsUsername'
  | 'needsDetails'
  | 'ready';

type SessionState = {
  status: SessionStatus;
  token: string | null;
  userId: string | null;

  /** Called once at startup. Resolves the stored token into a status. */
  restore: () => Promise<void>;
  /**
   * After verification. Stores the token, then asks the server how far this account
   * actually got — a code can be requested at any time, so this is the recovery path
   * for a half-finished account as well as the happy path for a new one.
   */
  adoptToken: (token: string, userId: string) => Promise<void>;
  /** After login. Resolves how far onboarding actually got before landing anywhere. */
  signIn: (token: string, userId: string) => Promise<void>;
  usernameChosen: () => void;
  detailsCompleted: () => void;
  signOut: () => Promise<void>;
};

export const useSession = create<SessionState>((set, get) => ({
  status: 'loading',
  token: null,
  userId: null,

  restore: async () => {
    const [token, userId] = await Promise.all([
      secureStorage.get(TOKEN_KEY),
      secureStorage.get(USER_ID_KEY),
    ]);

    if (!token || !userId) {
      set({ status: 'signedOut', token: null, userId: null });
      return;
    }

    // Set the token first: the profile call below needs it, and the provider reads
    // straight from this store.
    set({ token, userId });

    try {
      const me = await profileApi.me();
      set({ status: stageOf(me) });
    } catch (error) {
      // An expired or rejected token already triggered the session-expired handler,
      // which clears everything. Anything else — the backend being down, no network —
      // must not sign the user out; they would lose their session because their train
      // went into a tunnel. Let them through and let the first real request fail.
      if (get().token) set({ status: 'ready' });
    }
  },

  adoptToken: async (token, userId) => {
    await persist(token, userId);
    set({ token, userId });
    try {
      set({ status: stageOf(await profileApi.me()) });
    } catch {
      // The profile read failed on a token the server just issued, so the token is
      // fine and the network is not. A brand-new account is the overwhelmingly likely
      // case, and starting at the username step is recoverable — completing a step
      // that was already done is idempotent, whereas landing on the home screen with
      // no profile is not.
      set({ status: 'needsUsername' });
    }
  },

  signIn: async (token, userId) => {
    await persist(token, userId);
    // Before the status, so the profile call below has a token to send.
    set({ token, userId });

    // Logging in does not prove onboarding finished. A token is issued at verification,
    // two steps before the account is usable, so somebody who closed the app partway
    // through and came back later signs in with a real password and an empty username.
    // Assuming 'ready' here dropped them on a home screen the app cannot render, and
    // the mistake only corrected itself on the next cold start, when `restore` asked
    // the question that should have been asked now.
    try {
      set({ status: stageOf(await profileApi.me()) });
    } catch {
      // The password was just accepted, so the token is good and the network is not.
      // 'ready' is the right guess: an account that can log in has almost always
      // finished onboarding, and the guard re-resolves on the next launch either way.
      set({ status: 'ready' });
    }
  },

  usernameChosen: () => set({ status: 'needsDetails' }),

  detailsCompleted: () => set({ status: 'ready' }),

  signOut: async () => {
    // Clear the state before storage: the guard should redirect immediately rather
    // than wait on a keychain write.
    set({ status: 'signedOut', token: null, userId: null });
    await Promise.all([secureStorage.remove(TOKEN_KEY), secureStorage.remove(USER_ID_KEY)]);
  },
}));

async function persist(token: string, userId: string) {
  await Promise.all([
    secureStorage.set(TOKEN_KEY, token),
    secureStorage.set(USER_ID_KEY, userId),
  ]);
}

/**
 * Reads onboarding progress off the profile rather than remembering it locally.
 *
 * The server is the only thing that actually knows: reinstalling the app, or signing
 * in on a second device, would both defeat a locally-tracked flag.
 */
function stageOf(me: UserInfo): SessionStatus {
  if (!me.username) return 'needsUsername';
  // `age` is the field `/auth/details` sets and nothing else does, which makes it the
  // reliable marker that the step completed. Games and keywords are set in the same
  // transaction, so any of the three would do.
  if (!me.age) return 'needsDetails';
  return 'ready';
}

/**
 * Wires the API client to this store. Called once, from the root layout, before any
 * request is made.
 */
export function connectSessionToApi(): void {
  setTokenProvider(() => useSession.getState().token);
  setSessionExpiredHandler(() => {
    // Guard against the stampede: a screen with four queries in flight will report
    // four expiries, and each one would otherwise re-enter sign-out.
    if (useSession.getState().status !== 'signedOut') {
      void useSession.getState().signOut();
    }
  });
}
