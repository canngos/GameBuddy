import { useState } from 'react';
import { Pressable, TextInput, type TextInputProps, View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { Text } from './Text';

type TextFieldProps = TextInputProps & {
  label?: string;
  /** Shown below the field and outlines it to match. */
  error?: string | null;
  hint?: string;
  /** Adds a show/hide toggle and starts obscured. */
  secure?: boolean;
  className?: string;
};

export function TextField({
  label,
  error,
  hint,
  secure = false,
  className,
  onFocus,
  onBlur,
  ...rest
}: TextFieldProps) {
  const colors = useThemeColors();
  const [focused, setFocused] = useState(false);
  const [revealed, setRevealed] = useState(false);

  return (
    <View className={cn('self-stretch', className)}>
      {label && (
        <Text variant="label" className="mb-2 text-muted">
          {label}
        </Text>
      )}

      <View
        className={cn(
          'min-h-touch flex-row items-center gap-2 rounded-field border-2 bg-field px-4',
          focused && 'border-brand bg-field-focus',
          !focused && !error && 'border-transparent',
          error && 'border-danger bg-field-focus',
        )}
      >
        <TextInput
          className="flex-1 py-3 font-sans text-[15px] leading-[22px] text-content"
          // Not reachable by a class — this is a colour value, not a style.
          placeholderTextColor={colors.muted}
          secureTextEntry={secure && !revealed}
          // Off by default: these fields are emails, usernames and passwords, and iOS
          // capitalising the first letter of an email address is a support ticket.
          autoCapitalize="none"
          autoCorrect={false}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          {...rest}
        />

        {secure && (
          <Pressable
            onPress={() => setRevealed((v) => !v)}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel={revealed ? 'Hide password' : 'Show password'}
          >
            <Text variant="label" className="text-brand">
              {revealed ? 'Hide' : 'Show'}
            </Text>
          </Pressable>
        )}
      </View>

      {/* Error wins over hint: when both are present the error is the actionable one. */}
      {(error || hint) && (
        <Text
          variant="caption"
          className={cn('ml-1 mt-1.5', error ? 'text-danger' : 'text-muted')}
        >
          {error || hint}
        </Text>
      )}
    </View>
  );
}
