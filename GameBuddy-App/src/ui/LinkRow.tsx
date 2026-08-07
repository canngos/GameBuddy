import { useRouter } from 'expo-router';
import { Pressable, View } from 'react-native';
import { cn } from './cn';
import { Text } from './Text';

type LinkRowProps = {
  label: string;
  hint?: string;
  /** Where tapping goes. Omit when supplying {@link onPress} instead. */
  href?: string;
  /**
   * Handles the tap itself, for rows that leave the app — the terms and privacy policy
   * open in a browser rather than routing anywhere.
   */
  onPress?: () => void;
  /** Rounds only the outer corners when rows are stacked into one group. */
  position?: 'single' | 'first' | 'middle' | 'last';
};

/**
 * A settings row that opens another screen.
 *
 * The navigating sibling of {@link SelectRow}, sharing its metrics so a group can mix
 * the two without the rows disagreeing about their height or their hairlines.
 */
export function LinkRow({ label, hint, href, onPress, position = 'single' }: LinkRowProps) {
  const router = useRouter();

  return (
    <Pressable
      onPress={() => (onPress ? onPress() : href && router.push(href as never))}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      className={cn(
        'min-h-touch flex-row items-center justify-between bg-surface px-4 py-3 active:bg-raised',
        position !== 'last' && position !== 'single' && 'border-b border-line',
        position === 'first' && 'rounded-t-card',
        position === 'last' && 'rounded-b-card',
        position === 'single' && 'rounded-card',
      )}
    >
      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{label}</Text>
        {hint && <Text variant="caption">{hint}</Text>}
      </View>

      {/* A forward chevron from two borders: top+right rotated gives a corner pointing
          right. Bottom+left would point down, which is a dropdown, not a "go". */}
      <View className="h-2 w-2 rotate-45 border-r-2 border-t-2 border-muted" />
    </Pressable>
  );
}
