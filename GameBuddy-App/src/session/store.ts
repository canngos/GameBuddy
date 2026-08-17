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
 * The last stage the server reported, cached so a cold start can paint before asking.
 *
 * Only ever a hint. The server remains the authority — {@link verify} overwrites this
 * within a second of launch — but it is a far better first guess than `'ready'` for
 * somebody who closed the app halfway through onboarding.
 */
const STAGE_KEY = 'gamebuddy.sessionStage';

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

  /** Called once at startup. Resolves the stored token into a status, without blocking. */
  restore: () => Promise<void>;
  /** Confirms the stored status against the server. Runs in the background after restore. */
  verify: () => Promise<void>;
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

  /**
   * Resolves the stored token into a status, and **does not wait for the network to do
   * it.**
   *
   * This used to `await profileApi.me()` before leaving `'loading'`, and the root layout
   * renders nothing at all until the status settles — so a cold start showed a blank
   * screen for one whole round trip, and up to the client's twenty-second timeout on a
   * bad connection. That is the single worst number in the app and it is paid by every
   * session.
   *
   * Now the stored token is enough to paint, and {@link verify} corrects the guess in the
   * background. Three things make that safe rather than optimistic:
   *
   * - The correction is a redirect, not a crash. `RouteGuard` is built to move somebody
   *   between route groups without unmounting the navigator.
   * - Nothing private can leak, because every screen's data comes from its own
   *   authenticated request; a revoked token fails those too, and the expiry handler
   *   already signs the user out when it does.
   * - Guessing on failure is not new — the `catch` below has always fallen back to a
   *   status it could not confirm. This promotes that fallback to the fast path and
   *   makes the guess better by remembering the last known answer.
   */
  restore: async () => {
    const [token, userId, stage] = await Promise.all([
      secureStorage.get(TOKEN_KEY),
      secureStorage.get(USER_ID_KEY),
      secureStorage.get(STAGE_KEY),
    ]);

    if (!token || !userId) {
      set({ status: 'signedOut', token: null, userId: null });
      return;
    }

    // Painted from here. `'ready'` is the fallback for a session stored before this
    // cache existed, which is the same guess the old catch branch made.
    set({ token, userId, status: isStage(stage) ? stage : 'ready' });

    void get().verify();
  },

  /**
   * Asks the server where this account actually is, and corrects the guess.
   *
   * Not awaited by anything that paints. A failure is deliberately silent: an expired
   * token has already been handled by the session-expired handler, and anything else —
   * the backend down, no network — must not sign somebody out because their train went
   * into a tunnel.
   */
  verify: async () => {
    try {
      const stage = rememberStage(stageOf(await profileApi.me()));
      // Compared before writing so the overwhelmingly common case — the guess was right —
      // does not re-render the guard for nothing.
      if (get().status !== stage) set({ status: stage });
    } catch {
      // Left as it was. See the note above.
    }

    // Housekeeping, and last: making the first screen wait on a renewal would trade a
    // visible delay for an invisible gain.
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
      set({ status: rememberStage(stageOf(await profileApi.me())) });
    } catch {
      // The profile read failed on a token the server just issued, so the token is
      // fine and the network is not. A brand-new account is the overwhelmingly likely
      // case, and starting at the username step is recoverable — completing a step
      // that was already done is idempotent, whereas landing on the home screen with
      // no profile is not.
      set({ status: rememberStage('needsUsername') });
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
      set({ status: rememberStage(stageOf(await profileApi.me())) });
    } catch {
      // The password was just accepted, so the token is good and the network is not.
      // 'ready' is the right guess: an account that can log in has almost always
      // finished onboarding, and the guard re-resolves on the next launch either way.
      set({ status: rememberStage('ready') });
    }
  },

  usernameChosen: () => set({ status: rememberStage('needsDetails') }),

  detailsCompleted: () => set({ status: rememberStage('ready') }),

  signOut: async () => {
    // Clear the state before storage: the guard should redirect immediately rather
    // than wait on a keychain write.
    set({ status: 'signedOut', token: null, userId: null });
    await Promise.all([
      secureStorage.remove(TOKEN_KEY),
      secureStorage.remove(USER_ID_KEY),
      secureStorage.remove(STAGE_KEY),
    ]);
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
/**
 * The statuses worth caching: the ones `stageOf` can return.
 *
 * `'loading'` and `'signedOut'` are excluded because neither is a place to start — one
 * is the absence of an answer and the other is handled by there being no token at all.
 */
const STAGES = ['needsUsername', 'needsDetails', 'ready', 'admin'] as const;

function isStage(value: string | null): value is (typeof STAGES)[number] {
  return !!value && (STAGES as readonly string[]).includes(value);
}

/**
 * Writes the stage to storage and hands it straight back, so every place that decides a
 * status can cache it by wrapping the value rather than by remembering a second call.
 *
 * That shape is the point: the cache is only useful if it cannot drift from the status,
 * and a separate `set` next to every `set({ status })` is exactly the kind of pairing
 * somebody adds a sixth branch without.
 *
 * Not awaited. A failed keychain write costs a slightly worse guess on the next cold
 * start and nothing else, and no caller should wait on it.
 */
function rememberStage(status: SessionStatus): SessionStatus {
  if (isStage(status)) void secureStorage.set(STAGE_KEY, status);
  return status;
}

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
