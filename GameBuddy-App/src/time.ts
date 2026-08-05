/**
 * "3h", "2d" — the compact age of something, for a timestamp sitting beside a name.
 *
 * Deliberately not a library. `Intl.RelativeTimeFormat` exists on Hermes but produces
 * "3 hours ago", which is three times the width for the same information in a row that
 * is already tight, and every date library worth using costs more than eleven lines.
 *
 * Anything older than a week gets a date instead: "how long ago" stops being the useful
 * framing once the answer is in weeks.
 */
export function shortAgo(iso: string | null | undefined): string {
  if (!iso) return '';

  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return 'now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h`;
  if (seconds < 604_800) return `${Math.floor(seconds / 86_400)}d`;

  const date = new Date(then);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    ...(sameYear ? {} : { year: 'numeric' }),
  });
}
