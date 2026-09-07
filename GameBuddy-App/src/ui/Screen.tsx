import type { ReactNode, RefObject } from 'react';
import { ScrollView, View } from 'react-native';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';
import { SafeAreaView, type Edge } from 'react-native-safe-area-context';
import { cn } from './cn';
import { useScreenScale } from './useScreenScale';

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
  /**
   * The underlying ScrollView, for screens that need to move the viewport themselves.
   *
   * Only meaningful with `scroll`. Added for the Market, where refusing a purchase for
   * want of coins has to be able to show the gamer where coins come from.
   */
  scrollRef?: RefObject<ScrollView | null>;
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
  scrollRef,
}: ScreenProps) {
  const { pick } = useScreenScale();
  /*
   * The page gutter and the two paddings below, per tier.
   *
   * Numbers rather than classes: the value has to change with the device and a compiled class
   * cannot. Every route inherits these, so this is the cheapest place in the app to give a
   * small phone its screen back — 24dp of side gutter on a 360dp-wide device is 13% of the
   * width spent on nothing.
   *
   * Safe here because no caller passes padding through `className` — unlike `Card`, where an
   * inline style would silently beat the `p-0` that `LobbyCard` depends on. If a screen ever
   * does need its own gutter, give it a prop rather than a class, or this will override it
   * without a word.
   */
  const gutterPad = padded ? pick(16, 20, 24) : 0;
  const scrollPad = pick(20, 26, 32);
  const footerPad = { top: pick(8, 10, 12), bottom: pick(4, 6, 8) };

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
            ref={scrollRef}
            // grow, not flex: fill the screen when short, scroll when tall.
            contentContainerClassName={cn('flex-grow', className)}
            contentContainerStyle={{ paddingHorizontal: gutterPad, paddingBottom: scrollPad }}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {children}
          </ScrollView>
        ) : (
          <View className={cn('flex-1', className)} style={{ paddingHorizontal: gutterPad }}>
            {children}
          </View>
        )}

        {/* The hairline is what stops this reading as the end of the list. Without it the
            buttons look like the last two rows, and content scrolling underneath them
            looks like a rendering fault. */}
        {footer && (
          <View
            className="border-t border-line bg-canvas"
            style={{
              paddingHorizontal: gutterPad,
              paddingTop: footerPad.top,
              paddingBottom: footerPad.bottom,
            }}
          >
            {footer}
          </View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
