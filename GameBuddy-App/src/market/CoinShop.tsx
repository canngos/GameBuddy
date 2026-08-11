import { Pressable, View } from 'react-native';
import { COIN_PACKS, bonusPercent, type CoinPack } from '../api/billing';
import { storeAvailable } from '../billing/purchases';
import { usePurchase } from '../billing/usePurchase';
import { Card, ErrorNotice, Text } from '../ui';

/**
 * Coin packs, bought with real money.
 *
 * Coins are earned through badges first and bought second, and the ordering on this screen
 * says so: the shelf of things to spend them on comes above this, not below it. Somebody
 * who has never seen a frame they want has no reason to buy currency, and leading with the
 * currency is how a cosmetics shop starts feeling like a slot machine.
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
  const buy = usePurchase('coins');
  const canBuy = storeAvailable();

  return (
    <View
      className="gap-3 pb-8"
      onLayout={(event) => onLayoutY?.(event.nativeEvent.layout.y)}
    >
      <View className="gap-1">
        <Text variant="overline">COINS</Text>
        <Text variant="caption">
          {canBuy
            ? 'Earn them from badges, or top up here.'
            : 'Earn them from badges. Buying coins is not available in this build yet.'}
        </Text>
      </View>

      {COIN_PACKS.map((pack) => (
        <PackRow
          key={pack.productId}
          pack={pack}
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
          <Text variant="bodyStrong">Your coins are on the way</Text>
          <Text variant="caption" className="mt-1">
            They can take a moment to arrive. There is no need to buy again.
          </Text>
        </Card>
      )}
    </View>
  );
}

function PackRow({
  pack,
  balance,
  disabled,
  onPress,
}: {
  pack: CoinPack;
  balance: number;
  disabled: boolean;
  onPress: () => void;
}) {
  const bonus = bonusPercent(pack);

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={`Buy ${pack.coins} coins for ${pack.price}`}
      accessibilityState={{ disabled }}
      className={[
        'flex-row items-center justify-between gap-4 rounded-card border border-line bg-raised p-4',
        disabled ? 'opacity-50' : 'active:opacity-70',
      ].join(' ')}
    >
      <View className="flex-1 flex-row items-center gap-3">
        <View className="h-10 w-10 items-center justify-center rounded-full bg-brand/15">
          <Text className="text-[18px] leading-[22px]">🪙</Text>
        </View>
        <View className="gap-0.5">
          <View className="flex-row items-center gap-2">
            <Text variant="bodyStrong">{pack.coins.toLocaleString()} coins</Text>
            {bonus !== null && (
              <View className="rounded-full bg-brand/15 px-2 py-0.5">
                <Text className="font-semibold text-[11px] leading-[15px] text-brand">
                  +{bonus}%
                </Text>
              </View>
            )}
          </View>
          {/* What the balance becomes, not just what the pack contains. The question on
              this screen is always "can I afford that frame", and this answers it. */}
          <Text variant="caption">Takes you to {(balance + pack.coins).toLocaleString()}</Text>
        </View>
      </View>

      <Text variant="bodyStrong" className="text-brand">
        {pack.price}
      </Text>
    </Pressable>
  );
}
