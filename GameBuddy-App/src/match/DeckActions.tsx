import { Pressable, View, type ViewStyle } from 'react-native';
import { brand } from '../theme';
import { cn } from '../ui/cn';
import { lift } from '../ui/elevation';
import { Text } from '../ui/Text';
import type { Decision } from './useDeck';

type DeckActionsProps = {
  onDecide: (decision: Decision) => void;
  disabled?: boolean;
};

/**
 * The button equivalents of the two swipes.
 *
 * Not decoration. A swipe deck that can only be driven by dragging is unusable with a
 * screen reader or a switch control, and is awkward one-handed on a large phone — so
 * these are a real second input path, labelled for assistive technology, not a hint
 * that gestures exist.
 */
export function DeckActions({ onDecide, disabled = false }: DeckActionsProps) {
  return (
    <View className="flex-row items-center justify-center gap-8 py-5">
      <CircleButton
        label="Pass"
        hint="Skip this gamer"
        onPress={() => onDecide('decline')}
        disabled={disabled}
        className="border-line bg-surface"
      >
        {/* An X drawn from two bars: no icon font is bundled. */}
        <View className="h-6 w-6 items-center justify-center">
          <View className="absolute h-0.5 w-6 rotate-45 rounded-full bg-muted" />
          <View className="absolute h-0.5 w-6 -rotate-45 rounded-full bg-muted" />
        </View>
      </CircleButton>

      <CircleButton
        label="Match"
        hint="Say yes to this gamer"
        onPress={() => onDecide('accept')}
        disabled={disabled}
        className="border-brand bg-brand"
        style={lift('lg', brand.DEFAULT)}
        size={72}
      >
        {/* A heart is hard to draw without an icon; a filled ring reads as "yes". */}
        <View className="h-7 w-7 items-center justify-center rounded-full border-[3px] border-white">
          <View className="h-2.5 w-2.5 rounded-full bg-white" />
        </View>
      </CircleButton>
    </View>
  );
}

function CircleButton({
  label,
  hint,
  onPress,
  disabled,
  className,
  style,
  size = 60,
  children,
}: {
  label: string;
  hint: string;
  onPress: () => void;
  disabled?: boolean;
  className?: string;
  /** Depth, which has to be a style rather than a class. See src/ui/elevation.ts. */
  style?: ViewStyle;
  size?: number;
  children: React.ReactNode;
}) {
  return (
    <View className="items-center gap-2">
      <Pressable
        onPress={onPress}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint={hint}
        accessibilityState={{ disabled: !!disabled }}
        style={[{ width: size, height: size, borderRadius: size / 2 }, style]}
        className={cn(
          'items-center justify-center border-2 active:scale-95',
          className,
          disabled && 'opacity-40',
        )}
      >
        {children}
      </Pressable>
      <Text variant="caption">{label}</Text>
    </View>
  );
}
