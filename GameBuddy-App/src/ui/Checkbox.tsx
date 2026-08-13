import { Check } from 'lucide-react-native';
import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { glow } from './glow';
import { Icon } from './Icon';

type CheckboxProps = {
  checked: boolean;
  onChange: (next: boolean) => void;
  /** The label. A node rather than a string so it can hold links. */
  children: ReactNode;
  accessibilityLabel: string;
};

/**
 * A square checkbox with a tappable label beside it.
 *
 * Square, where {@link SelectRow} is round, because the shape is the only thing telling
 * someone that this is a thing they choose rather than one of a set they pick from.
 *
 * It starts unchecked and nothing ticks it but a tap. That is the point of it: the terms
 * checkbox is the record that somebody agreed, and a box that arrives pre-ticked records
 * nothing at all.
 */
export function Checkbox({ checked, onChange, children, accessibilityLabel }: CheckboxProps) {
  const colors = useThemeColors();

  return (
    <View className="flex-row items-start gap-3">
      <Pressable
        onPress={() => onChange(!checked)}
        accessibilityRole="checkbox"
        accessibilityState={{ checked }}
        accessibilityLabel={accessibilityLabel}
        // Bigger than it looks: the box is 24pt but the touch target has to clear the
        // 44pt minimum, and the label alone is not a reliable place to aim.
        hitSlop={12}
        className={cn(
          'mt-0.5 h-6 w-6 items-center justify-center rounded-md border-2',
          checked ? 'border-primary bg-primary' : 'border-line',
        )}
        style={glow(checked ? 'soft' : 'none', colors.primary)}
      >
        {checked && <Icon as={Check} size={16} tone="inverse" strokeWidth={3} />}
      </Pressable>

      <View className="flex-1">{children}</View>
    </View>
  );
}
