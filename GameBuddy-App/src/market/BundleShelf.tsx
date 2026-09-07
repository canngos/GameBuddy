import { Image } from 'expo-image';
import { Pressable, StyleSheet, View } from 'react-native';
import type { Bundle } from '../api/types';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
import { Card, Text, useScreenScale } from '../ui';

const FILL = StyleSheet.create({ fill: { width: '100%', height: '100%' } }).fill;

type BundleShelfProps = {
  bundles: Bundle[];
  balance: number;
  /** True while any purchase on the Market is in flight; all its buttons share one. */
  busy: boolean;
  /**
   * Buys a set. The mutation lives on the Market screen so this shelf and the preview sheet
   * share one — two would mean two toasts for the same purchase.
   */
  onBuy: (bundleId: string) => void;
  /** Opens the close-up. A set is two things at once, so it needs more than a row can show. */
  onPreview: (bundle: Bundle) => void;
};

/**
 * Matching sets, sold for less than their parts.
 *
 * <p><strong>Above the frames and banners, not among them.</strong> A bundle is an offer
 * about several rows further down; putting it in the same list would make the parts look
 * like they were priced twice.
 *
 * <p><strong>Laid out down the card, not across it.</strong> The first version borrowed the
 * shelf row — two thumbnails, then text, then a button, all in one line — and on a 360dp
 * phone that left the text column **32dp wide**: the name, the struck-through parts price
 * and the saving all truncated to nothing, so the one thing a bundle has to communicate
 * (that it is cheaper) was the thing you could not read. A set is simply more information
 * than a row of that shape can hold, so it gets a card instead: artwork, then words at full
 * width, then a full-width control.
 */
export function BundleShelf({ bundles, balance, busy, onBuy, onPreview }: BundleShelfProps) {
  const t = useT();
  const upper = useUpper();
  const { pick } = useScreenScale();

  // An owned set has nothing left to sell, and its parts are on the shelves below either
  // way — so it leaves rather than sitting there permanently disabled.
  const offers = bundles.filter((b) => !b.owned);
  if (offers.length === 0) return null;

  const thumb = pick(64, 72, 80);

  return (
    <>
      {/* No caption: every card already prints what the pieces cost separately and what
          the set saves, which is the whole of what a sentence here could have said. */}
      <View className="pb-3">
        <Text variant="overline">{upper(t.market.shop.bundles)}</Text>
      </View>

      <View className="gap-3 pb-6">
        {offers.map((bundle) => {
          const affordable = bundle.price <= balance;
          const saving = bundle.partsPrice - bundle.price;

          return (
            <Card key={bundle.id}>
              <View className="gap-3">
                {/* The artwork opens the close-up, exactly as a shelf row's does. */}
                <Pressable
                  onPress={() => onPreview(bundle)}
                  accessibilityRole="button"
                  accessibilityLabel={t.market.shop.bundleA11y(bundle.name, bundle.price, saving)}
                  className="flex-row items-center gap-3 active:opacity-70"
                >
                  {bundle.items.map((item) => (
                    <View
                      key={item.id}
                      className={
                        item.kind === 'BANNER'
                          ? 'overflow-hidden rounded-xl bg-raised'
                          : 'items-center justify-center rounded-full bg-raised'
                      }
                      style={
                        item.kind === 'BANNER'
                          ? { height: thumb, width: thumb * 1.5 }
                          : { height: thumb, width: thumb }
                      }
                    >
                      <Image
                        source={{ uri: item.image ?? undefined }}
                        style={FILL}
                        contentFit={item.kind === 'BANNER' ? 'cover' : 'contain'}
                        autoplay
                        transition={150}
                        cachePolicy="memory-disk"
                        recyclingKey={item.id}
                      />
                    </View>
                  ))}
                </Pressable>

                {/* Full width, so none of it truncates. */}
                <View className="gap-0.5">
                  <Text variant="bodyStrong">{bundle.name}</Text>
                  <View className="flex-row items-baseline gap-2">
                    <Text variant="caption" className="font-medium text-gold">
                      {t.market.shop.coinsPrice(bundle.price)}
                    </Text>
                    <Text variant="caption" className="text-muted line-through">
                      {t.market.shop.bundleParts(bundle.partsPrice)}
                    </Text>
                  </View>
                  <Text variant="caption" className="text-success">
                    {t.market.shop.bundleSaving(saving)}
                  </Text>
                </View>

                <Pressable
                  disabled={busy || !affordable}
                  onPress={() => onBuy(bundle.id)}
                  accessibilityRole="button"
                  accessibilityState={{ disabled: busy || !affordable }}
                  // One class-key set across both states, only the values moving — the
                  // appearing-and-disappearing key defect `src/ui/hairline.ts` documents.
                  className={[
                    'items-center rounded-full border-2 px-4 py-2.5 active:opacity-70',
                    affordable ? 'border-transparent bg-primary' : 'border-line bg-transparent',
                  ].join(' ')}
                >
                  <Text variant="label" className={affordable ? 'text-white' : 'text-muted'}>
                    {affordable ? t.market.shop.buy : t.market.shop.notEnoughCoins}
                  </Text>
                </Pressable>
              </View>
            </Card>
          );
        })}
      </View>
    </>
  );
}
