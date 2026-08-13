/**
 * What a gamer plays on.
 *
 * Mirrors the backend `Platform` enum, and the ids must stay identical to its constant
 * names — they are what goes over the wire, and the server refuses anything it does not
 * recognise rather than quietly ignoring it.
 *
 * Five families rather than individual consoles: nobody looking for someone to play with
 * cares whether the other person is on a PS4 or a PS5, they care whether they can join the
 * same lobby. Splitting generations would double the list and halve every match.
 *
 * The labels are duplicated here rather than fetched, because a picker that cannot render
 * until a request comes back is a picker that flashes empty. The server sends labels on
 * every profile it returns, so nothing downstream depends on this copy being authoritative
 * — it only has to name the same five things.
 */
export type PlatformId = 'PC' | 'PLAYSTATION' | 'XBOX' | 'SWITCH' | 'MOBILE';

export type PlatformOption = {
  id: PlatformId;
  label: string;
};

/**
 * In rough order of how many players each has, so common answers need no scrolling.
 *
 * No icon field. The glyphs live in {@link PlatformIcon} as drawn SVG rather than as emoji
 * or brand assets — see that file for why none of the five carries an official logo.
 */
export const PLATFORMS: PlatformOption[] = [
  { id: 'PC', label: 'PC' },
  { id: 'PLAYSTATION', label: 'PlayStation' },
  { id: 'XBOX', label: 'Xbox' },
  { id: 'SWITCH', label: 'Nintendo Switch' },
  { id: 'MOBILE', label: 'Mobile' },
];

/** One is a complete answer — most people do play on exactly one. */
export const MIN_PLATFORMS = 1;

export function platformLabel(id: string): string {
  return PLATFORMS.find((p) => p.id === id)?.label ?? id;
}

/**
 * The reverse lookup, for drawing a glyph next to a platform the server named.
 *
 * Accepts an id *or* a label, deliberately: the profile DTO sends enum names
 * (`"PLAYSTATION"`) and the match DTO sends labels (`"PlayStation"`) — see the two
 * `platforms` fields in `src/api/types.ts`, which disagree and say so. A lookup that
 * handled only one of them would silently drop every icon on whichever screen used the
 * other.
 *
 * Returns null for anything unrecognised rather than guessing, so a platform added to the
 * backend before this list shows as a plain label instead of the wrong picture.
 */
export function platformIdOf(value: string): PlatformId | null {
  const needle = value.trim().toLowerCase();
  const hit = PLATFORMS.find(
    (p) => p.id.toLowerCase() === needle || p.label.toLowerCase() === needle,
  );
  return hit?.id ?? null;
}
