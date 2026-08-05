import { useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
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
  const router = useRouter();

  return (
    <View className="flex-row items-center gap-3 pb-4 pt-2">
      <Pressable
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel="Back"
        hitSlop={12}
        className="h-10 w-10 items-center justify-center active:opacity-60"
      >
        <View className="h-2.5 w-2.5 rotate-45 border-b-2 border-l-2 border-content" />
      </Pressable>

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
