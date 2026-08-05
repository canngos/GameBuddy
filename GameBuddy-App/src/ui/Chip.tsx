import { Pressable } from 'react-native';
import { brand } from '../theme';
import { cn } from './cn';
import { lift } from './elevation';
import { Text } from './Text';

type ChipProps = {
  label: string;
  selected: boolean;
  onPress: () => void;
  disabled?: boolean;
};

/** Multi-select pill, used for picking games and keywords during onboarding. */
export function Chip({ label, selected, onPress, disabled }: ChipProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected, disabled: !!disabled }}
      className={cn(
        'rounded-full border-2 px-4 py-2.5 active:opacity-80',
        selected ? 'border-brand bg-brand' : 'border-line bg-raised',
        // Dimmed only when it cannot be chosen. An already-selected chip stays
        // pressable at the cap so the user can always deselect.
        disabled && !selected && 'opacity-40',
      )}
      // See src/ui/elevation.ts — a `shadow-*` class here crashed on Android.
      style={lift(selected ? 'md' : 'none', brand.DEFAULT)}
    >
      <Text
        variant={selected ? 'bodyStrong' : 'body'}
        className={selected ? 'text-white' : 'text-content'}
      >
        {label}
      </Text>
    </Pressable>
  );
}
