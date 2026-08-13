import { Text as RNText, type TextProps as RNTextProps } from 'react-native';
import { cn } from './cn';

/**
 * The type scale. Every string in the app renders through one of these, so nothing
 * falls back to the system font by accident — on Android that is the difference
 * between Poppins and Roboto, easy to miss until the screenshots are side by side.
 *
 * Two families. Chakra Petch on `hero`, `display`, `title`, `numeral` and `overline`;
 * Poppins on everything you actually read. See `src/theme/typography.ts` for why.
 *
 * **The sizes and line-heights here are deliberately unchanged from the previous scale.**
 * `Text` has 68 importers, and `leading-*` is what decides where every list row breaks
 * and where every `numberOfLines={1}` truncates — so moving the metrics reflows the whole
 * app and belongs in its own commit with its own screen-by-screen pass. This change is
 * family and colour only. `hero` and `numeral` are new, so they have no old value to
 * preserve.
 */
const variants = {
  /** The largest thing on a screen. Welcome, and the match moment. */
  hero: 'font-display-bold text-[40px] leading-[48px] text-content',
  display: 'font-display-bold text-[34px] leading-[42px] text-content',
  title: 'font-display-semibold text-[26px] leading-[34px] text-content',
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

type TextProps = RNTextProps & {
  variant?: TextVariant;
  className?: string;
};

export function Text({ variant = 'body', className, ...rest }: TextProps) {
  // Through cn, so a caller passing `text-brand` actually overrides the variant's
  // `text-content` rather than racing it on specificity.
  return <RNText className={cn(variants[variant], className)} {...rest} />;
}
