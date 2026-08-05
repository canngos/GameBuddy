/**
 * Client-side copies of the server's rules.
 *
 * These exist to answer instantly, not to enforce anything — the backend validates
 * everything again and is the only authority. They deliberately mirror
 * `PasswordPolicy.java` and the bean-validation annotations on the auth requests; if
 * those change, these are wrong until updated, which is the accepted cost of not
 * making the user wait for a round trip to be told their password is too short.
 */

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;
export const MIN_GAMES = 3;
export const MIN_KEYWORDS = 5;
export const MIN_AGE = 12;
export const MAX_AGE = 99;

/** Deliberately loose. Address validity is decided by whether the code arrives. */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailError(value: string): string | null {
  if (!value.trim()) return 'Enter your email address';
  if (!EMAIL.test(value.trim())) return 'That does not look like an email address';
  return null;
}

export function passwordError(value: string): string | null {
  if (!value) return 'Enter a password';
  if (value.length < PASSWORD_MIN) return `At least ${PASSWORD_MIN} characters`;
  if (value.length > PASSWORD_MAX) return `At most ${PASSWORD_MAX} characters`;
  if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    return 'Include at least one letter and one number';
  }
  return null;
}

/**
 * Unlike the rest of this file, these rules are *stricter* than the backend, which
 * only requires the username to be non-blank and unique. A username is displayed to
 * strangers, so unbounded length and arbitrary characters are a layout problem and an
 * impersonation one (trailing spaces, lookalike scripts). Enforced here for now; the
 * backend should grow the same check, since a client is not a place to enforce
 * anything.
 */
export function usernameError(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return 'Pick a username';
  if (trimmed.length < 3) return 'At least 3 characters';
  if (trimmed.length > 20) return 'At most 20 characters';
  if (!/^[a-zA-Z0-9_]+$/.test(trimmed)) return 'Letters, numbers and underscores only';
  return null;
}

export function ageError(value: string): string | null {
  if (!value.trim()) return 'Enter your age';
  const age = Number(value);
  if (!Number.isInteger(age)) return 'Enter your age as a whole number';
  if (age < MIN_AGE || age > MAX_AGE) return `Must be between ${MIN_AGE} and ${MAX_AGE}`;
  return null;
}

/** Six digits. The backend rejects anything outside 100000–999999. */
export function codeError(value: string): string | null {
  if (!/^\d{6}$/.test(value.trim())) return 'Enter the 6-digit code';
  return null;
}
