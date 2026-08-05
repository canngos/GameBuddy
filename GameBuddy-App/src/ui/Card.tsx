import type { ReactNode } from 'react';
import { View } from 'react-native';
import { useIsDark } from '../theme';
import { cn } from './cn';
import { lift } from './elevation';
import { useHairline } from './hairline';

type CardProps = {
  children: ReactNode;
  className?: string;
};

/**
 * A raised panel.
 *
 * In light mode depth comes from a shadow; in dark mode shadows are close to invisible
 * against a near-black canvas, so the same lift is carried by a lighter surface plus a
 * hairline border. Both are declared here so no screen has to think about it — and both
 * as styles rather than classes, because a `dark:` variant does not survive a theme
 * change at runtime. See `useHairline`.
 */
export function Card({ children, className }: CardProps) {
  const isDark = useIsDark();
  const hairline = useHairline();

  return (
    <View
      className={cn('rounded-card bg-surface p-5', className)}
      // Both halves are always present — a flat lift in dark, a transparent border in
      // light. Swapping one for the other instead is what makes the card go blank on a
      // theme change; `useHairline` explains why.
      style={[lift(isDark ? 'none' : 'sm'), hairline]}
    >
      {children}
    </View>
  );
}
