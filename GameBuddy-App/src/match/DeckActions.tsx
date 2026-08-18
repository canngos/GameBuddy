import { Heart, RotateCcw, X } from 'lucide-react-native';
import type { ReactNode } from 'react';
import { Pressable, View, type ViewStyle } from 'react-native';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import { cn } from '../ui/cn';
import { lift } from '../ui/elevation';
import { GradientView } from '../ui/Gradient';
import { glow } from '../ui/glow';
import { commit, tapLight } from '../ui/haptics';
import { Icon } from '../ui/Icon';
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
  const t = useT();
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
        label={t.deck.actions.pass}
        hint={t.deck.actions.passHint}
        onPress={() => {
          tapLight();
          onDecide('decline');
        }}
        disabled={disabled}
        className="border-line bg-surface"
      >
        <Icon as={X} size={26} tone="muted" strokeWidth={2.5} />
      </CircleButton>

      <CircleButton
        label={t.deck.actions.match}
        hint={t.deck.actions.matchHint}
        // The one button in the app that gets `commit`. Saying yes is the decision that
        // costs an allowance and cannot be taken back without paying for a rewind, and a
        // pass is not — feedback of the same weight on both would flatten that difference.
        onPress={() => {
          commit();
          onDecide('accept');
        }}
        disabled={disabled}
        className="border-transparent"
        gradient
        size={72}
      >
        {/* The pink survives here and only here — this is the like/match colour's one job.
            Filled rather than outlined: white on the accent ramp measures 3.23:1, which
            WCAG allows for a graphical element and not for a label. A heart is legal on it.
            A word would not be. */}
        <Icon as={Heart} size={30} tone="inverse" fill="#FFFFFF" strokeWidth={0} />
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
  const t = useT();
  return (
    <Pressable
      onPress={() => {
        tapLight();
        onPress();
      }}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={cost > 0 ? t.deck.actions.undoCost(cost) : t.deck.actions.undo}
      accessibilityState={{ disabled }}
      hitSlop={8}
      className={cn(
        'h-14 w-14 items-center justify-center rounded-full border border-line bg-surface',
        disabled ? 'opacity-40' : 'active:opacity-70',
      )}
    >
      <Icon as={RotateCcw} size={20} tone="muted" />
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
  gradient = false,
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
  /** Fills with the accent ramp and lights it. The Match button, and nothing else. */
  gradient?: boolean;
  size?: number;
  children: React.ReactNode;
}) {
  const colors = useThemeColors();
  const lit = gradient && !disabled;

  return (
    <View className="items-center gap-2">
      <Pressable
        onPress={onPress}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityHint={hint}
        accessibilityState={{ disabled: !!disabled }}
        /*
         * Depth here, clip on the child below. On Android `overflow: hidden` clips a node's
         * own shadow and elevation, so a single node carrying both draws the gradient and
         * silently loses the light around it. Same split as `src/ui/Button.tsx`.
         *
         * Both depth calls always emit their keys — `'none'` rather than a dropped style —
         * because a style whose keys appear and disappear across a theme change stops the
         * subtree painting. See `src/ui/hairline.ts`.
         */
        style={[
          { width: size, height: size, borderRadius: size / 2 },
          lift(lit ? 'lg' : 'none', colors.accent),
          glow(lit ? 'strong' : 'none', colors.accent),
          style,
        ]}
        className={cn(
          'items-center justify-center border-2 active:scale-95',
          className,
          disabled && 'opacity-40',
        )}
      >
        {gradient && (
          <View
            className="absolute inset-0 overflow-hidden"
            style={{ borderRadius: size / 2 }}
            pointerEvents="none"
          >
            <GradientView name="accent" direction="diagonal" className="absolute inset-0" />
          </View>
        )}
        {children}
      </Pressable>
      <Text variant="caption">{label}</Text>
    </View>
  );
}
