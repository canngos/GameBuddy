import { Check } from 'lucide-react-native';
import { Pressable, View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { glow } from './glow';
import { Icon } from './Icon';
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
 * The tick used to be a drawn ring with a dot in it, because there was no icon set and a
 * bare Unicode checkmark renders differently on every platform. There is one now, so it is
 * an actual check — but the surrounding ring stays: it is what gives the unselected state
 * something to be, and a row whose only selected-state signal is a mark appearing is much
 * harder to scan down a list.
 */
export function SelectRow({
  label,
  hint,
  selected,
  onPress,
  position = 'single',
}: SelectRowProps) {
  const colors = useThemeColors();

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

      {/* Conditional *values* inside a class list whose keys never change — which is the
          safe shape. See useHairline for the shape that is not. */}
      <View
        className={cn(
          'h-6 w-6 items-center justify-center rounded-full border-2',
          selected ? 'border-primary bg-primary' : 'border-line',
        )}
        style={glow(selected ? 'soft' : 'none', colors.primary)}
      >
        {selected && <Icon as={Check} size={14} tone="inverse" strokeWidth={3} />}
      </View>
    </Pressable>
  );
}
