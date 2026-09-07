import type { ReactNode } from 'react';
import { View } from 'react-native';
import { BackButton } from './BackButton';
import { Text } from './Text';

type BackHeaderProps = {
  title: string;
  subtitle?: string;
  /** Sits at the trailing edge: an owner menu, a member count, an action. */
  right?: ReactNode;
};

/**
 * A back chevron, a title, and room for one action.
 *
 * The community screens are a stack inside a tab, so the tab bar stays put and this is
 * the only way back up. It is a plain row rather than a native header because the rest
 * of the app draws its own chrome and a stack header would be the one inconsistent bar.
 */
export function BackHeader({ title, subtitle, right }: BackHeaderProps) {
  return (
    <View className="flex-row items-center gap-3 pb-4 pt-2">
      {/* The chevron itself lives in `BackButton`, because screens that draw their own
          header need the same affordance without a title. Was a rotated two-border View —
          the mirror of the trick in `LinkRow`. */}
      <BackButton />

      <View className="flex-1">
        <Text variant="bodyStrong" numberOfLines={1}>
          {title}
        </Text>
        {!!subtitle && (
          <Text variant="caption" numberOfLines={1}>
            {subtitle}
          </Text>
        )}
      </View>

      {right}
    </View>
  );
}
