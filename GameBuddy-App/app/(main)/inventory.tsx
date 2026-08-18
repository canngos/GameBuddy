import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Shirt } from 'lucide-react-native';
import { memo, useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import { cosmeticsApi } from '../../src/api/cosmetics';
import type { Cosmetic, CosmeticStore } from '../../src/api/types';
import { useT } from '../../src/i18n/useT';
import { useThemeColors } from '../../src/theme';
import {
  BackHeader,
  Card,
  EmptyState,
  ErrorNotice,
  Screen,
  Segment,
  SegmentRow,
  Text,
  feedback,
} from '../../src/ui';

const STORE_KEY = ['cosmetics'];

/** The image fills its already-sized box. A constant, so it is not a new prop per row. */
const FILL = StyleSheet.create({ fill: { width: '100%', height: '100%' } }).fill;

/**
 * Everything this account owns, and the only place any of it is put on.
 *
 * **Why this exists.** The Market was doing two jobs with one list: selling cosmetics and
 * managing them. A row there could say Buy or Equip depending on a flag, which meant the
 * same control in the same place did opposite things — one spends coins, the other changes
 * how you look — and the things somebody already owned were scattered between the things
 * they did not, sorted by price rather than by whether they were theirs. Buying and wearing
 * are different intents on different days. The Market is now a shop; this is the wardrobe.
 *
 * Owned is the server's flag, not a re-derivation from price. The Gold frame and banner are
 * owned for as long as a membership lasts, and the free frame is owned only once it has been
 * claimed — neither can be worked out from what they cost. A brand new account therefore
 * sees the empty state until it claims something, which is why that state exists.
 */
export default function Inventory() {
  const colors = useThemeColors();
  const router = useRouter();
  const t = useT();
  const queryClient = useQueryClient();

  const [kind, setKind] = useState<'FRAME' | 'BANNER'>('FRAME');

  const store = useQuery({ queryKey: STORE_KEY, queryFn: cosmeticsApi.store });

  /**
   * Equip and unequip both answer with the whole refreshed store, so the response is written
   * straight into the cache rather than triggering a refetch — the same contract the Market
   * relies on. Refetching would leave a window where an item was worn and still offering an
   * Equip button.
   *
   * The profile is invalidated rather than written, because what changed there is the worn
   * frame and it is not on screen to flicker.
   */
  const applyStore = (next: CosmeticStore) => {
    queryClient.setQueryData(STORE_KEY, next);
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };

  const equip = useMutation({
    mutationFn: cosmeticsApi.equip,
    onSuccess: (next: CosmeticStore) => {
      applyStore(next);
      // A buzz and nothing more. Putting a frame on is an adjustment you make several times
      // in a row while deciding, not an event — a cue for each one would be a rattle.
      feedback.tapLight();
    },
  });

  const unequip = useMutation({ mutationFn: cosmeticsApi.unequip, onSuccess: applyStore });

  const busy = equip.isPending || unequip.isPending;
  const failure = equip.error ?? unequip.error;

  const all = kind === 'FRAME' ? (store.data?.frames ?? []) : (store.data?.banners ?? []);
  const owned = useMemo(() => all.filter((item) => item.owned), [all]);
  const wearingOne = all.some((item) => item.equipped);

  // Stable, so the memoised rows can bail out rather than rebuilding the whole wardrobe
  // every time anything on this screen moves.
  const onEquip = useCallback((id: string) => equip.mutate(id), [equip]);

  return (
    <Screen scroll edges={['top']}>
      <BackHeader title={t.market.inventory.title} subtitle={t.market.inventory.subtitle} />

      <SegmentRow>
        <Segment
          label={t.market.shop.frames}
          active={kind === 'FRAME'}
          onPress={() => setKind('FRAME')}
        />
        <Segment
          label={t.market.shop.banners}
          active={kind === 'BANNER'}
          onPress={() => setKind('BANNER')}
        />
      </SegmentRow>

      <View className="pt-5">
        {store.isPending && <ActivityIndicator color={colors.primary} />}
        {store.error && <ErrorNotice error={store.error} onRetry={() => store.refetch()} />}

        {failure && (
          <View className="pb-3">
            <ErrorNotice error={failure} />
          </View>
        )}

        {/* Conditionally *mounted*, never conditionally classed — swapping which class keys
            exist between renders is the defect `src/ui/hairline.ts` documents. */}
        {!store.isPending && !store.error && owned.length === 0 && (
          <EmptyState
            icon={Shirt}
            title={
              kind === 'FRAME' ? t.market.inventory.noFramesYet : t.market.inventory.noBannersYet
            }
            blurb={
              kind === 'FRAME'
                ? t.market.inventory.emptyBlurbFrames
                : t.market.inventory.emptyBlurbBanners
            }
          >
            <Pressable
              onPress={() => router.push('/market')}
              accessibilityRole="button"
              className="rounded-full bg-primary px-5 py-3 active:opacity-70"
            >
              <Text variant="label" className="text-white">
                {t.market.inventory.goToMarket}
              </Text>
            </Pressable>
          </EmptyState>
        )}

        {owned.length > 0 && (
          <View className="gap-3 pb-8">
            {owned.map((item) => (
              <Row key={item.id} item={item} busy={busy} onEquip={onEquip} />
            ))}

            {/* Last, not first: taking something off is the rarest thing done here, and a
                destructive-looking control above the list would read as the point of the
                screen. Shown only when there is something on.

                Unequips by *kind* rather than by id — "take off my frame" is what somebody
                means, and it does not require this screen to know which one is currently on.
                That is also what lets it reach an item this list cannot show. */}
            {wearingOne && (
              <Pressable
                disabled={busy}
                onPress={() => unequip.mutate(kind)}
                accessibilityRole="button"
                className="items-center rounded-card py-3 active:opacity-70"
              >
                <Text variant="label" className="text-muted">
                  {kind === 'FRAME'
                    ? t.market.inventory.takeOffFrame
                    : t.market.inventory.takeOffBanner}
                </Text>
              </Pressable>
            )}
          </View>
        )}
      </View>
    </Screen>
  );
}

/**
 * One owned item.
 *
 * Two states only, because everything here is already owned: worn says so and offers
 * nothing, and anything else offers Equip. There is no price and no Buy — that is the
 * Market's half of the split this screen exists to make.
 */
const Row = memo(function Row({
  item,
  busy,
  onEquip,
}: {
  item: Cosmetic;
  busy: boolean;
  onEquip: (id: string) => void;
}) {
  const t = useT();
  const isBanner = item.kind === 'BANNER';
  const equipThis = useCallback(() => onEquip(item.id), [onEquip, item.id]);

  return (
    <Card>
      <View className="flex-row items-center gap-4">
        {/* A frame is shown against a plain disc so the ring reads as a ring rather than as
            a picture with a hole punched in it. */}
        <View
          className={
            isBanner
              ? 'h-16 w-24 overflow-hidden rounded-xl bg-raised'
              : 'h-16 w-16 items-center justify-center rounded-full bg-raised'
          }
        >
          <Image
            source={{ uri: item.image }}
            style={FILL}
            contentFit={isBanner ? 'cover' : 'contain'}
            autoplay
            transition={150}
            // See the note on the same image in `market.tsx`: animated WebP is the most
            // expensive thing this app decodes, and it is the same catalogue every visit.
            cachePolicy="memory-disk"
            recyclingKey={item.id}
          />
        </View>

        <View className="min-w-0 flex-1 gap-0.5">
          <Text variant="bodyStrong" numberOfLines={1}>
            {item.name}
          </Text>
          {/* How it was come by, which is the only thing left worth saying about something
              already owned. A membership item stops being yours when the membership does,
              and that is worth knowing before you get attached to it. */}
          <Text variant="caption" numberOfLines={1} className={item.membershipOnly ? 'text-gold' : ''}>
            {item.membershipOnly
              ? t.market.gold.withGold
              : isBanner
                ? t.market.gold.banner
                : t.market.gold.frame}
          </Text>
        </View>

        {item.equipped ? (
          <Text variant="label" className="shrink-0 text-primary">
            {t.market.gold.worn}
          </Text>
        ) : (
          <Pressable
            disabled={busy}
            onPress={equipThis}
            accessibilityRole="button"
            accessibilityLabel={t.market.gold.equipA11y(item.name)}
            accessibilityState={{ disabled: busy }}
            className="shrink-0 rounded-full border-2 border-primary bg-transparent px-4 py-2 active:opacity-70"
          >
            <Text variant="label" className="text-primary">
              {t.market.gold.equip}
            </Text>
          </Pressable>
        )}
      </View>
    </Card>
  );
});
