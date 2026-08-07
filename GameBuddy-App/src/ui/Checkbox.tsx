import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { cn } from './cn';

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
          checked ? 'border-brand bg-brand' : 'border-line',
        )}
      >
        {checked && <View className="h-2.5 w-2.5 rounded-sm bg-white" />}
      </Pressable>

      <View className="flex-1">{children}</View>
    </View>
  );
}
