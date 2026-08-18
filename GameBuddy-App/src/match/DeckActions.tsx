import { Heart, RotateCcw, Star, X } from 'lucide-react-native';
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
  /** Super Likes in hand. The button is not offered at zero — see below. */
  superLikes?: number;
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
  superLikes = 0,
}: DeckActionsProps) {
  const t = useT();
  const colors = useThemeColors();
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

      {/* Where the retired deck boost used to sit — the row read undo · pass · match ·
          boost. A Super Like belongs in that slot far better than a spacer did: it is a
          third answer to the same question the other two buttons ask, and until now it was
          a thing you could buy and then never use. Sized between Pass and Match, because
          it is a stronger yes than a like and a rarer one than either.

          Hidden entirely at zero rather than shown disabled. A greyed-out button on the
          main screen is a permanent advertisement for something you do not have, and the
          swipe still offers the same purchase prompt for anyone who wants one. */}
      {superLikes > 0 ? (
        <CircleButton
          label={t.deck.actions.superLike}
          hint={t.deck.actions.superLikeHint}
          caption={t.deck.actions.superLikeLeft(superLikes)}
          onPress={() => {
            commit();
            onDecide('super');
          }}
          disabled={disabled}
          className="border-gold/60 bg-surface"
        >
          <Icon as={Star} size={26} tone="gold" fill={colors.gold} strokeWidth={0} />
        </CircleButton>
      ) : (
        <View className="w-14" />
      )}
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
  caption,
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
  /** Replaces the label under the button. The label still goes to assistive technology. */
  caption?: string;
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
      {/* The caption is what the eye needs — a count is more use than a name it can already
          read from the icon — while `accessibilityLabel` above keeps saying what the button
          is, which is what a screen reader needs. */}
      <Text variant="caption">{caption ?? label}</Text>
    </View>
  );
}
