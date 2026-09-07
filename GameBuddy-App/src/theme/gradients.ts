import { useColorScheme } from 'nativewind';

/**
 * Gradients, which live in JS and nowhere else.
 *
 * Two independent reasons they cannot be Tailwind tokens, either of which would be
 * enough on its own:
 *
 * 1. A Tailwind colour token has to resolve to a single colour so `<alpha-value>` can be
 *    interpolated into it — that is the whole mechanism `bg-surface/60` relies on. A
 *    two-stop gradient has no such form.
 * 2. NativeWind 4 does not implement `bg-gradient-to-*` / `from-*` / `to-*` on native at
 *    all. They compile to nothing, so the utility path is closed regardless.
 *
 * So gradients are consumed only by `src/ui/Gradient.tsx`, never by a class. Where a
 * gradient would be overkill — a focus ring, a hairline, an `ActivityIndicator` — use the
 * solid `primary` token from `colors.ts` instead.
 */

/**
 * Stops are a tuple, not an array: `expo-linear-gradient`'s `colors` prop requires at
 * least two entries and a plain `string[]` will not typecheck against it.
 */
export type GradientStops = readonly [string, string, ...string[]];

/*
 * There are two violet gradients, not one, and the split is forced by contrast.
 *
 * The expressive one runs violet into cyan, which is the whole look. But white on
 * `#00E5FF` measures **1.54:1** — a white button label sitting over the cyan end would be
 * effectively invisible, and no amount of shadow or weight fixes a ratio that low. The
 * usual dodges do not survive contact: a dark label fails at the violet end instead, and
 * angling the gradient so the text avoids the cyan only works until someone translates the
 * label into a longer language.
 *
 * So `primary` stays expressive and is for *surfaces* — hero panels, the match moment, the
 * paywall header, anything large that does not carry small white text. `action` is the one
 * that goes under labels, and every stop in it clears 4.5:1 against white. It gives up the
 * cyan and keeps the violet, which is the half that carries the identity anyway.
 *
 * If you find yourself putting a white label on `primary`, that is the bug.
 */
const gradients = {
  /** Expressive. Surfaces only — see above. */
  primary: {
    light: ['#6D3AF0', '#00A0C4'] as GradientStops,
    dark: ['#7C4DFF', '#00E5FF'] as GradientStops,
  },

  /** Button-safe. White stays legible across the whole ramp. */
  action: {
    light: ['#6D3AF0', '#1163B5'] as GradientStops,
    dark: ['#7C4DFF', '#2E5BE0'] as GradientStops,
  },

  /**
   * Like, match, admirers. The old brand pink, now doing one job.
   *
   * White on the dark pink is 3.23:1 — enough for an *icon* (WCAG treats graphical
   * elements as 3:1) and not enough for a label. The match button carries a heart, not
   * a word, which is what makes this legal. Put text on it and it stops being.
   */
  accent: {
    light: ['#D42540', '#8E2FCC'] as GradientStops,
    dark: ['#FF4D67', '#C04BFF'] as GradientStops,
  },

  /** Coins, Gold membership, badge tiers. */
  gold: {
    light: ['#8A5C00', '#6B3F00'] as GradientStops,
    dark: ['#FFC53D', '#FF8A3D'] as GradientStops,
  },
} as const;

export type GradientName = keyof typeof gradients;

/** The stops for whichever theme is on screen. */
export function useGradient(name: GradientName): GradientStops {
  const { colorScheme } = useColorScheme();
  return gradients[name][colorScheme === 'dark' ? 'dark' : 'light'];
}

/*
 * Per-identity gradients — the monogram behind someone with no photo — are *not* here.
 * They live in `src/avatars.ts` as `avatarGradient`, beside the hash they share with
 * `avatarColor`, because the one thing that must not happen is the same person being two
 * different colours on two different screens.
 */
