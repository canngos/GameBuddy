import { useEffect } from 'react';
import { Pressable, View } from 'react-native';
import { trackFunnel } from '../api/funnel';
import { coinPackRows, type CoinPack, type ResolvedPrice } from '../api/billing';
import { storeAvailable } from '../billing/purchases';
import { usePurchase } from '../billing/usePurchase';
import { useStorePrices } from '../billing/useStorePrices';
import { CoinPackIcon, tierForIndex, type CoinPackTier } from './CoinPackIcon';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
import { Card, ErrorNotice, Text } from '../ui';

/**
 * Coin packs, bought with real money.
 *
 * First thing under Shop, because the balance is what everything else in that tab is priced
 * in. Earning used to be pointed at from here, back when it was a section further down the
 * same scroll; it is a whole tab of its own now, sitting next to this one, so the sentence
 * that named it was telling people what they were already looking at.
 *
 * The packs are consumables. There is no entitlement to check afterwards — only a balance
 * that should have gone up — which is why {@link usePurchase} is told this is a `coins`
 * purchase and watches the balance instead of the tier.
 */
export function CoinShop({
  balance,
  onLayoutY,
}: {
  balance: number;
  /**
   * Reports this section's offset inside the scroll content.
   *
   * From `onLayout`, which is relative to the parent — exactly what `scrollTo` wants.
   * `measureInWindow` would give viewport coordinates, which scroll to the wrong place
   * by however far the list is already scrolled.
   */
  onLayoutY?: (y: number) => void;
}) {
  const t = useT();
  const upper = useUpper();
  const buy = usePurchase('coins');
  const canBuy = storeAvailable();
  // Real prices, in the buyer's currency. The rows are built together so a badge and the
  // price it was derived from cannot come from two different snapshots of the same query.
  const rows = coinPackRows(useStorePrices());

  // Says whether the currency is understood as buyable at all — the Market renders this
  // section on every visit, so it is a view count rather than an intent signal.
  useEffect(() => {
    trackFunnel('COIN_SHOP_VIEWED');
  }, []);

  return (
    <View
      className="gap-3 pb-8"
      onLayout={(event) => onLayoutY?.(event.nativeEvent.layout.y)}
    >
      <View className="gap-1">
        <Text variant="overline">{upper(t.market.coins.header)}</Text>
        {/* Only when the packs below are dead. Captioning a working shelf with a sentence
            about what a coin pack is says nothing the prices do not already say. */}
        {!canBuy && <Text variant="caption">{t.market.coins.blurbNoBuy}</Text>}
      </View>

      {rows.map(({ pack, price, bonus }, index) => (
        <PackRow
          key={pack.productId}
          pack={pack}
          tier={tierForIndex(index)}
          price={price}
          bonus={bonus}
          balance={balance}
          disabled={!canBuy || buy.isPending}
          onPress={() => buy.buy(pack.productId)}
        />
      ))}

      {buy.error && <ErrorNotice error={buy.error} />}

      {/* Paid, not yet delivered. Deliberately not an error: RevenueCat keeps retrying the
          webhook, so this resolves itself, and calling it a failure is how somebody ends up
          buying the same pack twice. */}
      {buy.awaitingEntitlement && (
        <Card>
          <Text variant="bodyStrong">{t.market.coins.onTheWay}</Text>
          <Text variant="caption" className="mt-1">
            {t.market.coins.onTheWayBody}
          </Text>
        </Card>
      )}
    </View>
  );
}

function PackRow({
  pack,
  tier,
  price,
  bonus,
  balance,
  disabled,
  onPress,
}: {
  pack: CoinPack;
  /** Which of the four pictures this rung gets. See CoinPackIcon. */
  tier: CoinPackTier;
  price: ResolvedPrice;
  bonus: number | null;
  balance: number;
  disabled: boolean;
  onPress: () => void;
}) {
  const t = useT();

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={
        price.pending
          ? t.market.coins.packA11yPending(pack.coins)
          : t.market.coins.packA11y(pack.coins, price.text)
      }
      accessibilityState={{ disabled }}
      className={[
        'flex-row items-center justify-between gap-4 rounded-card border border-line bg-raised p-4',
        disabled ? 'opacity-50' : 'active:opacity-70',
      ].join(' ')}
    >
      <View className="flex-1 flex-row items-center gap-3">
        {/* 40dp well, 26dp mark. Bigger than the 18dp Lucide glyph this replaced, because
            a heap and a chest carry detail a single coin did not. */}
        <View className="h-10 w-10 items-center justify-center rounded-full bg-gold/15">
          <CoinPackIcon tier={tier} size={26} />
        </View>
        <View className="gap-0.5">
          <View className="flex-row items-center gap-2">
            <Text variant="bodyStrong">
              {t.market.coins.packCoins(pack.coins.toLocaleString(t.locale))}
            </Text>
            {bonus !== null && (
              <View className="rounded-full bg-gold/15 px-2 py-0.5">
                <Text className="font-semibold text-[11px] leading-[15px] text-gold">
                  +{bonus}%
                </Text>
              </View>
            )}
          </View>
          {/* What the balance becomes, not just what the pack contains. The question on
              this screen is always "can I afford that frame", and this answers it. */}
          <Text variant="caption">
            {t.market.coins.takesYouTo((balance + pack.coins).toLocaleString(t.locale))}
          </Text>
        </View>
      </View>

      {/* A placeholder, not the bundled dollar price: showing "$16.99" and then swapping it
          for "₺549,99" a moment later re-creates a milder version of the very problem the
          localised price exists to fix. Bounded by the six second deadline in
          `fetchStorePrices`, and normally never seen at all because the tab layout prefetches
          this before anyone can reach the Market. */}
      {price.pending ? (
        <View className="h-4 w-14 rounded bg-line" />
      ) : (
        <Text variant="bodyStrong" className="text-gold">
          {price.text}
        </Text>
      )}
    </Pressable>
  );
}
