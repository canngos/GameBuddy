import type { LucideIcon } from 'lucide-react-native';
import { useThemeColors } from '../theme';

/**
 * The one place Lucide is configured.
 *
 * Before this, every glyph in the app was either an emoji (`⚙︎ 🔍 🎮 ⚡ ↺`), a shape
 * assembled from bordered `View`s — the tab bar, the chevrons, the three-dot menu, and a
 * "heart" that was two concentric circles because, per the comment that was in
 * `DeckActions`, a heart is hard to draw without an icon — or one of two hand-drawn SVG
 * sets. Emoji are the worst of those: they render as somebody else's artwork, differ
 * between Android versions, and cannot take a colour.
 *
 * Lucide is not an arbitrary replacement. `src/ui/KeywordIcon.tsx` and
 * `src/profile/PlatformIcon.tsx` were already drawn to a 24×24 viewBox with 2px strokes
 * and round caps, which is Lucide's spec exactly — so this extends an icon language the
 * app already speaks rather than introducing a second one. It draws through the
 * `react-native-svg` that was already installed, which is also why adding it needed no
 * native rebuild.
 *
 * Being real SVG rather than a font matters for this design: `strokeWidth` is what makes a
 * glyph read as neon, and an icon font cannot vary it.
 *
 * Everything goes through here so that swapping the set, or changing the default weight,
 * is one edit instead of two hundred.
 */

/** Semantic colour roles, so call sites do not each reach for `useThemeColors`. */
export type Tone =
  | 'content'
  | 'muted'
  | 'primary'
  | 'accent'
  | 'gold'
  | 'online'
  | 'danger'
  | 'success'
  | 'inverse';

type IconProps = {
  /** The Lucide component itself: `<Icon as={Heart} />`. */
  as: LucideIcon;
  size?: number;
  /** Overrides `tone`. For the cases where the colour comes from a navigator or a prop. */
  color?: string;
  tone?: Tone;
  /**
   * 2 matches the existing hand-drawn sets. 2.5 reads as "lit" and is what active and
   * emphasised states use — the icon equivalent of a bold weight.
   */
  strokeWidth?: number;
  /** Lucide fills with `currentColor`; used for the solid/active variants. */
  fill?: string;
};

export function Icon({
  as: Glyph,
  size = 24,
  color,
  tone = 'content',
  strokeWidth = 2,
  fill = 'none',
}: IconProps) {
  const colors = useThemeColors();

  const tones: Record<Tone, string> = {
    content: colors.content,
    muted: colors.muted,
    primary: colors.primary,
    accent: colors.accent,
    gold: colors.gold,
    online: colors.online,
    danger: colors.danger,
    success: colors.success,
    inverse: colors.onBrand,
  };

  return <Glyph size={size} color={color ?? tones[tone]} strokeWidth={strokeWidth} fill={fill} />;
}
