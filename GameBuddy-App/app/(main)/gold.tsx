import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { billingApi, developmentReceipt, GOLD_PLANS, type GoldPlan } from '../../src/api/billing';
import { BackHeader, Button, Card, ErrorNotice, Screen, SelectRow, Text } from '../../src/ui';

/**
 * The Gold pitch and plan picker.
 *
 * Reached from the admirers screen, the daily-limit sheet, and the Market — the moments
 * where the thing Gold removes is the thing currently in the way. Never from onboarding:
 * gating before anyone has seen the product converts worse, and the free tier is meant to
 * be good enough to sell this on its own.
 *
 * **No store sheet yet.** The purchase goes straight to `/billing/redeem` with a
 * development receipt, which the backend accepts only because sandbox billing is switched
 * on locally. That is the whole of the "pretend it was paid" behaviour, and it lives in
 * one place: `developmentReceipt`. When the IAP library lands, the receipt comes from the
 * store instead and nothing else on this screen changes.
 */
export default function Gold() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<GoldPlan>(GOLD_PLANS[1]);

  const subscription = useQuery({
    queryKey: ['subscription'],
    queryFn: billingApi.subscription,
  });

  const isGold = subscription.data?.tier === 'GOLD';

  const buy = useMutation({
    mutationFn: (plan: GoldPlan) =>
      billingApi.redeem(plan.productId, developmentReceipt(plan.productId)),
    onSuccess: () => {
      // Everything that reads a tier is now wrong: the deck's allowance, the admirers
      // list's `locked`, and the subscription itself.
      void queryClient.invalidateQueries({ queryKey: ['subscription'] });
      void queryClient.invalidateQueries({ queryKey: ['admirers'] });
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
    },
  });

  return (
    <Screen
      scroll
      footer={
        isGold ? (
          <Button label="Done" onPress={() => router.back()} />
        ) : (
          <View className="gap-2">
            <Button
              label={`Continue — ${selected.price}`}
              loading={buy.isPending}
              onPress={() => buy.mutate(selected)}
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
        ['Advanced filters', 'Game, platform, region, online now.'],
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
