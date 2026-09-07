import { api } from './client';
import type {
  AuthProvider,
  LinkedProvider,
  ProfileDetails,
  Session,
  SocialSession,
} from './types';

export const authApi = {
  /**
   * Creates the account and mails a six-digit code.
   *
   * The whole transaction is rolled back if the mail cannot be sent, so a failure
   * here leaves no account behind and the same address can be tried again.
   *
   * Registering an address that already exists but was never verified is allowed and
   * simply re-issues the code — an unverified record is not evidence anyone owns the
   * mailbox. Only a *verified* address comes back as EMAIL_EXISTS, which is why that
   * branch on the register screen is rarer than it looks.
   *
   * **No device token is sent.** There cannot be one yet: on Android 13+ notification
   * permission has not been asked for at this point, so the only thing we could send is a
   * placeholder — and the backend used to store it, giving every not-yet-registered
   * account the same token and breaking the lookups that resolve a gamer by it. The device
   * registers itself after sign-in, in `usePushRegistration`.
   *
   * `acceptedTerms` is not a formality the client can shortcut: the server refuses the
   * registration without it (169) and stamps the account with the version and the moment
   * of acceptance. Passing `true` from anywhere the user did not actually tick the box
   * would forge that record.
   */
  register: (email: string, password: string, acceptedTerms: boolean) =>
    api.post<void>('/auth/register', { email, password, acceptedTerms }, { anonymous: true }),

  /** Exchanges the code for a token. This is where a new user first gets a session. */
  verify: (email: string, verificationCode: number) =>
    api.post<Session>('/auth/verify', { email, verificationCode }, { anonymous: true }),

  /**
   * Re-sends the code. Rate limited server-side.
   *
   * `isRegister` only picks the wording of the mail — a "confirm your address" subject
   * versus a password-reset one. The code itself is identical either way.
   */
  sendCode: (email: string, isRegister = true) =>
    api.post<void>('/auth/sendCode', { email, isRegister }, { anonymous: true }),

  /**
   * Step one of a forgotten-password reset: spends the mailed code for a ticket.
   *
   * The code is requested through `sendCode(email, false)` above — there is no separate
   * "start a reset" call, and deliberately so: that endpoint already answers the same way
   * whether or not the address has an account, and adding one would be an easier oracle.
   *
   * The ticket is a credential in its own right, valid for ten minutes and spendable once.
   * It is returned exactly this once and is never stored server-side in a readable form.
   */
  resetVerify: (email: string, verificationCode: number) =>
    api.post<{ resetToken: string }>(
      '/auth/reset/verify',
      { email, verificationCode },
      { anonymous: true },
    ),

  /**
   * Step two: sets the password and ends every session the account had.
   *
   * Returns no token on purpose. The caller has just proved control of the mailbox, not
   * of the account, so the way back in is the ordinary login with the new password.
   */
  resetPassword: (email: string, resetToken: string, password: string) =>
    api.post<void>('/auth/reset/pwd', { email, resetToken, password }, { anonymous: true }),

  /**
   * Accepts either the username or the email.
   *
   * Refuses with code 109 until onboarding is finished — that is the intended flow,
   * and the caller should resume onboarding rather than treat it as a failure.
   */
  login: (usernameOrEmail: string, password: string) =>
    api.post<Session>('/auth/login', { usernameOrEmail, password }, { anonymous: true }),

  /** Confirms the stored token is still good, and is how the app resumes a session. */
  validateToken: () => api.post<{ userId: string }>('/auth/validateToken'),

  /**
   * Trades the current token for one with a later expiry, keeping the session alive.
   *
   * Called by the session store when the stored token is past half its life — not on
   * every launch. The session keeps its original start date across refreshes, so this
   * extends a session but cannot hold one open indefinitely: past the server's ceiling
   * it answers TOKEN_INVALID and the app asks for the password again.
   */
  refresh: () => api.post<Session>('/auth/refresh'),

  setUsername: (username: string) => api.post<void>('/auth/username', { username }),

  /** Second and final onboarding step. Completing it is what makes login work. */
  setDetails: (details: ProfileDetails) => api.post<void>('/auth/details', details),

  updateFcmToken: (fcmToken: string) => api.put<void>('/auth/fcm-token', { fcmToken }),

  /** The new password is sent as `password`, not `newPassword`. */
  changePassword: (currentPassword: string, password: string) =>
    api.put<void>('/auth/change/pwd', { currentPassword, password }),

  changeAvatar: (avatarId: string) => api.put<void>('/auth/change/avatar', { avatarId }),

  /**
   * Takes a date, not a number. The server derives the age, refuses anything under 18,
   * and logs the change — see AgePolicy and DefaultAuthService#changeAge.
   */
  changeBirthDate: (birthDate: string) => api.put<void>('/auth/change/age', { birthDate }),

  changeGames: (gameIds: string[]) =>
    api.put<void>('/auth/change/games', { gamesOrKeywordsList: gameIds }),

  changeKeywords: (keywordIds: string[]) =>
    api.put<void>('/auth/change/keywords', { gamesOrKeywordsList: keywordIds }),

  /**
   * Platform enum names, at least one.
   *
   * Shares the games/keywords request shape — the body is a list of identifiers either
   * way, and the server refuses any name it does not recognise rather than dropping it.
   */
  changePlatforms: (platformIds: string[]) =>
    api.put<void>('/auth/change/platforms', { gamesOrKeywordsList: platformIds }),

  /**
   * Requires the password: a stolen token must not be enough to delete an account.
   *
   * Optional, because an account created through Google has no password. The server then
   * requires the session to be minutes old instead — sign in again, come back, confirm —
   * and answers `REAUTH_REQUIRED` when it is not.
   */
  deleteAccount: (currentPassword?: string) =>
    api.delete<void>('/auth/account', { currentPassword }),

  /** Sets a first password on an account that has none. No current password to prove. */
  setPassword: (password: string) => api.post<void>('/auth/password', { password }),

  /**
   * Which social providers this deployment can offer.
   *
   * Read before the welcome screen draws its buttons, and cached hard: it is deployment
   * configuration and cannot change without a redeploy.
   */
  socialProviders: () =>
    api.get<{ providers: AuthProvider[] }>('/auth/social/providers', { anonymous: true }),

  /**
   * Signs in with a Google ID token from the device's account sheet.
   *
   * `acceptedTerms` is sent only on a retry: the server refuses a brand-new account with
   * `TERMS_NOT_ACCEPTED`, the app shows the consent sheet, and the same token comes back
   * with the tick. Google ID tokens are valid for about an hour, so the retry is safe.
   */
  socialGoogle: (idToken: string, acceptedTerms?: boolean) =>
    api.post<SocialSession>('/auth/social/google', { idToken, acceptedTerms }, { anonymous: true }),

  /** Starts a Discord sign-in and returns the URL to open in the system browser. */
  socialDiscordStart: () =>
    api.post<{ authorizeUrl: string }>('/auth/social/discord/start', undefined, { anonymous: true }),

  /**
   * Trades the ticket the callback deep-linked back for a session.
   *
   * Not the ticket that travelled through Discord — that one is already spent. See
   * `app/social.tsx`.
   */
  socialExchange: (ticket: string, acceptedTerms?: boolean) =>
    api.post<SocialSession>('/auth/social/exchange', { ticket, acceptedTerms }, { anonymous: true }),


  /**
   * Which providers this deployment actually has credentials for.
   *
   * <p>Configuration rather than account data, but the app has no other way to know it: the
   * credentials are per-deployment and optional by design, so a build that always drew the
   * row would offer a button that could only ever fail — which reads as a broken app rather
   * than an unconfigured one.
   */
  linkProviders: () => api.get<{ providers: LinkedProvider[] }>('/auth/link/providers'),

  /**
   * Starts a Discord link and returns the URL to open.
   *
   * The app does not do OAuth itself. It asks for a URL, opens it in the **system
   * browser**, and the backend handles the rest — the code exchange needs a client secret
   * that must never ship inside an app, and a password typed into a WebView we control is
   * a password we could have read.
   *
   * The URL carries a single-use ticket, not the session token. What comes back is a
   * `gamebuddy://settings/linked` deep link, which is why the screen that starts this is
   * also the screen that reports the result.
   */
  linkStart: (provider: LinkedProvider) =>
    api.post<{ authorizeUrl: string }>(`/auth/link/${lower(provider)}/start`),

  unlink: (provider: LinkedProvider) => api.delete<void>(`/auth/link/${lower(provider)}`),

  setLinkVisibility: (provider: LinkedProvider, visibility: 'PUBLIC' | 'MATCHES') =>
    api.put<void>(`/auth/link/${lower(provider)}/visibility`, { visibility }),

};

const lower = (provider: LinkedProvider) => provider.toLowerCase();
