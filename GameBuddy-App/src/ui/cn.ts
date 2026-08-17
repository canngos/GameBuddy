import clsx, { type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

/**
 * Merges class names so the *last* conflicting utility wins.
 *
 * NativeWind resolves conflicts by CSS specificity, not by position in the string, so
 * `"text-content text-brand"` does not reliably come out brand-coloured. That makes
 * every component prop of the form "here is a base style, override it from outside"
 * a coin toss. Collapsing the conflict before NativeWind ever sees it removes the
 * question: only one `text-*` colour survives, and it is the caller's.
 */
const twMerge = extendTailwindMerge({
  extend: {
    // Custom scales from tailwind.config.js. tailwind-merge cannot know these exist,
    // and without them it treats `rounded-card` and `rounded-field` as unrelated to
    // each other, leaving both in place.
    classGroups: {
      // All six must be in one group: they are mutually exclusive families, so a caller
      // passing `font-display-bold` has to displace the variant's `font-semibold`
      // rather than sit alongside it and lose the specificity race.
      'font-family': [
        {
          font: [
            'sans',
            'medium',
            'semibold',
            'bold',
            'display',
            'display-semibold',
            'display-bold',
          ],
        },
      ],
      rounded: [{ rounded: ['card', 'field'] }],
    },
  },
});

/**
 * Resolved class strings, keyed by the joined input.
 *
 * `twMerge` parses every utility in the string to find the conflicts, and this runs on
 * every render of every `Text` in the app — a component with sixty-eight importers, and one
 * that appears several times in every row of every list. The set of distinct class strings
 * an app produces is small and fixed, so almost every call after the first frame is a
 * repeat.
 *
 * Correctness is unaffected: `twMerge` is pure, so the same input always produced this
 * answer anyway. In particular the *set of class keys* that comes out is bit-for-bit what
 * it was, which is what UI_NOTE §4.2 cares about.
 *
 * The bound exists only so a caller that generates unbounded strings — an interpolated
 * colour, say — cannot turn this into a leak. Clearing wholesale rather than evicting one
 * entry keeps it to two lines; the cost is one cold frame roughly never.
 */
const cache = new Map<string, string>();
const MAX_ENTRIES = 500;

export function cn(...inputs: ClassValue[]): string {
  const raw = clsx(inputs);

  const hit = cache.get(raw);
  if (hit !== undefined) return hit;

  const merged = twMerge(raw);
  if (cache.size >= MAX_ENTRIES) cache.clear();
  cache.set(raw, merged);
  return merged;
}
