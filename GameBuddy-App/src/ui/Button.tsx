import { ActivityIndicator, Pressable, type PressableProps, View } from 'react-native';
import { brand, useThemeColors } from '../theme';
import { cn } from './cn';
import { lift } from './elevation';
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

const containers: Record<Variant, string> = {
  primary: 'bg-brand active:bg-brand-deep',
  secondary: 'bg-transparent border-2 border-brand active:bg-brand/10',
  ghost: 'bg-transparent active:bg-raised',
  danger: 'bg-transparent border-2 border-danger active:bg-danger/10',
};

const labels: Record<Variant, string> = {
  primary: 'text-white',
  secondary: 'text-brand',
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
  ...rest
}: ButtonProps) {
  const colors = useThemeColors();
  // A loading button is disabled too. Otherwise a double tap during a slow request
  // registers twice, and on registration that is two verification emails.
  const inert = disabled || loading;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!inert, busy: loading }}
      disabled={inert}
      className={cn(
        'flex-row items-center justify-center rounded-full',
        sizes[size],
        containers[variant],
        inert && 'opacity-40',
        className,
      )}
      // The lift only belongs on the filled variant; a shadow under a transparent
      // button draws a rectangle around nothing. A style rather than a `shadow-*`
      // class — see src/ui/elevation.ts. 'none' rather than undefined so the style
      // keys never come and go; see src/ui/hairline.ts.
      style={lift(variant === 'primary' && !inert ? 'lg' : 'none', brand.DEFAULT)}
      {...rest}
    >
      {/* The label stays mounted under the spinner so the button does not change
          width mid-request and shift everything below it. */}
      <Text variant="button" className={cn(labels[variant], loading && 'opacity-0')}>
        {label}
      </Text>
      {loading && (
        <View className="absolute inset-0 items-center justify-center" pointerEvents="none">
          <ActivityIndicator
            color={variant === 'primary' ? colors.onBrand : colors.brand}
          />
        </View>
      )}
    </Pressable>
  );
}
