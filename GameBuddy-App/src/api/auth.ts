import { api } from './client';
import type { ProfileDetails, Session } from './types';

/**
 * The device token Firebase would normally supply. The backend requires the field on
 * registration, and push is not wired up yet, so a placeholder goes in and
 * `PUT /auth/fcm-token` replaces it once notifications are added.
 */
const PLACEHOLDER_FCM_TOKEN = 'pending';

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
   */
  register: (email: string, password: string, fcmToken = PLACEHOLDER_FCM_TOKEN) =>
    api.post<void>('/auth/register', { email, password, fcmToken }, { anonymous: true }),

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
   * Accepts either the username or the email.
   *
   * Refuses with code 109 until onboarding is finished — that is the intended flow,
   * and the caller should resume onboarding rather than treat it as a failure.
   */
  login: (usernameOrEmail: string, password: string) =>
    api.post<Session>('/auth/login', { usernameOrEmail, password }, { anonymous: true }),

  /** Confirms the stored token is still good, and is how the app resumes a session. */
  validateToken: () => api.post<{ userId: string }>('/auth/validateToken'),

  setUsername: (username: string) => api.post<void>('/auth/username', { username }),

  /** Second and final onboarding step. Completing it is what makes login work. */
  setDetails: (details: ProfileDetails) => api.post<void>('/auth/details', details),

  updateFcmToken: (fcmToken: string) => api.put<void>('/auth/fcm-token', { fcmToken }),

  /** The new password is sent as `password`, not `newPassword`. */
  changePassword: (currentPassword: string, password: string) =>
    api.put<void>('/auth/change/pwd', { currentPassword, password }),

  changeAvatar: (avatarId: string) => api.put<void>('/auth/change/avatar', { avatarId }),

  changeAge: (age: number) => api.put<void>('/auth/change/age', { age }),

  changeGames: (gameIds: string[]) =>
    api.put<void>('/auth/change/games', { gamesOrKeywordsList: gameIds }),

  changeKeywords: (keywordIds: string[]) =>
    api.put<void>('/auth/change/keywords', { gamesOrKeywordsList: keywordIds }),

  /** Requires the password: a stolen token must not be enough to delete an account. */
  deleteAccount: (currentPassword: string) =>
    api.delete<void>('/auth/account', { currentPassword }),
};
