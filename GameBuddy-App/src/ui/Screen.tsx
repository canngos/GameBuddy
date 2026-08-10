import type { ReactNode } from 'react';
import { ScrollView, View } from 'react-native';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';
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
  /**
   * Pinned below the scroll area, always visible.
   *
   * For the actions that end a screen — Continue, Back, Save. Inside a `scroll` screen
   * those sit after the content, which is fine for a form and wrong for a list: choosing
   * three games out of a hundred meant scrolling the whole catalogue to reach Continue,
   * and then scrolling back up to carry on choosing.
   *
   * Inside the keyboard-avoiding view, so it rises with the keyboard instead of being
   * covered by it.
   */
  footer?: ReactNode;
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
  footer,
}: ScreenProps) {
  const gutter = padded ? 'px-6' : '';

  return (
    <SafeAreaView className="flex-1 bg-canvas" edges={edges}>
      {/* From react-native-keyboard-controller, not React Native.

          The built-in one is driven by the window being resized, and Android stopped
          resizing it when Expo turned on edge-to-edge by default in SDK 54 — so it did
          nothing at all there, whatever `behavior` it was given. `padding` was tried and
          measured: the compose bar stayed at y=2016 with the keyboard starting at y=1516,
          exactly where it had been.

          This one tracks the keyboard inset reported by the platform, so it works the same
          under edge-to-edge, and 'padding' is now correct on both platforms rather than
          being a no-op on one of them. */}
      <KeyboardAvoidingView className="flex-1" behavior="padding">
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

        {/* The hairline is what stops this reading as the end of the list. Without it the
            buttons look like the last two rows, and content scrolling underneath them
            looks like a rendering fault. */}
        {footer && (
          <View className={cn('border-t border-line bg-canvas pb-2 pt-3', gutter)}>{footer}</View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
