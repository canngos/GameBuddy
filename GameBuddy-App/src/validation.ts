/**
 * Client-side copies of the server's rules.
 *
 * These exist to answer instantly, not to enforce anything — the backend validates
 * everything again and is the only authority. They deliberately mirror
 * `PasswordPolicy.java` and the bean-validation annotations on the auth requests; if
 * those change, these are wrong until updated, which is the accepted cost of not
 * making the user wait for a round trip to be told their password is too short.
 *
 * Validators return a *message selector* — `(t) => t.validation.…` — rather than an
 * English string. The caller resolves it against the current dictionary at render time
 * (`problem && problem(t)`), so the module stays free of React and the compiler forces
 * every call site to translate rather than leak English. Limits like `PASSWORD_MIN` are
 * baked in here, not hardcoded in seven dictionaries, so the policy has one source.
 */

import type { Dictionary } from './i18n/dictionaries/en';

/** A validation message, waiting to be resolved against the current language. */
export type Msg = (t: Dictionary) => string;

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;
export const MIN_GAMES = 3;
export const MIN_KEYWORDS = 5;
/**
 * GameBuddy is an adults-only service. Mirrors `AgePolicy.java`, which is the authority:
 * the server computes the age from the date of birth and refuses anything below this.
 */
export const MIN_AGE = 18;
export const MAX_AGE = 99;

/** Deliberately loose. Address validity is decided by whether the code arrives. */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailError(value: string): Msg | null {
  if (!value.trim()) return (t) => t.validation.emailEmpty;
  if (!EMAIL.test(value.trim())) return (t) => t.validation.emailInvalid;
  return null;
}

export function passwordError(value: string): Msg | null {
  if (!value) return (t) => t.validation.passwordEmpty;
  if (value.length < PASSWORD_MIN) return (t) => t.validation.atLeastChars(PASSWORD_MIN);
  if (value.length > PASSWORD_MAX) return (t) => t.validation.atMostChars(PASSWORD_MAX);
  if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    return (t) => t.validation.letterAndNumber;
  }
  return null;
}

/**
 * Mirrors `UsernamePolicy.java`, which is the authority — these rules used to live only
 * here, which meant they were not enforced at all for anyone not using this app.
 */
const RESERVED = new Set([
  'admin',
  'administrator',
  'moderator',
  'mod',
  'staff',
  'support',
  'help',
  'system',
  'gamebuddy',
  'official',
  'root',
  'null',
  'undefined',
]);

export function usernameError(value: string): Msg | null {
  const trimmed = value.trim();
  if (!trimmed) return (t) => t.validation.usernameEmpty;
  if (trimmed.length < 3) return (t) => t.validation.atLeastChars(3);
  if (trimmed.length > 20) return (t) => t.validation.atMostChars(20);
  if (!/^[a-zA-Z0-9_]+$/.test(trimmed)) return (t) => t.validation.usernameCharset;
  if (!/[a-zA-Z0-9]/.test(trimmed)) return (t) => t.validation.usernameLetterOrNumber;
  const lower = trimmed.toLowerCase();
  if (RESERVED.has(lower) || lower.startsWith('deleted_')) {
    return (t) => t.validation.usernameUnavailable;
  }
  return null;
}

/** Completed years between a date of birth and today. */
export function ageFrom(birthDate: Date, today = new Date()): number {
  let age = today.getFullYear() - birthDate.getFullYear();
  const monthDelta = today.getMonth() - birthDate.getMonth();
  // Not had this year's birthday yet.
  if (monthDelta < 0 || (monthDelta === 0 && today.getDate() < birthDate.getDate())) {
    age -= 1;
  }
  return age;
}

/**
 * Parses a day/month/year the user typed, rejecting dates that do not exist.
 *
 * `new Date(1999, 1, 31)` silently rolls over to 3 March, so the parts are compared back
 * against the parsed date — otherwise 31 February would be accepted as a birthday.
 */
export function parseBirthDate(day: string, month: string, year: string): Date | null {
  const d = Number(day);
  const m = Number(month);
  const y = Number(year);
  if (!d || !m || !y || year.length !== 4) return null;

  const parsed = new Date(y, m - 1, d);
  if (parsed.getFullYear() !== y || parsed.getMonth() !== m - 1 || parsed.getDate() !== d) {
    return null;
  }
  return parsed;
}

export function birthDateError(day: string, month: string, year: string): Msg | null {
  if (!day.trim() || !month.trim() || !year.trim()) return (t) => t.validation.birthDateEmpty;

  const parsed = parseBirthDate(day, month, year);
  if (!parsed) return (t) => t.validation.notARealDate;
  if (parsed > new Date()) return (t) => t.validation.dateInFuture;

  const age = ageFrom(parsed);
  if (age < MIN_AGE) return (t) => t.validation.mustBeAge(MIN_AGE);
  if (age > MAX_AGE) return (t) => t.validation.checkYear;
  return null;
}

/** `yyyy-MM-dd`, which is what the API takes. Built by hand to avoid a UTC shift. */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Six digits. The backend rejects anything outside 100000–999999. */
export function codeError(value: string): Msg | null {
  if (!/^\d{6}$/.test(value.trim())) return (t) => t.validation.sixDigitCode;
  return null;
}
