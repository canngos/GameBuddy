import { Image, View } from 'react-native';
import { avatarColor, avatarUri, initialsOf } from '../avatars';
import { brand } from '../theme';
import { cn } from './cn';
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
export function Avatar({
  source,
  name,
  colorSeed,
  size = 56,
  selected = false,
  className,
}: AvatarProps) {
  const uri = avatarUri(source);
  // Size is a runtime number, so it stays inline — an arbitrary Tailwind value cannot
  // be built from a variable, since the class list is extracted at build time.
  const frame = { width: size, height: size, borderRadius: size / 2 };

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
      style={[
        frame,
        {
          borderWidth: selected ? 3 : 0,
          borderColor: selected ? brand.DEFAULT : 'transparent',
          backgroundColor: avatarColor(colorSeed ?? name ?? source ?? '?'),
        },
      ]}
    >
      {uri ? (
        <Image source={{ uri }} style={frame} resizeMode="cover" />
      ) : (
        <Text
          variant="heading"
          className="text-white"
          style={{ fontSize: size * 0.36, lineHeight: size * 0.44 }}
        >
          {initialsOf(name ?? source)}
        </Text>
      )}
    </View>
  );
}
