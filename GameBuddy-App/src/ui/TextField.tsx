import { type Ref, useState } from 'react';
import { Pressable, TextInput, type TextInputProps, View } from 'react-native';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { glow } from './glow';
import { Text } from './Text';

type TextFieldProps = TextInputProps & {
  label?: string;
  /** Shown below the field and outlines it to match. */
  error?: string | null;
  hint?: string;
  /** Adds a show/hide toggle and starts obscured. */
  secure?: boolean;
  className?: string;
  /**
   * Forwarded to the inner TextInput, so a caller can move focus between fields — the
   * date-of-birth row advances day to month to year on its own.
   *
   * A plain prop rather than `forwardRef`: React 19 passes `ref` to function components
   * like any other prop, and `forwardRef` is on its way out.
   */
  ref?: Ref<TextInput>;
};

/**
 * A stable handle for UI tests, derived from the label.
 *
 * The label itself is not usable as a selector: it renders as its own Text node above the
 * field, so an automated tap on "Password" hits the caption and the typing goes into
 * whichever input still had focus. That is not hypothetical — it put an email address and a
 * password into the same box on the first run of the sign-in flow, and the failure looked
 * like a broken login screen rather than a bad selector.
 *
 * Derived rather than passed per screen, so every field in the app gets one from this one
 * place and no caller has to remember. An explicit `testID` still wins.
 */
const fieldTestId = (label?: string) =>
  label ? `field-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')}` : undefined;

export function TextField({
  label,
  error,
  hint,
  secure = false,
  className,
  onFocus,
  onBlur,
  ref,
  testID,
  ...rest
}: TextFieldProps) {
  const colors = useThemeColors();
  const t = useT();
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
          focused && 'border-primary bg-field-focus',
          !focused && !error && 'border-transparent',
          error && 'border-danger bg-field-focus',
        )}
        // A lit ring on focus, which on a near-black canvas is what tells you where the
        // keyboard is pointed. Error outranks focus: a field that is both should look
        // wrong, not active.
        style={glow(error ? 'soft' : focused ? 'soft' : 'none', error ? colors.danger : colors.primary)}
      >
        <TextInput
          ref={ref}
          testID={testID ?? fieldTestId(label)}
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
            accessibilityLabel={revealed ? t.ui.hidePasswordA11y : t.ui.showPasswordA11y}
          >
            <Text variant="label" className="text-primary">
              {revealed ? t.ui.hidePassword : t.ui.showPassword}
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
