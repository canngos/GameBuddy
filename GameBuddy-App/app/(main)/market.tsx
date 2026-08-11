import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { billingApi } from '../../src/api/billing';
import { cosmeticsApi } from '../../src/api/cosmetics';
import { ApiError, Code } from '../../src/api/envelope';
import type { Cosmetic, CosmeticStore } from '../../src/api/types';
import { CoinShop } from '../../src/market/CoinShop';
import { EarnCoins } from '../../src/market/EarnCoins';
import { useThemeColors } from '../../src/theme';
import { Card, ErrorNotice, Screen, Text, messageOf } from '../../src/ui';

const STORE_KEY = ['cosmetics'];

/**
 * The Market: frames and banners, bought with earned coins.
 *
 * Selling avatars was the original plan and it was the wrong one — once gamers can
 * upload their own picture, a stock image everyone else can also buy is a weak thing to
 * charge for. A frame composes with whatever photo someone already chose, so buying one
 * never asks anybody to give up their own face.
 */
export default function Market() {
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<'FRAME' | 'BANNER'>('FRAME');
  const [failure, setFailure] = useState<string | null>(null);
  /** Set when a purchase was refused for want of coins, which has its own way out. */
  const [shortOfCoins, setShortOfCoins] = useState(false);

  const scrollRef = useRef<ScrollView>(null);
  const coinShopY = useRef(0);

  const store = useQuery({ queryKey: STORE_KEY, queryFn: cosmeticsApi.store });

  /**
   * Buy, equip and unequip all answer with the whole refreshed store, so the response
   * is written straight into the cache instead of triggering a refetch. Refetching
   * would leave a window where the balance had dropped but the item still showed as
   * buyable — the shelf disagreeing with itself for a frame or two.
   *
   * The profile is invalidated rather than written, because what changed there is the
   * worn frame and the coin count, and it is not on screen to flicker.
   */
  const applyStore = (next: CosmeticStore) => {
    setFailure(null);
    setShortOfCoins(false);
    queryClient.setQueryData(STORE_KEY, next);
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };

  /**
   * Running out of coins is not the same kind of event as a request failing.
   *
   * It gets its own state and its own copy, because it is the one failure here with an
   * obvious remedy — and because the remedy must stay the gamer's to choose. Opening a
   * store sheet on somebody who has just been told "no" turns a refusal into a sales
   * pitch at the exact moment they are least receptive to one; this offers a button that
   * scrolls to the packs, and that is all.
   */
  const onError = (error: unknown) => {
    if (error instanceof ApiError && error.is(Code.COIN_NOT_ENOUGH)) {
      setShortOfCoins(true);
      setFailure(null);
      return;
    }
    setShortOfCoins(false);
    setFailure(messageOf(error));
  };

  const showCoinPacks = () => {
    setShortOfCoins(false);
    // -24 so the "COINS" heading is not flush against the top edge on arrival.
    scrollRef.current?.scrollTo({ y: Math.max(0, coinShopY.current - 24), animated: true });
  };

  const buy = useMutation({ mutationFn: cosmeticsApi.buy, onSuccess: applyStore, onError });
  const equip = useMutation({ mutationFn: cosmeticsApi.equip, onSuccess: applyStore, onError });
  const unequip = useMutation({
    mutationFn: cosmeticsApi.unequip,
    onSuccess: applyStore,
    onError,
  });

  const busy = buy.isPending || equip.isPending || unequip.isPending;
  const items = kind === 'FRAME' ? (store.data?.frames ?? []) : (store.data?.banners ?? []);
  const wearingOne = items.some((item) => item.equipped);

  return (
    <Screen scroll edges={['top']} scrollRef={scrollRef}>
      <View className="flex-row items-end justify-between pb-5 pt-8">
        <View className="gap-1">
          <Text variant="overline">MARKET</Text>
          <Text variant="title">Show off</Text>
        </View>
        {/* The balance goes in the header rather than beside each price: the one
            question a gamer has on this screen is what they can afford. */}
        <View className="items-end">
          <Text className="font-bold text-[22px] leading-[28px] text-brand">
            {store.data?.coins ?? 0}
          </Text>
          <Text variant="caption">coins</Text>
        </View>
      </View>

      <GoldCard />

      <View className="flex-row gap-2 pb-5">
        <Segment label="Frames" active={kind === 'FRAME'} onPress={() => setKind('FRAME')} />
        <Segment label="Banners" active={kind === 'BANNER'} onPress={() => setKind('BANNER')} />
      </View>

      {store.isPending && <ActivityIndicator color={colors.brand} />}
      {store.error && <ErrorNotice error={store.error} onRetry={() => store.refetch()} />}

      {failure && (
        <Card className="mb-3">
          <Text variant="body" className="text-danger">
            {failure}
          </Text>
        </Card>
      )}

      {shortOfCoins && (
        <Card className="mb-3">
          <Text variant="bodyStrong">Not enough coins</Text>
          <Text variant="caption" className="mt-1">
            You have {store.data?.coins ?? 0}. Badges earn coins, or you can top up.
          </Text>
          <Pressable
            onPress={showCoinPacks}
            accessibilityRole="button"
            className="mt-3 self-start rounded-full bg-brand/15 px-4 py-2 active:opacity-70"
          >
            <Text variant="label" className="text-brand">
              See coin packs
            </Text>
          </Pressable>
        </Card>
      )}

      <View className="gap-3 pb-8">
        {items.map((item) => (
          <Row
            key={item.id}
            item={item}
            busy={busy}
            onBuy={() => buy.mutate(item.id)}
            onEquip={() => equip.mutate(item.id)}
          />
        ))}

        {/* Last, not first: taking something off is the rarest thing done here, and a
            destructive-looking control above the shelf would read as the point of the
            screen. Shown only when there is something on. */}
        {wearingOne && (
          <Pressable
            disabled={busy}
            onPress={() => unequip.mutate(kind)}
            accessibilityRole="button"
            className="items-center rounded-card py-3 active:opacity-70"
          >
            <Text variant="label" className="text-muted">
              Take off my {kind === 'FRAME' ? 'frame' : 'banner'}
            </Text>
          </Pressable>
        )}
      </View>

      {/* Earning comes before buying, and both come after the shelf. Somebody who has not
          yet seen a frame they want has no reason for either — putting the till before the
          goods is what makes a cosmetics shop feel like a slot machine. */}
      <EarnCoins />

      <CoinShop
        balance={store.data?.coins ?? 0}
        onLayoutY={(y) => {
          coinShopY.current = y;
        }}
      />
    </Screen>
  );
}

function Segment({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      // Both branches carry the same class keys and only the values move. A class that
      // appears on one state and not the other stops NativeWind painting the subtree —
      // see `src/ui/hairline.ts`, which is the same defect twice over.
      className={
        active
          ? 'flex-1 items-center rounded-full bg-brand py-2.5'
          : 'flex-1 items-center rounded-full bg-raised py-2.5'
      }
    >
      <Text variant="label" className={active ? 'text-white' : 'text-muted'}>
        {label}
      </Text>
    </Pressable>
  );
}

/**
 * One item on the shelf.
 *
 * Three states, and they are the whole interaction: not owned shows a price and Buy,
 * owned shows Equip, worn shows a label rather than a button — there is nothing left to
 * do to something already on.
 *
 * `owned` comes from the server and is not re-derived from `price`, because a free
 * cosmetic is owned by everyone without any purchase existing. Deciding that here as
 * well would be the same rule in two places, and the free frames — the only ones a
 * gamer with no coins can wear — are exactly what a disagreement would lock.
 */
function Row({
  item,
  busy,
  onBuy,
  onEquip,
}: {
  item: Cosmetic;
  busy: boolean;
  onBuy: () => void;
  onEquip: () => void;
}) {
  const isBanner = item.kind === 'BANNER';

  return (
    <Card>
      <View className="flex-row items-center gap-4">
        {/* A frame is shown against a plain disc so the ring reads as a ring rather
            than as a picture with a hole punched in it. */}
        <View
          className={
            isBanner
              ? 'h-16 w-24 overflow-hidden rounded-xl bg-raised'
              : 'h-16 w-16 items-center justify-center rounded-full bg-raised'
          }
        >
          <Image
            source={{ uri: item.image }}
            style={{ width: '100%', height: '100%' }}
            contentFit={isBanner ? 'cover' : 'contain'}
            autoplay
            transition={150}
          />
        </View>

        <View className="flex-1 gap-0.5">
          <Text variant="bodyStrong">{item.name}</Text>
          <Text variant="caption">
            {[item.animated ? 'Animated' : null, item.price === 0 ? 'Free' : `${item.price} coins`]
              .filter(Boolean)
              .join(' · ')}
          </Text>
        </View>

        {item.equipped ? (
          <Text variant="label" className="text-brand">
            Worn
          </Text>
        ) : (
          <Pressable
            disabled={busy}
            onPress={item.owned ? onEquip : onBuy}
            accessibilityRole="button"
            accessibilityLabel={`${item.owned ? 'Equip' : 'Buy'} ${item.name}`}
            className={
              item.owned
                ? 'rounded-full border-2 border-brand px-4 py-2 active:opacity-70'
                : 'rounded-full bg-brand px-4 py-2 active:opacity-70'
            }
          >
            <Text variant="label" className={item.owned ? 'text-brand' : 'text-white'}>
              {item.owned ? 'Equip' : 'Buy'}
            </Text>
          </Pressable>
        )}
      </View>
    </Card>
  );
}


/**
 * The membership, at the top of the Market.
 *
 * One of the four places the paywall is reachable from, and the least urgent of them —
 * somebody on this screen is already thinking about how their profile looks, which is
 * the mood Gold's cosmetics speak to. The pressing ones are the limit sheet and the
 * admirers screen, where the thing Gold removes is the thing currently in the way.
 *
 * Shows status rather than a pitch once somebody has subscribed. A paywall that keeps
 * selling to an existing member reads as a system that does not know who they are.
 */
function GoldCard() {
  const router = useRouter();
  const subscription = useQuery({
    queryKey: ['subscription'],
    queryFn: billingApi.subscription,
  });

  const isGold = subscription.data?.tier === 'GOLD';

  return (
    <Pressable
      onPress={() => router.push('/gold')}
      accessibilityRole="button"
      accessibilityLabel={isGold ? 'Your Gold membership' : 'Get GameBuddy Gold'}
      className="mb-5 active:opacity-80"
    >
      <Card>
        <View className="flex-row items-center justify-between gap-4">
          <View className="flex-1 gap-1">
            <Text variant="overline" className="text-brand">
              GAMEBUDDY GOLD
            </Text>
            <Text variant="heading">{isGold ? 'You are a member' : 'See who likes you'}</Text>
            <Text variant="caption">
              {isGold
                ? 'The Gold frame and banner are yours while your membership lasts.'
                : 'No daily limit, advanced filters, and the Gold frame and banner.'}
            </Text>
          </View>
          <View className="h-2 w-2 rotate-45 border-r-2 border-t-2 border-muted" />
        </View>
      </Card>
    </Pressable>
  );
}
