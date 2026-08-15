/**
 * "in 2h", "Tonight 20:00 GMT+3", "Started 1h ago" — when a lobby plans to play, phrased
 * for a card. The counterpart of `src/time.ts`'s `shortAgo`, which looks backwards; this
 * looks both ways because a lobby's planned start can be either side of now.
 *
 * **Everything here renders in the reader's own time zone, and says so.** The instant is
 * absolute — the API carries ISO instants and Postgres stores `timestamptz` — so a lobby
 * opened at 20:00 in Helsinki is 19:00 to somebody in Berlin, correctly, without anybody
 * converting anything. What was missing was the label: an unlabelled "20:00" leaves the
 * reader guessing whether it is their evening or the owner's, and that ambiguity is
 * exactly how somebody misses the game they were accepted into.
 */

/** A rough, compact distance — no zone needed, because "in 2h" means the same everywhere. */
export function startsLabel(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const deltaSeconds = Math.round((then - Date.now()) / 1000);
  const magnitude = Math.abs(deltaSeconds);

  if (magnitude < 15 * 60) return 'now';

  const phrase =
    magnitude < 3600
      ? `${Math.floor(magnitude / 60)}m`
      : magnitude < 86_400
        ? `${Math.floor(magnitude / 3600)}h`
        : `${Math.floor(magnitude / 86_400)}d`;

  if (deltaSeconds > 0) return `in ${phrase}`;
  return `${phrase} ago`;
}

/**
 * "Fri 20:00 GMT+3" — the absolute time in the reader's zone, for the detail header
 * where precision matters and a wrong hour costs somebody the session.
 */
export function startsExact(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString(undefined, {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  });
}

/** "Sun 24 Aug, 20:00 GMT+3" — a full stamp, for confirming a time being entered. */
export function startsFull(date: Date): string {
  return date.toLocaleString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  });
}

/**
 * The reader's IANA zone ("Europe/Helsinki"), or null where the runtime cannot say.
 *
 * Used to name the zone a time is being *entered* in. Everywhere a time is displayed the
 * short offset from the formatters above is enough, but somebody typing "20:00" deserves
 * to be told, in words, whose eight o'clock they just picked.
 */
export function localZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? null;
  } catch {
    return null;
  }
}
