import type { LucideIcon } from 'lucide-react-native';
import type { ReactNode } from 'react';
import { View } from 'react-native';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { glow } from './glow';
import { Icon } from './Icon';
import { Text } from './Text';

type EmptyStateProps = {
  icon: LucideIcon;
  title: string;
  /** One or two sentences. What happened, and what to do about it. */
  blurb?: string;
  /** Buttons. Primary action first. */
  children?: ReactNode;
  /**
   * Draws the ring in the primary colour instead of neutral. For states that are an
   * *offer* — a paywall, an upgrade — rather than simply the end of a list.
   */
  accent?: boolean;
  className?: string;
};

/**
 * The "there is nothing here" panel.
 *
 * This shape was copy-pasted into the deck (twice — exhausted and filters-locked),
 * admirers, messages and community, every time as a 64px circle containing a 28px emoji
 * over a heading and a muted line. The emoji were the problem: 🎮 🔍 💬 are somebody
 * else's artwork, they differ between Android versions, they cannot take a colour, and
 * they made the emptiest screens in the app look the least designed.
 *
 * Empty states matter more than their frequency suggests. A new account sees several of
 * them before it sees a full deck, and a filtered deck that has run out is the moment
 * someone decides whether the app has anybody in it.
 */
export function EmptyState({
  icon,
  title,
  blurb,
  children,
  accent = false,
  className,
}: EmptyStateProps) {
  const colors = useThemeColors();

  return (
    <View className={cn('items-center gap-4 px-4', className)}>
      <View
        className={cn(
          'h-20 w-20 items-center justify-center rounded-full border',
          accent ? 'border-primary/40 bg-primary/10' : 'border-line bg-raised',
        )}
        // Only the accent variant glows. A glow on "that's everyone for now" would be
        // celebrating an empty list.
        style={glow(accent ? 'soft' : 'none', colors.primary)}
      >
        <Icon as={icon} size={32} tone={accent ? 'primary' : 'muted'} strokeWidth={1.75} />
      </View>

      <View className="gap-1.5">
        <Text variant="heading" className="text-center">
          {title}
        </Text>
        {!!blurb && (
          <Text variant="body" className="text-center text-muted">
            {blurb}
          </Text>
        )}
      </View>

      {!!children && <View className="w-full gap-2 pt-1">{children}</View>}
    </View>
  );
}
