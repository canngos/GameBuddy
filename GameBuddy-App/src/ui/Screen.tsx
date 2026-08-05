import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, View } from 'react-native';
import { SafeAreaView, type Edge } from 'react-native-safe-area-context';
import { cn } from './cn';

type ScreenProps = {
  children: ReactNode;
  /** Wrap the content in a ScrollView. Off for screens that manage their own height. */
  scroll?: boolean;
  /** Which insets to honour. Tabbed screens leave `bottom` to the tab bar. */
  edges?: readonly Edge[];
  padded?: boolean;
  className?: string;
};

/**
 * Page chrome: safe-area insets, the standard gutter, and keyboard avoidance.
 *
 * Keyboard handling lives here rather than per-screen because nearly every screen in
 * the auth and onboarding flow has a field near the bottom, and getting it wrong means
 * the keyboard covers the button the user is reaching for.
 */
export function Screen({
  children,
  scroll = false,
  edges = ['top', 'bottom'],
  padded = true,
  className,
}: ScreenProps) {
  const gutter = padded ? 'px-6' : '';

  return (
    <SafeAreaView className="flex-1 bg-canvas" edges={edges}>
      <KeyboardAvoidingView
        className="flex-1"
        // iOS moves the whole view. On Android the system already resizes the window,
        // and 'padding' there double-counts and leaves a gap above the keyboard.
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {scroll ? (
          <ScrollView
            // grow, not flex: fill the screen when short, scroll when tall.
            contentContainerClassName={cn('flex-grow pb-8', gutter, className)}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {children}
          </ScrollView>
        ) : (
          <View className={cn('flex-1', gutter, className)}>{children}</View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
