import type { ReactNode } from 'react';
import { View } from 'react-native';
import { cn } from './cn';
import { useHairline } from './hairline';

type RowGroupProps = {
  /** `SelectRow`s and `LinkRow`s, which round their own outer corners. */
  children: ReactNode;
  className?: string;
};

/**
 * The settings-style group: rows stacked into one panel with hairlines between them.
 *
 * Exists as a component rather than a copied `className` so the dark hairline is
 * declared once. It has to be a style rather than a `dark:` class — see `useHairline`
 * for what happens otherwise.
 */
export function RowGroup({ children, className }: RowGroupProps) {
  const hairline = useHairline();

  return (
    <View
      className={cn('overflow-hidden rounded-card bg-surface', className)}
      style={hairline}
    >
      {children}
    </View>
  );
}
