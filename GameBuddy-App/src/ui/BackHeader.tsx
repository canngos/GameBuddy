import { useRouter } from 'expo-router';
import { ChevronLeft } from 'lucide-react-native';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { useT } from '../i18n/useT';
import { Icon } from './Icon';
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
  const t = useT();

  return (
    <View className="flex-row items-center gap-3 pb-4 pt-2">
      <Pressable
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel={t.common.back}
        hitSlop={12}
        className="h-10 w-10 items-center justify-center active:opacity-60"
      >
        {/* Was a rotated two-border View — the mirror of the trick in `LinkRow`. Both
            were replaced together so the two chevrons cannot drift apart. */}
        <Icon as={ChevronLeft} size={24} tone="content" />
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
