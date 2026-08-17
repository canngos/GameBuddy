import { Image } from 'expo-image';
import { memo, useMemo } from 'react';
import { View } from 'react-native';
import { avatarColor, avatarGradient, avatarUri, initialsOf } from '../avatars';
import { useThemeColors } from '../theme';
import { cn } from './cn';
import { GradientView } from './Gradient';
import { Text } from './Text';

type AvatarProps = {
  /** The backend's `avatar` value: a URL, a bare filename, or null. */
  source: string | null | undefined;
  /** Used for the fallback initials, and for the colour unless `colorSeed` is given. */
  name: string | null | undefined;
  /**
   * What to hash for the fallback colour. Pass a stable id where the displayed name is
   * not distinctive — the avatar picker labels its options "1".."8", and colouring by
   * that would give eight neighbouring hues instead of eight distinguishable ones.
   */
  colorSeed?: string;
  size?: number;
  selected?: boolean;
  className?: string;
};

/**
 * An avatar, or a coloured monogram when there is no image. See `src/avatars.ts` —
 * bare filenames cannot be resolved until the art is hosted, and a grey box on every
 * profile would be much worse than initials.
 */
export const Avatar = memo(function Avatar({
  source,
  name,
  colorSeed,
  size = 56,
  selected = false,
  className,
}: AvatarProps) {
  const colors = useThemeColors();
  const uri = avatarUri(source);
  const seed = colorSeed ?? name ?? source ?? '?';
  // Size is a runtime number, so it stays inline — an arbitrary Tailwind value cannot
  // be built from a variable, since the class list is extracted at build time.
  //
  // Memoised, along with the outer style array below, because this component sits in
  // every row of every list in the app and both objects are props: rebuilding them each
  // render is what stopped the rows above from ever bailing out.
  const frame = useMemo(
    () => ({ width: size, height: size, borderRadius: size / 2 }),
    [size],
  );

  const box = useMemo(
    () => [
      frame,
      {
        borderWidth: selected ? 3 : 0,
        borderColor: selected ? colors.primary : 'transparent',
        backgroundColor: avatarColor(seed),
      },
    ],
    [frame, selected, colors.primary, seed],
  );

  const label = useMemo(
    () => ({ fontSize: size * 0.36, lineHeight: size * 0.44 }),
    [size],
  );

  return (
    <View
      className={cn('items-center justify-center overflow-hidden', className)}
      // The selection ring is a style with a constant key set, not a `selected &&`
      // class, and that is not a stylistic preference. A class that appears and
      // disappears makes NativeWind 4.2.6 stop painting the element's subtree: in the
      // avatar picker, tapping an option blanked it, and tapping it again brought it
      // back. Same defect as the one `src/ui/hairline.ts` documents for `dark:`.
      // Both keys are always present; only the values move.
      //
      // The tint is unconditional too, for the same reason. It is invisible behind an
      // image anyway, and it means a slow-loading avatar shows its own colour rather
      // than a grey box.
      style={box}
    >
      {/*
        The gradient sits on top of that backgroundColor rather than replacing it, and both
        are unconditional. It renders before the image so the image paints over it, which is
        also why it is a sibling and not a wrapper — a slow-loading avatar shows its own
        colour rather than a grey box, and a broken URL degrades to the monogram's backdrop
        instead of to nothing.
      */}
      <GradientView
        colors={avatarGradient(seed)}
        direction="diagonal"
        className="absolute inset-0"
        pointerEvents="none"
      />

      {uri ? (
        <Image
          source={{ uri }}
          style={frame}
          contentFit="cover"
          // The same faces recur constantly — the inbox, the friends list, a lobby
          // roster, the deck — so a disk cache is the difference between paying for
          // somebody's picture once and paying for it on every screen that shows them.
          cachePolicy="memory-disk"
          // Load-bearing now that these lists are virtualized: without it a recycled
          // cell keeps the previous person's face until the new one decodes, which in a
          // list of people is not a glitch so much as a lie.
          recyclingKey={uri}
          // No fade. An avatar is a small element in a dense row, and a transition on
          // each one makes a list look like it is still loading after it has settled.
          transition={0}
        />
      ) : (
        <Text variant="heading" className="text-white" style={label}>
          {initialsOf(name ?? source)}
        </Text>
      )}
    </View>
  );
});
