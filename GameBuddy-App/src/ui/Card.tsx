import { useMemo, type ReactNode } from 'react';
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

  // Both halves are always present — a flat lift in dark, a transparent border in light.
  // Swapping one for the other instead is what makes the card go blank on a theme change;
  // `useHairline` explains why.
  //
  // Memoised because the array is a prop: `lift` and `useHairline` now return cached
  // objects, but a fresh array around them would still hand `View` a new `style` on every
  // render, and `Card` is the row container on most of the list screens in the app.
  //
  // No `memo()` on the component itself — `children` is a fresh element on every parent
  // render, so it could never bail out until the row above it is memoised too. That comes
  // with the list work.
  const style = useMemo(() => [lift(isDark ? 'none' : 'sm'), hairline], [isDark, hairline]);

  return (
    <View className={cn('rounded-card bg-surface p-5', className)} style={style}>
      {children}
    </View>
  );
}
