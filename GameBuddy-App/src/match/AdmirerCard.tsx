import { Image } from "expo-image";
import { Star } from "lucide-react-native";
import { memo, useMemo } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import type { Candidate } from "../api/types";
import { avatarColor, avatarUri, initialsOf } from "../avatars";
import { useT } from "../i18n/useT";
import { useThemeColors } from "../theme";
import { FrameOverlay } from "../ui/FramedAvatar";
import { Icon } from "../ui/Icon";
import { Text } from "../ui/Text";
import { cn } from "../ui/cn";
import { useHairline } from "../ui/hairline";

type AdmirerCardProps =
  | { locked: true; candidate?: undefined; onPress?: undefined }
  | { locked?: false; candidate: Candidate; onPress: () => void };

/**
 * One person who liked you, as a tile.
 *
 * Two states from one component, and the union type is what keeps them honest: a locked
 * tile cannot be given a candidate, and an unlocked one cannot be built without one. The
 * server sends an empty `likedYou` array while locked, so there is genuinely nothing to
 * render behind the blur — which is the point. Nothing is being hidden client-side that a
 * determined person could read out of the response.
 */
/**
 * `flex-1` with a third-width cap rather than a `flexBasis`, because the grid is a
 * three-column `FlatList` now: flex splits the row exactly, and the cap is what stops one
 * or two cards on a short last row from stretching across the whole width.
 */
const styles = StyleSheet.create({
  card: { flex: 1, maxWidth: "33.33%" },
  photo: { width: 64, height: 64, borderRadius: 32 },
});

export const AdmirerCard = memo(function AdmirerCard({
  locked,
  candidate,
  onPress,
}: AdmirerCardProps) {
  const t = useT();
  const colors = useThemeColors();
  const hairline = useHairline();

  /*
   * The ring is a style, not a class, and that is forced rather than chosen.
   *
   * `useHairline()` already puts `borderWidth`/`borderColor` in this element's inline
   * `style`, and in NativeWind an inline style beats a class — so `border-2 border-accent`
   * on the className was silently overwritten by the hairline's `borderWidth: 0` in light
   * mode, and the tile rendered with no ring at all while every class involved compiled
   * perfectly. The two have to meet in the same slot to have an argument at all.
   *
   * Replacing the hairline rather than adding to it, for the same reason it exists in that
   * shape: both branches carry the same two keys and only the values differ. A style key
   * that appears on one render and not the next makes the subtree stop painting.
   */
  const superLike = !locked && !!candidate?.superLike;
  const ring = useMemo(
    () => (superLike ? { borderWidth: 2, borderColor: colors.gold } : hairline),
    [superLike, colors.gold, hairline],
  );
  const box = useMemo(() => [styles.card, ring], [ring]);

  if (locked) {
    return (
      <View
        style={box}
        className="aspect-[3/4] items-center justify-center overflow-hidden rounded-card bg-raised"
      >
        {/* Concentric rings rather than a blurred face. A real blur would need a real
            image, and we do not have one — inventing a silhouette would be pretending to
            withhold something we were never sent. This reads as "somebody", which is the
            truth. */}
        <View className="h-12 w-12 rounded-full bg-line opacity-70" />
        <View className="mt-2 h-2 w-12 rounded-full bg-line opacity-50" />
      </View>
    );
  }

  const photo = avatarUri(candidate.avatar);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${candidate.gamerUsername}, ${candidate.age}`}
      style={box}
      className="aspect-[3/4] overflow-hidden rounded-card bg-surface active:opacity-80"
    >
      <View
        className="flex-1 items-center justify-center"
        style={{
          backgroundColor: photo ? undefined : avatarColor(candidate.userId),
        }}
      >
        <View className="h-16 w-16 items-center justify-center">
          {photo ? (
            <Image
              source={{ uri: photo }}
              // A style, not `h-16 w-16 rounded-full` — expo-image is not registered with
              // NativeWind, so a className here would silently resolve to nothing.
              style={styles.photo}
              contentFit="cover"
              cachePolicy="memory-disk"
              recyclingKey={photo}
              transition={0}
            />
          ) : (
            <Text variant="heading" className="text-white">
              {initialsOf(candidate.gamerUsername)}
            </Text>
          )}
          {!!candidate.frame && (
            <FrameOverlay frame={candidate.frame} size={64} />
          )}
        </View>
      </View>

      <View className="px-2 py-1.5">
        <Text
          variant="caption"
          numberOfLines={1}
          className={cn("text-content")}
        >
          {candidate.gamerUsername}
        </Text>
      </View>

      {/* Top-left, over the portrait. A whole element appearing and disappearing is fine —
          it is one element's *class list* that must not change shape, not the tree. The
          same star the deck's Super Like button carries, so the badge reads as the answer
          to an action the gamer already knows how to take. */}
      {candidate.superLike && (
        <View
          className="absolute left-1.5 top-1.5 h-6 w-6 items-center justify-center rounded-full bg-gold"
          accessibilityLabel={t.deck.actions.superLike}
        >
          {/*
            The canvas colour, not white — `gold` is not one colour but two, and they sit
            on opposite sides of the scale. It is #8A5C00 in light mode, dark enough to
            carry a white star at 5.81:1, and #FFC53D in dark mode, where that same white
            star lands at 1.58:1 and effectively disappears. Following the canvas inverts
            the star with the theme and clears 4.5:1 on both (5.44 and 12.43).
          */}
          <Icon
            as={Star}
            size={13}
            color={colors.canvas}
            fill={colors.canvas}
            strokeWidth={2}
          />
        </View>
      )}
    </Pressable>
  );
});
