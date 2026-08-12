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
