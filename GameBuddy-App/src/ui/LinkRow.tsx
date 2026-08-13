import { useRouter } from 'expo-router';
import { ChevronRight } from 'lucide-react-native';
import { Pressable, View } from 'react-native';
import { cn } from './cn';
import { Icon } from './Icon';
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

      {/* Was a 8×8 View rotated 45° with two borders. It read as a chevron at exactly one
          size and one weight, and it could not be given the stroke weight the rest of the
          icon set now uses. `BackHeader` had the mirrored version of the same trick. */}
      <Icon as={ChevronRight} size={20} tone="muted" />
    </Pressable>
  );
}
