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

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
