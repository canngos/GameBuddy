import { Image } from 'expo-image';
import { Modal, Pressable, View } from 'react-native';
import type { Bundle } from '../api/types';
import { useT } from '../i18n/useT';
import { Button, FramedAvatar, Text, useScreenScale } from '../ui';

type BundlePreviewProps = {
  bundle: Bundle | null;
  avatar: string | null | undefined;
  username: string | null | undefined;
  userId: string | null | undefined;
  affordable: boolean;
  busy: boolean;
  onBuy: (bundleId: string) => void;
  onClose: () => void;
};

/**
 * A set, worn together.
 *
 * <p><strong>Why a set needs its own close-up.</strong> The single-item preview answers
 * "what does this look like on me". A bundle asks a different question — *why these two* —
 * and the only honest answer is to put them on at the same time: the banner behind the
 * header with the frame around the avatar in front of it, which is exactly where a buyer
 * will see them. Two separate previews, one after the other, would show both pieces and
 * none of the reason they are sold together.
 *
 * <p>A `Modal` for the same reason {@link CosmeticPreview} is one: the Market scrolls, and
 * the app's usual positioned-sibling sheets have to live outside a ScrollView.
 */
export function BundlePreview({
  bundle,
  avatar,
  username,
  userId,
  affordable,
  busy,
  onBuy,
  onClose,
}: BundlePreviewProps) {
  const t = useT();
  const { pick } = useScreenScale();

  const bannerHeight = pick(104, 120, 136);
  const avatarSize = pick(64, 72, 80);

  if (!bundle) return null;

  const banner = bundle.items.find((item) => item.kind === 'BANNER') ?? null;
  const frame = bundle.items.find((item) => item.kind === 'FRAME') ?? null;
  const saving = bundle.partsPrice - bundle.price;

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      <Pressable
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel={t.common.close}
        className="flex-1 items-center justify-center bg-black/60 px-6"
      >
        <Pressable
          onPress={() => {}}
          className="w-full items-center gap-4 rounded-card bg-elevated p-5"
        >
          <Text variant="overline" className="text-muted">
            {t.market.shop.bundleTogether}
          </Text>

          {/* The profile header the set produces, in miniature. */}
          <View className="w-full">
            <View
              className="w-full overflow-hidden rounded-xl bg-raised"
              style={{ height: bannerHeight }}
            >
              {banner ? (
                <Image
                  source={{ uri: banner.image ?? undefined }}
                  style={{ width: '100%', height: '100%' }}
                  contentFit="cover"
                  autoplay
                  transition={150}
                  cachePolicy="memory-disk"
                  recyclingKey={banner.id}
                />
              ) : null}
            </View>

            {/* Outside the clipping box, because a frame overhangs its avatar and an
                `overflow-hidden` parent would cut the ring — see `FramedAvatar`. */}
            <View className="-mt-8 flex-row items-end gap-3 pl-1">
              <FramedAvatar
                frame={frame?.image}
                source={avatar}
                name={username}
                colorSeed={userId ?? undefined}
                size={avatarSize}
              />
              <View className="min-w-0 flex-1 pb-1">
                <Text variant="bodyStrong" numberOfLines={1}>
                  {username ?? ''}
                </Text>
              </View>
            </View>
          </View>

          {/* What is in it, named — the header above shows them worn but does not say
              which items they are, and both are also for sale separately. */}
          <View className="w-full gap-1">
            <Text variant="heading" numberOfLines={1}>
              {bundle.name}
            </Text>
            <Text variant="caption" className="text-muted">
              {bundle.items.map((item) => item.name).join(' · ')}
            </Text>
            <View className="flex-row items-baseline gap-2 pt-1">
              <Text variant="bodyStrong" className="text-gold">
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

          <View className="w-full flex-row gap-2 pt-1">
            <View className="flex-1">
              <Button label={t.common.close} variant="secondary" size="md" onPress={onClose} />
            </View>
            <View className="flex-1">
              <Button
                label={affordable ? t.market.shop.buy : t.market.shop.notEnoughCoins}
                variant="primary"
                size="md"
                disabled={busy || !affordable}
                onPress={() => onBuy(bundle.id)}
              />
            </View>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
