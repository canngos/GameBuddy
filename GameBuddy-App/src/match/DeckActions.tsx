import type { ReactNode } from 'react';
import { Pressable, View, type ViewStyle } from 'react-native';
import { brand } from '../theme';
import { cn } from '../ui/cn';
import { lift } from '../ui/elevation';
import { Text } from '../ui/Text';
import type { Decision } from './useDeck';

type DeckActionsProps = {
  onDecide: (decision: Decision) => void;
  disabled?: boolean;
  /** Omitted where there is nothing to undo, which hides the control entirely. */
  onRewind?: () => void;
  canRewind?: boolean;
  /** Coins a rewind costs here. Zero on Gold, and shown so the price is never a surprise. */
  rewindCost?: number;
  /**
   * The Boost control, rendered on the right so the row reads undo · pass · match · boost.
   *
   * Passed in rather than built here because Boost owns its own queries and countdown,
   * and this component is otherwise presentational. It sits in this row rather than the
   * header because the header already carried filters, admirers and the like count — a
   * fourth control there wrapped the screen title onto two lines.
   */
  boost?: ReactNode;
};

/**
 * The button equivalents of the two swipes.
 *
 * Not decoration. A swipe deck that can only be driven by dragging is unusable with a
 * screen reader or a switch control, and is awkward one-handed on a large phone — so
 * these are a real second input path, labelled for assistive technology, not a hint
 * that gestures exist.
 */
export function DeckActions({
  onDecide,
  disabled = false,
  onRewind,
  canRewind = false,
  rewindCost = 0,
  boost,
}: DeckActionsProps) {
  return (
    <View className="flex-row items-center justify-center gap-6 py-5">
      {/* Left of Pass, and smaller than both. Undo is a recovery action, not a third
          choice — sizing it like one would invite taps from people who meant to pass.
          Rendered as a spacer when there is nothing to undo, so Pass and Match do not
          jump sideways the moment the first swipe happens. */}
      {onRewind ? (
        <RewindButton onPress={onRewind} disabled={disabled || !canRewind} cost={rewindCost} />
      ) : (
        <View className="w-14" />
      )}

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

      {/* Mirrors the rewind spacer, so Pass and Match stay centred whether or not
          either optional control is present. */}
      {boost ?? <View className="w-14" />}
    </View>
  );
}

/**
 * Undo the last swipe.
 *
 * The price is on the button. A control that silently spends coins is the kind of thing
 * people only discover from their balance afterwards, and "50" next to an arrow is enough
 * to make the trade explicit without a confirmation step that would defeat the purpose —
 * the whole value of a rewind is that it is faster than the regret.
 */
function RewindButton({
  onPress,
  disabled,
  cost,
}: {
  onPress: () => void;
  disabled: boolean;
  cost: number;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={cost > 0 ? `Undo last swipe, ${cost} coins` : 'Undo last swipe'}
      accessibilityState={{ disabled }}
      hitSlop={8}
      className={cn(
        'h-14 w-14 items-center justify-center rounded-full border border-line bg-surface',
        disabled ? 'opacity-40' : 'active:opacity-70',
      )}
    >
      {/* A counter-clockwise arrow, drawn rather than iconed: no icon font is bundled. */}
      <Text className="text-[20px] leading-[24px] text-muted">↺</Text>
      {cost > 0 && (
        <Text className="font-semibold text-[10px] leading-[12px] text-muted">{cost}</Text>
      )}
    </Pressable>
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
