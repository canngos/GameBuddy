import { Pressable, View } from 'react-native';
import { cn } from './cn';
import { Text } from './Text';

type SelectRowProps = {
  label: string;
  hint?: string;
  selected: boolean;
  onPress: () => void;
  /** Rounds only the outer corners when rows are stacked into one group. */
  position?: 'single' | 'first' | 'middle' | 'last';
};

/**
 * A settings-style radio row. Used for the theme picker.
 *
 * The tick is a drawn ring rather than an icon glyph — the app ships no icon font yet,
 * and a bare Unicode checkmark renders differently on every platform.
 */
export function SelectRow({
  label,
  hint,
  selected,
  onPress,
  position = 'single',
}: SelectRowProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
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

      <View
        className={cn(
          'h-6 w-6 items-center justify-center rounded-full border-2',
          selected ? 'border-brand bg-brand' : 'border-line',
        )}
      >
        {selected && <View className="h-2 w-2 rounded-full bg-white" />}
      </View>
    </Pressable>
  );
}
