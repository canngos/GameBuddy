import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { View } from 'react-native';
import { cosmeticsApi } from '../api/cosmetics';
import { useThemeColors } from '../theme';
import { Burst } from '../ui/Burst';
import { CountUp } from '../ui/CountUp';
import { Text } from '../ui/Text';

/**
 * The coin balance in the Market header, and the only place it is shown.
 *
 * The balance goes in the header rather than beside each price because the one question a
 * gamer has on this screen is what they can afford. What is new is that it now *moves*:
 * before, buying a 200-coin frame changed a figure from 1,440 to 1,240 between two frames,
 * and the entire evidence that a transaction had occurred was a number nobody saw change.
 * That is UI_NOTE §5.1's complaint in one line.
 *
 * **Up bursts, down does not.** Coins arriving — a daily claim, a quest, an advert, a
 * top-up — get the gold burst as well as the count. Coins leaving only get the count.
 * Throwing sparks when money leaves the account congratulates somebody for spending, which
 * is both the wrong signal and a slightly grubby one; the celebration for a purchase belongs
 * on the thing that was bought, and that is what the toast is for.
 *
 * Reads `['cosmetics']` itself rather than taking a prop. It is the same query key the
 * Market already uses, so react-query serves both from one request, and it means the balance
 * cannot be handed a stale figure by a parent that forgot to re-render.
 */
export function CoinBalance() {
  const colors = useThemeColors();
  const store = useQuery({ queryKey: ['cosmetics'], queryFn: cosmeticsApi.store });

  const coins = store.data?.coins ?? 0;

  const previous = useRef(coins);
  const [gains, setGains] = useState(0);

  useEffect(() => {
    // Strictly greater. Equal is a refetch that changed nothing, and less is a purchase,
    // which has its own acknowledgement elsewhere.
    if (coins > previous.current) setGains((n) => n + 1);
    previous.current = coins;
  }, [coins]);

  return (
    <View className="items-end">
      {/* Mounted only once something has actually been earned, because `Burst` plays on
          mount — that is what makes it fire without a caller having to arrange anything, and
          it is also why it cannot simply sit here from the first render. Conditional
          *mounting*, not a conditional class: swapping which class keys exist between
          renders is what stops NativeWind painting a subtree. */}
      {gains > 0 && <Burst play={gains} color={colors.gold} radius={64} particles={14} />}

      <CountUp
        value={coins}
        className="text-[22px] leading-[28px] text-gold"
        accessibilityLabel={`${coins} coins`}
      />
      <Text variant="caption">coins</Text>
    </View>
  );
}
