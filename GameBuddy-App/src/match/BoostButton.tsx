import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Zap } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { Pressable, View } from 'react-native';
import { matchApi } from '../api/match';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import { messageOf } from '../ui/ErrorNotice';
import * as feedback from '../ui/feedback';
import { Icon } from '../ui/Icon';
import { Text } from '../ui/Text';

/** Coins a boost costs when the weekly free one is not available. Mirrors BoostPolicy. */
export const BOOST_COST_COINS = 200;

/** Coins a rewind costs off Gold. Mirrors BoostPolicy.REWIND_COST_COINS. */
export const REWIND_COST_COINS = 50;

/**
 * Half an hour at the front of the deck, in your country.
 *
 * Lives in the deck header rather than in the Market, because a boost is worth buying at
 * the moment you are already swiping and want to be seen tonight — not while browsing
 * frames. It is the only paid thing on this screen for that reason.
 *
 * While a boost is running the button becomes a countdown. That is the whole feedback
 * loop: somebody who pays for thirty minutes and gets no acknowledgement has no way to
 * tell whether it did anything, and will not buy a second one.
 */
export function BoostButton() {
  const queryClient = useQueryClient();
  const colors = useThemeColors();
  const t = useT();
  const [failure, setFailure] = useState<string | null>(null);

  const status = useQuery({
    queryKey: ['boost'],
    queryFn: matchApi.boostStatus,
    // A boost expires on a wall clock, so a cached "active" goes stale on its own.
    staleTime: 30 * 1000,
  });

  const start = useMutation({
    mutationFn: matchApi.boost,
    onSuccess: (next) => {
      setFailure(null);
      queryClient.setQueryData(['boost'], next);
      // Spending coins moves the Market header and the profile balance.
      void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });

      // A buzz and nothing else — deliberately lighter than the Market's purchases. The
      // button turning into a countdown is already the acknowledgement, and it is a better
      // one than a toast because it keeps saying so for the full half hour. A cue on top of
      // that would be noise on the deck, which is the one screen people stay on.
      feedback.commit();
    },
    onError: (error) => setFailure(messageOf(error)),
  });

  const boost = status.data;
  const remaining = useCountdown(boost?.active ? boost.expiresAt : null);

  // The countdown reaching zero is the only signal that the boost is over; nothing pushes
  // that. Refetching once at expiry turns the button back into a buy button by itself.
  useEffect(() => {
    if (boost?.active && remaining === 0) {
      void queryClient.invalidateQueries({ queryKey: ['boost'] });
    }
  }, [boost?.active, remaining, queryClient]);

  if (!boost) return null;

  const active = boost.active && remaining > 0;
  const label = active
    ? formatRemaining(remaining)
    : boost.freeAvailable
      ? t.deck.boost.free
      : String(boost.cost);

  return (
    <View className="items-center">
      <Pressable
        onPress={() => start.mutate()}
        disabled={active || start.isPending}
        accessibilityRole="button"
        accessibilityLabel={
          active
            ? t.deck.boost.activeA11y(formatRemaining(remaining))
            : boost.freeAvailable
              ? t.deck.boost.freeA11y
              : t.deck.boost.costA11y(boost.cost)
        }
        accessibilityState={{ disabled: active }}
        hitSlop={8}
        className={[
          'h-9 flex-row items-center gap-1.5 rounded-full border px-3',
          active ? 'border-primary bg-primary/15' : 'border-line bg-raised',
          start.isPending ? 'opacity-50' : 'active:opacity-70',
        ].join(' ')}
      >
        {/* A running boost fills the bolt rather than only tinting it, so "on" is legible
            at 15px and to anyone who cannot separate the two colours. */}
        <Icon
          as={Zap}
          size={15}
          tone={active || boost.freeAvailable ? 'primary' : 'muted'}
          fill={active ? colors.primary : 'none'}
        />
        <Text
          className={[
            'font-semibold text-[13px] leading-[17px]',
            active || boost.freeAvailable ? 'text-primary' : 'text-muted',
          ].join(' ')}
        >
          {label}
        </Text>
      </Pressable>

      {failure && (
        <Text variant="caption" className="max-w-[120px] text-center text-danger">
          {failure}
        </Text>
      )}
    </View>
  );
}

/** Seconds left until an ISO instant, ticking once a second. Zero when there is none. */
function useCountdown(expiresAt: string | null | undefined): number {
  const [remaining, setRemaining] = useState(() => secondsUntil(expiresAt));

  useEffect(() => {
    setRemaining(secondsUntil(expiresAt));
    if (!expiresAt) return;

    const timer = setInterval(() => setRemaining(secondsUntil(expiresAt)), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);

  return remaining;
}

function secondsUntil(expiresAt: string | null | undefined): number {
  if (!expiresAt) return 0;
  return Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
}

/** mm:ss while under an hour, which a thirty-minute boost always is. */
function formatRemaining(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, '0')}`;
}
