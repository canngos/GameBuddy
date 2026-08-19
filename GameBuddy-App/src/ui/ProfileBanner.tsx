import { Image } from "expo-image";
import { View } from "react-native";
import { useThemeColors } from "../theme";
import { GradientView } from "./Gradient";
import { useScreenScale } from "./useScreenScale";

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
 * <p>When there is no banner this renders the house gradient rather than nothing at all.
 * Collapsing the header would mean the layout jumps the moment someone equips one, and it
 * keeps the avatar's overlap consistent between a decorated profile and a bare one. The
 * gradient is the `primary` one rather than `action`: this is a surface, nothing small and
 * white sits on it, and it is the one place on the profile where the cyan end gets to
 * show.
 */
export function ProfileBanner({
  source,
}: {
  source: string | null | undefined;
}) {
  const colors = useThemeColors();
  const { pick } = useScreenScale();

  /*
   * These two have to move together, and the second one is not free to be chosen.
   *
   * The height is a judgement — 96dp of decoration is a seventh of a 640dp phone, and the
   * header below it (avatar, name, three stats) already took half the screen there.
   *
   * The margins are arithmetic: they exist to cancel `Card`'s padding exactly, and `Card`'s
   * padding is now itself per-tier. Leave them at `-mx-5` and on a small phone the banner
   * overhangs a 14dp padding by 6dp on each side and bleeds past the card's rounded corner.
   * Each entry here is the negative of the `Card` padding for the same tier; if that table
   * changes, this one changes with it.
   */
  const height = pick(64, 80, 96);
  const cancelPadding = pick("-mx-3.5 -mt-3.5", "-mx-4 -mt-4", "-mx-5 -mt-5");

  return (
    <View
      // The bottom margin is deliberately not cancelled — the avatar overlaps into it.
      className={`${cancelPadding} overflow-hidden rounded-t-card`}
      // Kept under the gradient as the colour a failed image decode falls back to.
      style={{ backgroundColor: colors.raised, height }}
    >
      <GradientView
        name="primary"
        direction="diagonal"
        className="absolute inset-0"
        pointerEvents="none"
      />

      {source ? (
        <Image
          source={{ uri: source }}
          style={{ width: "100%", height: "100%" }}
          // The slice shown is shorter than the art's 3:1, so this crops rather than
          // squashes. Anything else would distort every banner in the set.
          contentFit="cover"
          transition={150}
          cachePolicy="memory-disk"
          recyclingKey={source}
        />
      ) : null}
    </View>
  );
}

/**
 * The rest of the profile header, sized against the banner above.
 *
 * Both profile screens — your own and another gamer's — build the identical header: banner,
 * an avatar pulled up over its lower edge, name and stats. The overlap is the reason these
 * numbers live here rather than in either screen: it is measured *against the banner*, so a
 * shorter banner and a deeper pull would put the avatar through the top of the card. Two
 * screens each holding their own copy of that relationship is how they drift apart.
 *
 * The overlap stays half the avatar on every tier, which is what keeps the header looking
 * like the same design rather than a smaller one with the avatar sitting lower.
 */
export function useProfileHeaderLayout() {
  const { pick } = useScreenScale();

  return {
    /** Diameter of the header avatar. */
    avatar: pick(56, 64, 72),
    /** Pull up over the banner's lower edge — half the avatar, per tier. */
    overlap: pick("-mt-7", "-mt-8", "-mt-9"),
    /** Space between the header's rows inside the card. */
    cardGap: pick("gap-4", "gap-4", "gap-5"),
  };
}
