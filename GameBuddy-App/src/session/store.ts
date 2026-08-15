import { create } from 'zustand';
import { authApi } from '../api/auth';
import { profileApi } from '../api/catalogue';
import { setSessionExpiredHandler, setTokenProvider } from '../api/client';
import type { UserInfo } from '../api/types';
import { secureStorage } from './storage';
import { shouldRefresh } from './tokenClock';

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
  | 'ready'
  /**
   * The moderator. A separate status rather than a flag on 'ready' because it is a
   * different app: the console has its own route group, and an admin must never land
   * in the deck — the guards are what make that structural instead of a hidden tab.
   */
  | 'admin';

type SessionState = {
  status: SessionStatus;
  token: string | null;
  userId: string | null;

  /** Called once at startup. Resolves the stored token into a status. */
  restore: () => Promise<void>;
  /**
   * Renews the token if it is past half its life. Safe to call often — it is a no-op
   * for a fresh token — and safe to call when signed out.
   */
  renewIfStale: () => Promise<void>;
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

    // After the status is settled, not before: renewing is housekeeping, and making
    // the first screen wait on it would trade a visible delay for an invisible gain.
    void get().renewIfStale();
  },

  /**
   * The sliding half of the session.
   *
   * The token lasts a week from when it was issued, and that is a hard stop — without
   * this, somebody who opens the app every single day is still thrown out every seventh
   * day, for no reason they can see. Renewing on use turns that into "you stay signed in
   * as long as you keep playing", while an abandoned session still lapses a week after
   * it was last touched.
   *
   * Failures are swallowed on purpose. The token in hand is still valid — it has at
   * least half its life left, which is what made this a renewal rather than an expiry —
   * so a failed attempt costs nothing and will be retried on the next launch or the next
   * time the app comes back to the foreground.
   */
  renewIfStale: async () => {
    const token = get().token;
    if (!token || !shouldRefresh(token)) return;

    try {
      const renewed = await authApi.refresh();
      // The store may have moved on while the request was in flight — a sign-out, or a
      // sign-in as somebody else. Writing the new token then would resurrect a session
      // the user just ended.
      if (get().token !== token) return;

      await persist(renewed.accessToken, renewed.userId);
      set({ token: renewed.accessToken, userId: renewed.userId });
    } catch {
      // Includes the server refusing because the session hit its ceiling, which arrives
      // as TOKEN_INVALID and has already signed the user out through the expiry handler.
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
  // Before the onboarding checks, deliberately. The moderator account has no age and
  // no username step to complete, so asking those questions first would send it to a
  // profile form it can never finish.
  if (me.role === 'ADMIN') return 'admin';
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
