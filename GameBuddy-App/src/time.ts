import type { Dictionary } from './i18n/dictionaries/en';

/**
 * "3h", "2d" — the compact age of something, for a timestamp sitting beside a name.
 *
 * Deliberately not a library. `Intl.RelativeTimeFormat` exists on Hermes but produces
 * "3 hours ago", which is three times the width for the same information in a row that
 * is already tight, and every date library worth using costs more than eleven lines.
 *
 * Anything older than a week gets a date instead: "how long ago" stops being the useful
 * framing once the answer is in weeks. The caller passes the dictionary so the unit
 * words and the date both follow the app's language, not the device locale.
 */
export function shortAgo(iso: string | null | undefined, t: Dictionary): string {
  if (!iso) return '';

  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return t.time.now;
  if (seconds < 3600) return t.time.minutesShort(Math.floor(seconds / 60));
  if (seconds < 86_400) return t.time.hoursShort(Math.floor(seconds / 3600));
  if (seconds < 604_800) return t.time.daysShort(Math.floor(seconds / 86_400));

  const date = new Date(then);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(t.locale, {
    day: 'numeric',
    month: 'short',
    ...(sameYear ? {} : { year: 'numeric' }),
  });
}
