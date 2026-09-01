import { Image } from 'expo-image';
import { Modal, Pressable, View } from 'react-native';
import type { Cosmetic } from '../api/types';
import { useT } from '../i18n/useT';
import { Button, FramedAvatar, GradientView, Text, useScreenScale } from '../ui';
import { useGamerGradient } from '../theme';
import { ThemedCardFrame } from '../ui/ThemedCardFrame';

type CosmeticPreviewProps = {
  /** The item being previewed, or null when the sheet is closed. */
  item: Cosmetic | null;
  /** The shopper's own avatar, so a frame is judged on the face it will actually ring. */
  avatar: string | null | undefined;
  username: string | null | undefined;
  userId: string | null | undefined;
  /** The frame currently worn, drawn on the avatar in the banner preview. */
  wornFrame: string | null | undefined;
  /** False draws the buy control disabled, exactly as the shelf row does. */
  affordable: boolean;
  busy: boolean;
  onBuy: (id: string) => void;
  onClose: () => void;
};

/**
 * How an item will actually look, before any coins are spent.
 *
 * **Why the shelf needed this.** A 64dp thumbnail is enough to tell two frames apart and not
 * nearly enough to decide between them: a ring is bought to sit around one particular face,
 * and the shelf shows it around a grey disc. Banners are worse — the row crops them to the
 * middle half of their width, so the thing being sold is largely off-screen at the moment of
 * choosing. Both are answered the same way: show the item at a size worth looking at, on the
 * gamer's own picture, in the shape the profile will give it.
 *
 * **Only for items not yet owned.** Once something is bought there is nothing left to decide
 * and the Inventory is where it goes on, so an owned row keeps its plain, unpressable shape
 * rather than offering a preview of a thing already sitting in the wardrobe.
 *
 * **A `Modal`, unlike every other overlay in this app.** The house pattern is an absolutely
 * positioned sibling — see `ConfirmDialog` — because Reanimated's entering animations do not
 * run inside a `Modal` on Android. That pattern cannot be used here: those sheets have to be
 * mounted outside a ScrollView, and the Market *is* a scrolling screen, so an absolute
 * sibling would scroll away with the shelf that opened it. A `Modal` renders in its own
 * window and is therefore immune to where it was mounted. Nothing here animates with
 * Reanimated, so the reason to avoid it does not apply. `LanguagePicker` makes the same
 * trade for the same reason.
 */
export function CosmeticPreview({
  item,
  avatar,
  username,
  userId,
  wornFrame,
  affordable,
  busy,
  onBuy,
  onClose,
}: CosmeticPreviewProps) {
  const t = useT();
  const { pick } = useScreenScale();
  // The card underneath keeps its own identity colour; the theme is the edge on top.
  const identityStops = useGamerGradient(userId, null);

  // Hooks first: `visible` is what closes the Modal, so this can return null only after
  // every hook above has run.
  if (!item) return null;

  const isBanner = item.kind === 'BANNER';
  const isTheme = item.kind === 'THEME';
  const free = item.price === 0;

  /*
   * Sized against the screen tier like the rest of the app. The avatar is deliberately far
   * larger than anywhere it actually appears (72dp at most, on the profile header) — this is
   * the one screen whose whole job is to be looked at closely, and a frame's detail is the
   * thing being sold.
   */
  const avatarSize = pick(132, 152, 172);
  const bannerHeight = pick(104, 120, 136);

  const label = `${item.name}${item.animated ? t.market.shop.animatedSuffix : ''}`;

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      {/* Tapping the scrim closes, which is what everyone tries first. */}
      <Pressable
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel={t.common.close}
        className="flex-1 items-center justify-center bg-black/60 px-6"
      >
        {/* Swallows taps inside the card so a stray press on the artwork does not dismiss
            the very thing it was opened to show. */}
        <Pressable
          onPress={() => {}}
          className="w-full items-center gap-4 rounded-card bg-elevated p-5"
        >
          <Text variant="overline" className="text-muted">
            {t.market.shop.previewOn[item.kind]}
          </Text>

          {isTheme ? (
            /*
             * A deck card in miniature, wearing the edge. The theme is a border around the
             * card — see `ThemedCardFrame` — so a preview that filled a box with the
             * colours would be showing the paint rather than the product, which is exactly
             * the mistake the first version of this feature made.
             */
            <View className="w-full items-center py-2">
              {/* The sized box is the positioning context: `ThemedCardFrame` draws just
                  outside its parent's bounds, so its parent has to be the card's shape and
                  must not clip. The clipped card sits inside it. */}
              <View style={{ width: pick(150, 168, 186), height: pick(190, 212, 234) }}>
                <ThemedCardFrame theme={item.theme} radius={24} />
                <View className="flex-1 overflow-hidden rounded-card bg-surface">
                  <GradientView
                    colors={identityStops}
                    direction="diagonal"
                    className="absolute inset-0"
                  />
                  <GradientView
                    colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.55)']}
                    direction="vertical"
                    className="absolute inset-0"
                  />
                  <View className="flex-1 items-center justify-end gap-1 pb-4">
                    <FramedAvatar
                      frame={wornFrame}
                      source={avatar}
                      name={username}
                      colorSeed={userId ?? undefined}
                      size={pick(48, 54, 60)}
                    />
                    <Text variant="bodyStrong" numberOfLines={1} className="text-white">
                      {username ?? ''}
                    </Text>
                  </View>
                </View>
              </View>
            </View>
          ) : isBanner ? (
            /*
             * The profile header, in miniature: full-bleed art with the avatar pulled up over
             * its lower-left edge. Showing the raw 3:1 file instead would be a bigger picture
             * of something the gamer will never see — the header crops the top and bottom off
             * and puts a face in the corner, and that is the composition being bought.
             */
            <View className="w-full">
              <View
                className="w-full overflow-hidden rounded-xl bg-raised"
                style={{ height: bannerHeight }}
              >
                <Image
                  source={{ uri: item.image ?? undefined }}
                  style={{ width: '100%', height: '100%' }}
                  contentFit="cover"
                  transition={150}
                  cachePolicy="memory-disk"
                  recyclingKey={item.id}
                  accessibilityLabel={label}
                />
              </View>

              {/* Outside the clipping box above, because a frame overhangs its avatar and an
                  `overflow-hidden` parent would cut the ring — see `FramedAvatar`. */}
              <View className="-mt-7 flex-row items-end gap-3 pl-1">
                <FramedAvatar
                  frame={wornFrame}
                  source={avatar}
                  name={username}
                  colorSeed={userId ?? undefined}
                  size={pick(56, 64, 72)}
                />
                <View className="min-w-0 flex-1 pb-1">
                  <Text variant="bodyStrong" numberOfLines={1}>
                    {username ?? ''}
                  </Text>
                </View>
              </View>
            </View>
          ) : (
            <View className="items-center py-2">
              <FramedAvatar
                frame={item.image}
                source={avatar}
                name={username}
                colorSeed={userId ?? undefined}
                size={avatarSize}
              />
            </View>
          )}

          <View className="w-full items-center gap-1">
            <Text variant="heading" numberOfLines={1}>
              {item.name}
            </Text>
            <Text
              variant="caption"
              className={free ? 'text-muted' : 'font-medium text-gold'}
            >
              {free ? t.market.shop.free : t.market.shop.coinsPrice(item.price)}
            </Text>
          </View>

          {/* The same three states the shelf row offers, so a decision made here does not
              have to be carried back to a list to be acted on. */}
          <View className="w-full flex-row gap-2 pt-1">
            <View className="flex-1">
              <Button label={t.common.close} variant="secondary" size="md" onPress={onClose} />
            </View>
            <View className="flex-1">
              <Button
                label={
                  free
                    ? t.market.shop.claim
                    : affordable
                      ? t.market.shop.buy
                      : t.market.shop.notEnoughCoins
                }
                variant="primary"
                size="md"
                disabled={busy || !affordable}
                onPress={() => onBuy(item.id)}
              />
            </View>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
