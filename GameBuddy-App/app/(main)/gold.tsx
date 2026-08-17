import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Crown, Eye, Infinity as InfinityIcon, SlidersHorizontal, type LucideIcon } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { billingApi, GOLD_PLANS, type GoldPlan } from '../../src/api/billing';
import { cosmeticsApi } from '../../src/api/cosmetics';
import type { Cosmetic, CosmeticStore } from '../../src/api/types';
import { trackFunnel } from '../../src/api/funnel';
import { storeAvailable } from '../../src/billing/purchases';
import { usePurchase } from '../../src/billing/usePurchase';
import { useThemeColors } from '../../src/theme';
import { GradientView } from '../../src/ui/Gradient';
import { glow } from '../../src/ui/glow';
import { BackHeader, Button, Card, ErrorNotice, Icon, Screen, Text } from '../../src/ui';

/** The image fills its already-sized box. A constant, so it is not a new prop per row. */
const FILL = StyleSheet.create({ fill: { width: '100%', height: '100%' } }).fill;

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
 *
 * **Nothing on this screen puts text on the gold gradient, and that is a hard rule.** The
 * gold ramp inverts between themes — it is bright yellow in dark and dark brown in light —
 * so no single text colour clears 4.5:1 on it in both. The gradient appears only as a
 * low-alpha wash behind `surface`, with the type on the surface. See the two-gradient note
 * in `src/theme/gradients.ts`, which is the same problem with the violet.
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

  // The denominator of "paywall view to trial start". Once per mount rather than per
  // render, and not gated on tier: a member reopening the paywall is a view too, and
  // filtering it out here would hide the fact that they keep landing on it.
  useEffect(() => {
    trackFunnel('PAYWALL_VIEWED');
  }, []);

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
              onPress={() => {
                // Before the sheet opens, so an abandoned purchase still counts as intent.
                // The gap between this and a granted subscription is the store's own
                // drop-off, which is worth seeing apart from ours.
                trackFunnel('CHECKOUT_STARTED');
                buy.buy(selected.productId);
              }}
            />
            <Button label="Not now" variant="ghost" onPress={() => router.back()} />
          </View>
        )
      }
    >
      <BackHeader title="" />

      <Hero isGold={isGold} />

      {isGold ? (
        <>
          <ActiveMembership expiresAt={subscription.data?.expiresAt ?? null} />
          <GoldCosmetics isGold />
        </>
      ) : (
        <>
          <Benefits />

          {/* Directly under the claims, because it is the only one of them that can be
              *shown* rather than described. */}
          <GoldCosmetics isGold={false} />

          <View className="gap-3 pt-8">
            <Text variant="label" className="text-muted">
              Choose a plan
            </Text>
            {GOLD_PLANS.map((plan) => (
              <PlanRow
                key={plan.productId}
                plan={plan}
                selected={selected.productId === plan.productId}
                onPress={() => setSelected(plan)}
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

/**
 * The wordmark, as the one moment in the app that should look expensive.
 *
 * The crown is lit rather than flat, and the panel carries a wash of the gold ramp. Both
 * are the treatment the deck's Match button and the streak strip use, so this reads as the
 * top of the same system rather than a screen designed on its own.
 */
function Hero({ isGold }: { isGold: boolean }) {
  const colors = useThemeColors();

  return (
    <View className="mb-2 overflow-hidden rounded-card" style={glow('soft', colors.gold)}>
      <View className="overflow-hidden rounded-card border border-line bg-surface">
        <GradientView
          name="gold"
          direction="diagonal"
          className="absolute inset-0 opacity-10"
          pointerEvents="none"
        />

        <View className="items-center gap-3 px-6 py-8">
          <View className="rounded-full" style={glow('strong', colors.gold)}>
            <View className="h-16 w-16 items-center justify-center rounded-full bg-gold/15">
              <Icon as={Crown} size={30} tone="gold" fill={colors.gold} strokeWidth={1.5} />
            </View>
          </View>

          <View className="items-center gap-1">
            <Text variant="overline" className="text-gold">
              GAMEBUDDY
            </Text>
            {/* `hero` — shared with the match moment, and with nothing else. These are the
                two screens the product wants people to remember. */}
            <Text variant="hero" className="text-center text-content">
              Gold
            </Text>
            <Text variant="caption" className="text-center">
              {isGold
                ? 'Thanks for backing GameBuddy.'
                : 'The whole app, with nothing in the way.'}
            </Text>
          </View>
        </View>
      </View>
    </View>
  );
}

/**
 * What is actually being sold. Three claims, each tied to something the product does.
 *
 * The frame and banner used to be a fourth row here, reading "Yours while you are a member.
 * Not for sale." They are now shown as themselves in `GoldCosmetics` directly below this
 * list — a picture of the thing beats a sentence about it, and keeping both would have made
 * the same promise twice on one screen.
 */
const BENEFITS: { icon: LucideIcon; title: string; body: string }[] = [
  { icon: Eye, title: 'See who liked you', body: 'Every face, not just the number.' },
  { icon: InfinityIcon, title: 'No daily limit', body: 'Swipe and like as much as you want.' },
  // Platform is deliberately not listed: the profile has no such field, so it is not
  // something we can filter by. Promising it on a paid product is the kind of claim
  // that earns a refund and a store complaint rather than a subscriber.
  {
    icon: SlidersHorizontal,
    title: 'Advanced filters',
    body: 'Narrow the deck by game, region, and who is online now.',
  },
];

function Benefits() {
  return (
    <View className="gap-3 pt-6">
      {BENEFITS.map(({ icon, title, body }) => (
        // A glyph rather than the bullet dot that was here. Four identical dots made four
        // different promises look like one list; an eye, an infinity and a slider say what
        // each one is before the words are read.
        <View key={title} className="flex-row items-center gap-3 rounded-card bg-raised p-4">
          <View className="h-10 w-10 items-center justify-center rounded-full bg-gold/15">
            <Icon as={icon} size={18} tone="gold" />
          </View>
          <View className="flex-1 gap-0.5">
            <Text variant="bodyStrong">{title}</Text>
            <Text variant="caption">{body}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

/**
 * The Gold frame and banner, on the screen that sells them.
 *
 * They used to sit on the Market's shelf wearing a "Members only" label. That was the wrong
 * room: every other row there answers "what can I buy", and theirs answered "not this" —
 * a locked row is a weaker advert for a subscription than the art itself, and it spent two
 * slots of a shop to say so. Here the same two items are the fourth benefit, except you can
 * see them.
 *
 * This is also now the **only** place a member can put them on, which is why it is not
 * merely decorative when `isGold` — removing them from the Market took the equip control
 * with them. Taking one *off* is still the Market's "Take off my frame", which unequips by
 * kind rather than by id and therefore still reaches these; see the note on `wearingOne` in
 * `app/(main)/market.tsx`.
 */
function GoldCosmetics({ isGold }: { isGold: boolean }) {
  const queryClient = useQueryClient();

  // Same key the Market uses, so react-query serves both from one request and an equip here
  // is already applied when that screen is next opened.
  const store = useQuery({ queryKey: ['cosmetics'], queryFn: cosmeticsApi.store });

  const items = [...(store.data?.frames ?? []), ...(store.data?.banners ?? [])].filter(
    (item) => item.membershipOnly,
  );

  const equip = useMutation({
    mutationFn: cosmeticsApi.equip,
    onSuccess: (next: CosmeticStore) => {
      // Written through rather than refetched, for the reason the Market gives: the response
      // is the whole refreshed store, and refetching leaves a window where the item is worn
      // but still offers an Equip button.
      queryClient.setQueryData(['cosmetics'], next);
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  // Absent rather than an empty heading. The catalogue belongs to the server and is allowed
  // to carry no membership items at all.
  if (items.length === 0) return null;

  return (
    <View className="gap-3 pt-8">
      <Text variant="label" className="text-muted">
        {isGold ? 'Yours to wear' : 'Only for members'}
      </Text>

      {items.map((item) => (
        <GoldCosmeticRow
          key={item.id}
          item={item}
          isGold={isGold}
          busy={equip.isPending}
          onEquip={() => equip.mutate(item.id)}
        />
      ))}

      {equip.error && <ErrorNotice error={equip.error} />}
    </View>
  );
}

function GoldCosmeticRow({
  item,
  isGold,
  busy,
  onEquip,
}: {
  item: Cosmetic;
  isGold: boolean;
  busy: boolean;
  onEquip: () => void;
}) {
  const isBanner = item.kind === 'BANNER';

  return (
    <View className="flex-row items-center gap-4 rounded-card border border-line bg-raised p-4">
      {/* Shown at full brightness even when locked. Dimming the one thing being sold to
          signal that it is not yours yet argues against the sale. */}
      <View
        className={
          isBanner
            ? 'h-16 w-24 overflow-hidden rounded-xl bg-surface'
            : 'h-16 w-16 items-center justify-center rounded-full bg-surface'
        }
      >
        <Image
          source={{ uri: item.image }}
          style={FILL}
          contentFit={isBanner ? 'cover' : 'contain'}
          autoplay
          transition={150}
          // See `market.tsx`.
          cachePolicy="memory-disk"
          recyclingKey={item.id}
        />
      </View>

      <View className="min-w-0 flex-1 gap-0.5">
        <Text variant="bodyStrong" numberOfLines={1}>
          {item.name}
        </Text>
        <Text variant="caption" numberOfLines={1}>
          {isBanner ? 'Banner' : 'Frame'}
        </Text>
      </View>

      {!isGold ? (
        // No control at all for a non-member, and no padlock either: a lock implies there is
        // a way to open it from here, and the way is the button at the bottom of this screen.
        <Text variant="label" className="shrink-0 text-gold">
          With Gold
        </Text>
      ) : item.equipped ? (
        <Text variant="label" className="shrink-0 text-primary">
          Worn
        </Text>
      ) : (
        <Pressable
          disabled={busy}
          onPress={onEquip}
          accessibilityRole="button"
          accessibilityLabel={`Equip ${item.name}`}
          accessibilityState={{ disabled: busy }}
          // Same outlined pill the Market's Equip uses, so the two screens agree about what
          // an equip control looks like.
          className="shrink-0 rounded-full border-2 border-primary bg-transparent px-4 py-2 active:opacity-70"
        >
          <Text variant="label" className="text-primary">
            Equip
          </Text>
        </Pressable>
      )}
    </View>
  );
}

/**
 * One plan.
 *
 * Was a `SelectRow` — the same control the theme picker uses. That put "Yearly · $39.99"
 * and "Save 58%" in the same grey as "Always dark", which is the wrong weight for the only
 * decision on a paid screen: the price and the saving are the comparison being made, and
 * they were the quietest things in the row.
 *
 * `note` carries whatever distinguishes a plan — a free trial, a saving — and it comes from
 * `GOLD_PLANS`, so the badge follows a price change rather than having to be updated
 * alongside it.
 */
function PlanRow({
  plan,
  selected,
  onPress,
}: {
  plan: GoldPlan;
  selected: boolean;
  onPress: () => void;
}) {
  const colors = useThemeColors();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      accessibilityLabel={`${plan.label}, ${plan.price}${plan.note ? `, ${plan.note}` : ''}`}
      // Both branches carry the same class keys and only the values move — a class that
      // appears on one state and not the other stops NativeWind painting the subtree.
      // See `src/ui/hairline.ts`.
      className={
        selected
          ? 'flex-row items-center gap-3 rounded-card border-2 border-gold bg-gold/10 p-4 active:opacity-80'
          : 'flex-row items-center gap-3 rounded-card border-2 border-line bg-surface p-4 active:opacity-80'
      }
      style={glow(selected ? 'soft' : 'none', colors.gold)}
    >
      <View className="flex-1 gap-0.5">
        <View className="flex-row items-center gap-2">
          <Text variant="bodyStrong">{plan.label}</Text>
          {!!plan.note && (
            <View className="rounded-full bg-gold/20 px-2 py-0.5">
              <Text className="font-semibold text-[11px] leading-[15px] text-gold">
                {plan.note}
              </Text>
            </View>
          )}
        </View>
        <Text variant="caption">Billed every {plan.period}</Text>
      </View>

      {/* The price as a numeral, tabular, so three stacked plans line up on the decimal
          instead of drifting by a digit. */}
      <Text
        variant="numeral"
        className={selected ? 'text-[18px] leading-[24px] text-gold' : 'text-[18px] leading-[24px] text-content'}
      >
        {plan.price}
      </Text>
    </Pressable>
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
    <View className="pt-2">
      <Card>
        <Text variant="heading">You are a member</Text>
        <Text variant="body" className="mt-1 text-muted">
          {renews ? `Your membership runs until ${renews}.` : 'Your membership is active.'}
        </Text>
        {/* Points just below rather than at the Market, which is where these used to be
            equipped and no longer is. A stale instruction on a paid screen is worse than
            none: it sends a member somewhere their items are not. */}
        <Text variant="caption" className="mt-4">
          The Gold frame and banner are yours — put them on below.
        </Text>
      </Card>
    </View>
  );
}
