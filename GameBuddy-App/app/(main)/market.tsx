import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { cosmeticsApi } from '../../src/api/cosmetics';
import type { Cosmetic, CosmeticStore } from '../../src/api/types';
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
    queryClient.setQueryData(STORE_KEY, next);
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };

  const onError = (error: unknown) => setFailure(messageOf(error));

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
    <Screen scroll edges={['top']}>
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
