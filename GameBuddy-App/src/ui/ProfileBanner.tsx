import { Image } from 'expo-image';
import { View } from 'react-native';
import { useThemeColors } from '../theme';

/**
 * The strip of art behind a profile header.
 *
 * <p>Full-bleed inside a {@link Card}, which is why it cancels the card's padding with
 * negative margins rather than being given a padding-free card of its own: a banner
 * inset by 20px on three sides and clipped on the fourth looks like a mistake.
 *
 * <p>The art is 1200×400, but this deliberately shows a shorter slice than that ratio.
 * A banner is background — it sits behind a name, an avatar and three stats — and giving
 * it a third of the card's height makes the profile about the decoration instead of the
 * person. The banners were drawn without a focal point for the same reason.
 *
 * <p>When there is no banner this renders a plain tinted strip rather than nothing at
 * all. Collapsing the header would mean the layout jumps the moment someone equips one,
 * and it keeps the avatar's overlap consistent between a decorated profile and a bare
 * one.
 */
export function ProfileBanner({ source }: { source: string | null | undefined }) {
  const colors = useThemeColors();

  return (
    <View
      // -m-5 cancels Card's p-5; the bottom margin is left for the avatar to overlap into.
      className="-mx-5 -mt-5 h-[96px] overflow-hidden rounded-t-card"
      style={{ backgroundColor: colors.raised }}
    >
      {source ? (
        <Image
          source={{ uri: source }}
          style={{ width: '100%', height: '100%' }}
          // The slice shown is shorter than the art's 3:1, so this crops rather than
          // squashes. Anything else would distort every banner in the set.
          contentFit="cover"
          transition={150}
        />
      ) : null}
    </View>
  );
}
