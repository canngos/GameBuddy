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
/**
 * GameBuddy is an adults-only service. Mirrors `AgePolicy.java`, which is the authority:
 * the server computes the age from the date of birth and refuses anything below this.
 */
export const MIN_AGE = 18;
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

export function usernameError(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return 'Pick a username';
  if (trimmed.length < 3) return 'At least 3 characters';
  if (trimmed.length > 20) return 'At most 20 characters';
  if (!/^[a-zA-Z0-9_]+$/.test(trimmed)) return 'Letters, numbers and underscores only';
  if (!/[a-zA-Z0-9]/.test(trimmed)) return 'Include at least one letter or number';
  const lower = trimmed.toLowerCase();
  if (RESERVED.has(lower) || lower.startsWith('deleted_')) return 'That username is not available';
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

export function birthDateError(day: string, month: string, year: string): string | null {
  if (!day.trim() || !month.trim() || !year.trim()) return 'Enter your date of birth';

  const parsed = parseBirthDate(day, month, year);
  if (!parsed) return 'That is not a real date';
  if (parsed > new Date()) return 'That date is in the future';

  const age = ageFrom(parsed);
  if (age < MIN_AGE) return `You must be ${MIN_AGE} or over to use GameBuddy`;
  if (age > MAX_AGE) return 'Check the year';
  return null;
}

/** `yyyy-MM-dd`, which is what the API takes. Built by hand to avoid a UTC shift. */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Six digits. The backend rejects anything outside 100000–999999. */
export function codeError(value: string): string | null {
  if (!/^\d{6}$/.test(value.trim())) return 'Enter the 6-digit code';
  return null;
}
