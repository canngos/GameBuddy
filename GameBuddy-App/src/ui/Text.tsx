import { Text as RNText, type TextProps as RNTextProps } from 'react-native';
import { cn } from './cn';

/**
 * The type scale. Every string in the app renders through one of these, so nothing
 * falls back to the system font by accident — on Android that is the difference
 * between Poppins and Roboto, easy to miss until the screenshots are side by side.
 */
const variants = {
  display: 'font-bold text-[34px] leading-[42px] text-content',
  title: 'font-semibold text-[26px] leading-[34px] text-content',
  heading: 'font-semibold text-[19px] leading-[26px] text-content',
  body: 'font-sans text-[15px] leading-[22px] text-content',
  bodyStrong: 'font-medium text-[15px] leading-[22px] text-content',
  label: 'font-medium text-[13px] leading-[18px] text-content',
  caption: 'font-sans text-[12px] leading-[17px] text-muted',
  button: 'font-semibold text-[16px] leading-[22px] text-content',
  /** Small, wide, upper-case. Section headers and step counters. */
  overline: 'font-semibold text-[11px] leading-[16px] tracking-[1.5px] text-muted',
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
