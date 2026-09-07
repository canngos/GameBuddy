import { Text as RNText, type TextProps as RNTextProps, type TextStyle } from 'react-native';
import { cn } from './cn';
import { useScreenScale } from './useScreenScale';

/**
 * The type scale. Every string in the app renders through one of these, so nothing
 * falls back to the system font by accident — on Android that is the difference
 * between Poppins and Roboto, easy to miss until the screenshots are side by side.
 *
 * Two families. Chakra Petch on `hero`, `display`, `title`, `numeral` and `overline`;
 * Poppins on everything you actually read. See `src/theme/typography.ts` for why.
 *
 * **Everything you actually read is a fixed size, on every device.** `body`, `bodyStrong`,
 * `label`, `caption`, `button` and `overline` keep their metrics exactly — `Text` has 68
 * importers and `leading-*` is what decides where every list row breaks and where every
 * `numberOfLines={1}` truncates, so moving those reflows the whole app. It is also the wrong
 * thing to do: shrinking body text on a small phone is the one change that makes a screen
 * harder to use rather than easier, and the OS text-size setting is a preference to honour.
 *
 * **The three display variants do scale** — see {@link DISPLAY_SIZES}. They are the largest
 * single vertical consumers in the app (a 40px `hero` heads Welcome, Gold and the match
 * moment), they head screens rather than list rows, and nothing measures against them, so
 * stepping them down buys 40–80dp on the screens that need it without moving a single
 * truncation point.
 */
const variants = {
  /**
   * The largest thing on a screen. Welcome, and the match moment.
   *
   * These three carry no `text-`/`leading-` class: their metrics come from
   * {@link DISPLAY_SIZES} as an inline style, because the value has to change with the
   * device and a class cannot. Everything else about them — family, colour — stays here.
   */
  hero: 'font-display-bold text-content',
  display: 'font-display-bold text-content',
  title: 'font-display-semibold text-content',
  heading: 'font-semibold text-[19px] leading-[26px] text-content',
  body: 'font-sans text-[15px] leading-[22px] text-content',
  bodyStrong: 'font-medium text-[15px] leading-[22px] text-content',
  label: 'font-medium text-[13px] leading-[18px] text-content',
  caption: 'font-sans text-[12px] leading-[17px] text-muted',
  button: 'font-semibold text-[16px] leading-[22px] text-content',
  /**
   * Counts, balances, stat tiles. Anything whose value changes while it is on screen.
   *
   * Size is left to the caller because a coin balance in a header and a stat tile on the
   * profile are the same *kind* of thing at different scales — this variant is about the
   * face and the figures, not the size.
   */
  numeral: 'font-display-bold text-content',
  /** Small, wide, upper-case. Section headers and step counters. */
  overline: 'font-display-semibold text-[11px] leading-[16px] tracking-[2px] text-muted',
} as const;

export type TextVariant = keyof typeof variants;

/**
 * The display scale, per tier: tight, compact, roomy.
 *
 * Line height moves with the size rather than staying put, so a heading that wraps to two
 * lines on a small phone tightens with it instead of leaving a gap where the old leading was.
 */
const DISPLAY_SIZES: Record<
  'hero' | 'display' | 'title',
  readonly [TextStyle, TextStyle, TextStyle]
> = {
  hero: [
    { fontSize: 30, lineHeight: 36 },
    { fontSize: 34, lineHeight: 41 },
    { fontSize: 40, lineHeight: 48 },
  ] as const,
  display: [
    { fontSize: 26, lineHeight: 32 },
    { fontSize: 30, lineHeight: 37 },
    { fontSize: 34, lineHeight: 42 },
  ] as const,
  title: [
    { fontSize: 21, lineHeight: 28 },
    { fontSize: 23, lineHeight: 30 },
    { fontSize: 26, lineHeight: 34 },
  ],
} as const;

type DisplayVariant = keyof typeof DISPLAY_SIZES;

function isDisplay(variant: TextVariant): variant is DisplayVariant {
  return variant in DISPLAY_SIZES;
}

type TextProps = RNTextProps & {
  variant?: TextVariant;
  className?: string;
};

export function Text({ variant = 'body', className, style, ...rest }: TextProps) {
  const { pick } = useScreenScale();

  /*
   * A style for the three display variants, `undefined` for the rest.
   *
   * Undefined rather than an empty object so the non-scaling variants — which is nearly every
   * `Text` in the app — pass exactly what they passed before and cost nothing extra. A call
   * site's `variant` is a literal in the JSX, so this branch does not flip between renders and
   * the style key set stays stable, which is what UI_NOTE §4.2 cares about.
   */
  const displaySize = isDisplay(variant) ? pick(...DISPLAY_SIZES[variant]) : undefined;
  // Through cn, so a caller passing `text-brand` actually overrides the variant's
  // `text-content` rather than racing it on specificity.
  //
  // Skipped entirely when there is nothing to override, which is most call sites: the
  // variant strings are already conflict-free, so merging one with nothing can only
  // return it unchanged. Worth the branch because this is the most-rendered component in
  // the app.
  return (
    <RNText
      className={className == null ? variants[variant] : cn(variants[variant], className)}
      // The variant's own metrics first, so a caller's `style` still wins — the same
      // precedence `cn` gives a caller's className over the variant's classes.
      style={displaySize == null ? style : [displaySize, style]}
      {...rest}
    />
  );
}
