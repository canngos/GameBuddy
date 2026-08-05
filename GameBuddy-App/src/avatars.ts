/**
 * Turns the backend's `avatar` field into something an `<Image>` can load.
 *
 * The column holds whatever was inserted. The old Firebase-era rows were full URLs;
 * `db/seed-local.sql` writes bare filenames like `avatar-01.png`, because the images
 * have no home yet. Both shapes therefore have to be handled, and neither can be
 * assumed.
 *
 * **This is an open decision, not a finished feature.** Before launch the avatar art
 * needs to live somewhere — object storage with `EXPO_PUBLIC_AVATAR_BASE_URL` pointing
 * at it, or bundled in the app and mapped by filename. Until then a bare filename
 * resolves to nothing and callers fall back to initials, which is why
 * {@link avatarUri} can return null rather than a broken URL.
 */
const BASE = process.env.EXPO_PUBLIC_AVATAR_BASE_URL?.replace(/\/+$/, '');

export function avatarUri(avatar: string | null | undefined): string | null {
  if (!avatar) return null;
  if (/^https?:\/\//i.test(avatar)) return avatar;
  if (!BASE) return null;
  return `${BASE}/${avatar.replace(/^\/+/, '')}`;
}

/**
 * The fallback: up to two letters from the name.
 *
 * Grapheme-naive — it takes code points, so an emoji username gives one emoji rather
 * than half a surrogate pair, but a combining accent may be dropped.
 */
export function initialsOf(name: string | null | undefined): string {
  if (!name) return '?';
  const words = name.trim().split(/[\s_.-]+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return [...words[0]].slice(0, 2).join('').toUpperCase();
  return ([...words[0]][0] + [...words[1]][0]).toUpperCase();
}

/**
 * A stable colour per identity, so the same person is always the same colour.
 *
 * Saturation and lightness are fixed so white text stays legible on every hue.
 *
 * The hash is finished with an avalanche step rather than used raw. A plain
 * `hash * 31 + charCode` leaves *similar seeds adjacent*: the eight onboarding avatars
 * seeded "1".."8" hashed to 49..56 and therefore to hues 49..56 — eight swatches of the
 * same olive, which looked like a rendering fault rather than eight choices. Mixing the
 * bits means one step in the seed is an arbitrary jump in the output.
 */
export function avatarColor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (Math.imul(hash, 31) + seed.charCodeAt(i)) | 0;
  }
  hash = Math.imul(hash ^ (hash >>> 16), 0x45d9f3b);
  hash = Math.imul(hash ^ (hash >>> 16), 0x45d9f3b);
  hash = (hash ^ (hash >>> 16)) >>> 0;
  return `hsl(${hash % 360}, 55%, 55%)`;
}
