import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { billingApi, GOLD_PLANS, type GoldPlan } from '../../src/api/billing';
import { storeAvailable } from '../../src/billing/purchases';
import { usePurchase } from '../../src/billing/usePurchase';
import { BackHeader, Button, Card, ErrorNotice, Screen, SelectRow, Text } from '../../src/ui';

/**
 * The Gold pitch and plan picker.
 *
 * Reached from the admirers screen, the daily-limit sheet, and the Market — the moments
 * where the thing Gold removes is the thing currently in the way. Never from onboarding:
 * gating before anyone has seen the product converts worse, and the free tier is meant to
 * be good enough to sell this on its own.
 *
 * Buying goes through RevenueCat — see `src/billing/purchases.ts`. The store sheet closing
 * does not make anyone Gold: RevenueCat has to tell our backend first, so `usePurchase`
 * waits for the entitlement and this screen has a state for "paid, still arriving".
 *
 * `canBuy` is false on builds made before the SDK was installed, and on builds with no
 * RevenueCat key. That is a normal state to be in right now, not an error.
 */
export default function Gold() {
  const router = useRouter();
  const [selected, setSelected] = useState<GoldPlan>(GOLD_PLANS[1]);

  const subscription = useQuery({
    queryKey: ['subscription'],
    queryFn: billingApi.subscription,
  });

  const isGold = subscription.data?.tier === 'GOLD';

  // Opens the store sheet, then waits for the webhook to land before calling it done.
  const buy = usePurchase();
  // False on any build made before react-native-purchases was installed, and on any build
  // with no RevenueCat key configured.
  const canBuy = storeAvailable();

  return (
    <Screen
      scroll
      footer={
        isGold ? (
          <Button label="Done" onPress={() => router.back()} />
        ) : (
          <View className="gap-2">
            {/* Disabled rather than hidden when the build cannot purchase. A missing button
                reads as a broken screen; a disabled one with a reason underneath does not,
                and this state is normal for anyone running a build made before the store
                was wired up. */}
            <Button
              label={canBuy ? `Continue — ${selected.price}` : 'Purchases not available yet'}
              loading={buy.isPending}
              disabled={!canBuy}
              onPress={() => buy.buy(selected.productId)}
            />
            <Button label="Not now" variant="ghost" onPress={() => router.back()} />
          </View>
        )
      }
    >
      <BackHeader title="" />

      <View className="gap-2 pb-6">
        <Text variant="overline">GAMEBUDDY</Text>
        <Text variant="display">Gold</Text>
      </View>

      {isGold ? (
        <ActiveMembership expiresAt={subscription.data?.expiresAt ?? null} />
      ) : (
        <>
          <Benefits />

          <View className="gap-3 pt-6">
            <Text variant="label" className="text-muted">
              Choose a plan
            </Text>
            {GOLD_PLANS.map((plan, index) => (
              <SelectRow
                key={plan.productId}
                label={`${plan.label} · ${plan.price}`}
                hint={plan.note ?? `Billed every ${plan.period}`}
                selected={selected.productId === plan.productId}
                onPress={() => setSelected(plan)}
                position={
                  index === 0 ? 'first' : index === GOLD_PLANS.length - 1 ? 'last' : 'middle'
                }
              />
            ))}
          </View>

          {buy.error && (
            <View className="pt-4">
              <ErrorNotice error={buy.error} />
            </View>
          )}

          {/* The store took the money but our side has not heard yet. Deliberately not an
              error: RevenueCat keeps retrying the webhook, so this resolves itself, and
              calling it a failure is how somebody ends up paying twice. */}
          {buy.awaitingEntitlement && (
            <View className="mt-4 rounded-card bg-raised p-4">
              <Text variant="bodyStrong">Your purchase is going through</Text>
              <Text variant="caption" className="mt-1">
                It can take a moment to arrive. Gold will switch on by itself — there is no
                need to buy again.
              </Text>
            </View>
          )}

          <Text variant="caption" className="pt-4">
            Cancel any time from your store account. A subscription renews until you cancel
            it.
          </Text>
        </>
      )}
    </Screen>
  );
}

function Benefits() {
  return (
    <View className="gap-3">
      {[
        ['See who liked you', 'Every face, not just the number.'],
        ['No daily limit', 'Swipe and like as much as you want.'],
        // Platform is deliberately not listed: the profile has no such field, so it is not
        // something we can filter by. Promising it on a paid product is the kind of claim
        // that earns a refund and a store complaint rather than a subscriber.
        ['Advanced filters', 'Narrow the deck by game, region, and who is online now.'],
        ['The Gold frame and banner', 'Yours while you are a member. Not for sale.'],
      ].map(([title, body]) => (
        <View key={title} className="flex-row gap-3">
          <View className="mt-1.5 h-2 w-2 rounded-full bg-brand" />
          <View className="flex-1 gap-0.5">
            <Text variant="bodyStrong">{title}</Text>
            <Text variant="caption">{body}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

function ActiveMembership({ expiresAt }: { expiresAt: string | null }) {
  const renews = expiresAt
    ? new Date(expiresAt).toLocaleDateString(undefined, {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      })
    : null;

  return (
    <Card>
      <Text variant="heading">You are a member</Text>
      <Text variant="body" className="mt-1 text-muted">
        {renews ? `Your membership runs until ${renews}.` : 'Your membership is active.'}
      </Text>
      <Text variant="caption" className="mt-4">
        The Gold frame and banner are in your profile — equip them from the Market.
      </Text>
    </Card>
  );
}
