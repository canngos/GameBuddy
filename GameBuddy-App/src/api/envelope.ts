/**
 * Every backend response has the same shape, successful or not:
 *
 * ```json
 * { "status": { "code": "100", "message": "Success", "success": true },
 *   "body":   { "data": { ... } } }
 * ```
 *
 * `body` is absent on failures and on endpoints that only report an outcome, so it
 * is optional here rather than assumed. See `common/base/BaseResponse.java`.
 */
export type Envelope<T> = {
  status: Status;
  body?: { data: T } | null;
};

export type Status = {
  /** A `TransactionCode` id rendered as a string, e.g. "100", "109". */
  code: string;
  message: string;
  success: boolean;
};

/**
 * The application-level codes the client actually branches on. The backend defines
 * around sixty (`common/enums/TransactionCode.java`); listing all of them here would
 * be a second copy to keep in sync, so only the ones a screen reacts to appear.
 */
export const Code = {
  SUCCESS: '100',
  EMAIL_EXISTS: '101',
  EMAIL_SEND_FAILED: '102',
  USER_NOT_FOUND: '103',
  VERIFICATION_CODE_INVALID: '105',
  USER_NOT_VERIFIED: '106',
  USERNAME_EXISTS: '107',
  WRONG_PASSWORD: '108',
  /** Verified, but username/details were never submitted. Resume onboarding. */
  USER_NOT_COMPLETED: '109',
  TOKEN_INVALID: '110',
  TOKEN_NOT_FOUND: '111',
  PASSWORD_SAME: '112',
  USER_BLOCKED: '113',
  /** A cosmetic cost more than the balance. The Market offers coins rather than nagging. */
  COIN_NOT_ENOUGH: '129',
  // 131-139 belonged to the retired Community feature; the numbers stay spent on the
  // backend and are no longer branched on here.
  VERIFICATION_CODE_EXPIRED: '144',
  TOO_MANY_ATTEMPTS: '145',
  RATE_LIMITED: '146',
  WEAK_PASSWORD: '147',
  INVALID_REQUEST: '148',
  /** Distinct from WRONG_PASSWORD: this one is the *current* password on a change. */
  CURRENT_PASSWORD_WRONG: '150',
  ALREADY_REPORTED: '157',
  ACCEPT_LIMIT_REACHED: '158',
  SUBSCRIPTION_REQUIRED: '159',
  SWIPE_LIMIT_REACHED: '163',
  LOBBY_NOT_FOUND: '178',
  LOBBY_FULL: '179',
  /** The lobby is locked, ended or cancelled — refresh and show it as it is now. */
  LOBBY_NOT_OPEN: '180',
  LOBBY_ALREADY_MEMBER: '181',
  /** One live lobby per owner: finish or cancel the current one first. */
  LOBBY_LIMIT_REACHED: '183',
  /** The owner already said no, and that answer is final for this lobby. */
  LOBBY_REJECTED: '185',
} as const;

export type CodeValue = (typeof Code)[keyof typeof Code];

/**
 * A request that reached the backend and was refused, or never reached it at all.
 *
 * `code` carries the application code when there is one. Transport failures — no
 * network, DNS, a timeout — get {@link ApiError.NETWORK}, because a screen that wants
 * to say "check your connection" has no other way to tell that apart from a 503.
 */
export class ApiError extends Error {
  static readonly NETWORK = 'NETWORK';
  static readonly UNREADABLE = 'UNREADABLE';

  readonly code: string;
  readonly httpStatus: number;

  constructor(message: string, code: string, httpStatus: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
  }

  is(...codes: string[]): boolean {
    return codes.includes(this.code);
  }

  /** True when retrying the same request unchanged could plausibly succeed. */
  get isTransient(): boolean {
    return (
      this.code === ApiError.NETWORK ||
      this.httpStatus === 502 ||
      this.httpStatus === 503 ||
      this.httpStatus === 504
    );
  }

  /**
   * True when the session is gone and the app should return to the sign-in screen.
   *
   * Two different 401s exist, and only one of them is an expiry:
   *
   * - Spring Security rejects the request before it reaches a controller, and
   *   `SecurityConfig` answers with `HttpStatusEntryPoint(UNAUTHORIZED)` — a bare 401
   *   with **no body at all**, so there is no application code to read. That is
   *   always a token problem: an anonymous caller cannot produce it, because the
   *   endpoints that path guards all require authentication.
   * - A controller ran and refused. Those come back as a proper envelope, and the
   *   code says which: 110/111 are token failures, but 108 is a wrong password on the
   *   login form. Signing someone out because they mistyped would be absurd.
   */
  get isSessionExpired(): boolean {
    if (this.code === Code.TOKEN_INVALID || this.code === Code.TOKEN_NOT_FOUND) return true;
    return this.httpStatus === 401 && this.code === ApiError.UNREADABLE;
  }
}
