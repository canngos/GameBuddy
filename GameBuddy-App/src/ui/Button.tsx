import { ActivityIndicator, Pressable, type PressableProps, View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { lift } from './elevation';
import { GradientView } from './Gradient';
import { glow } from './glow';
import { tapLight } from './haptics';
import { Text } from './Text';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'md' | 'lg';

type ButtonProps = Omit<PressableProps, 'style' | 'children'> & {
  label: string;
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  className?: string;
};

/*
 * `primary` is a gradient; the other three are not, and that is the hierarchy. A screen
 * with two gradient buttons on it has no primary action.
 *
 * The gradient used is `action`, never `primary` — `action` is the ramp whose every stop
 * clears 4.5:1 against white, so the label stays legible from end to end. See the comment
 * above the gradient table in `src/theme/gradients.ts` for why there are two.
 */
const containers: Record<Variant, string> = {
  primary: '',
  secondary: 'border-2 border-primary',
  ghost: '',
  danger: 'border-2 border-danger',
};

/*
 * The press feedback is a *separate* table, and it goes on the root `Pressable` — never on
 * the inner clipping node. That is not tidiness; putting it inside is a silent, total
 * failure.
 *
 * NativeWind implements `active:` by giving the element its own press handling. On a plain
 * `View` nested inside a `Pressable`, that View becomes the touch target and swallows the
 * gesture, so the `Pressable`'s `onPress` never fires at all. It does not misbehave — it
 * goes completely dead, while still looking and measuring like a working button.
 *
 * This shipped for a few hours and took out `ghost`, `secondary` and `danger` app-wide,
 * which meant every Cancel and every Back. `primary` was unaffected purely because it has
 * no `active:` class, so Save worked and made it look like a navigation bug. If you ever
 * add a `hover:`/`active:`/`focus:` class to the inner node, this comes straight back.
 */
const pressedStates: Record<Variant, string> = {
  primary: 'active:opacity-90',
  secondary: 'active:bg-primary/10',
  ghost: 'active:bg-raised',
  danger: 'active:bg-danger/10',
};

const labels: Record<Variant, string> = {
  primary: 'text-white',
  secondary: 'text-primary',
  ghost: 'text-muted',
  danger: 'text-danger',
};

const sizes: Record<Size, string> = {
  md: 'min-h-[44px] px-5',
  lg: 'min-h-touch px-6',
};

export function Button({
  label,
  variant = 'primary',
  size = 'lg',
  loading = false,
  disabled,
  className,
  onPress,
  ...rest
}: ButtonProps) {
  const colors = useThemeColors();
  // A loading button is disabled too. Otherwise a double tap during a slow request
  // registers twice, and on registration that is two verification emails.
  const inert = disabled || loading;
  const isPrimary = variant === 'primary';

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inert, busy: loading }}
      disabled={inert}
      onPress={(event) => {
        // Before the handler, not after: the handler may navigate, and feedback that
        // lands after the screen has changed feels like it belongs to the new screen.
        tapLight();
        onPress?.(event);
      }}
      // `inert` last of the two so a disabled button cannot also dim on press.
      className={cn('rounded-full', !inert && pressedStates[variant], inert && 'opacity-40', className)}
      /*
       * Depth lives on this node and the gradient's clip lives on the one below, which is
       * not a stylistic choice: on Android `overflow: hidden` clips the node's own shadow
       * and elevation, so a single node carrying both draws the gradient correctly and
       * silently loses the glow. Splitting them is the only arrangement that works on both
       * platforms.
       *
       * Both calls always emit their keys — `'none'` rather than a dropped style — because
       * a style whose keys come and go across a theme change stops the subtree painting.
       * See `src/ui/hairline.ts`.
       */
      style={[
        lift(isPrimary && !inert ? 'lg' : 'none', colors.primary),
        glow(isPrimary && !inert ? 'soft' : 'none', colors.primary),
      ]}
      {...rest}
    >
      <View className={cn('overflow-hidden rounded-full', containers[variant])}>
        {isPrimary && (
          <GradientView
            name="action"
            direction="horizontal"
            className="absolute inset-0"
            pointerEvents="none"
          />
        )}

        <View className={cn('flex-row items-center justify-center', sizes[size])}>
          {/* The label stays mounted under the spinner so the button does not change
              width mid-request and shift everything below it. */}
          <Text variant="button" className={cn(labels[variant], loading && 'opacity-0')}>
            {label}
          </Text>
          {loading && (
            <View className="absolute inset-0 items-center justify-center" pointerEvents="none">
              <ActivityIndicator color={isPrimary ? colors.onBrand : colors.primary} />
            </View>
          )}
        </View>
      </View>
    </Pressable>
  );
}
