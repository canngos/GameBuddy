import Svg, { Circle, Path } from 'react-native-svg';
import { useThemeColors } from '../theme';
import { outline, rimmed } from './shopArt';

/**
 * The consumables' pictures, drawn to the same rule as the coin packs.
 *
 * These sat on Lucide's `Sparkles` and `Heart` while the packs above them got their own
 * art, which left one shelf drawn in two weights — outlines above, filled marks below,
 * on the same scroll. Matching them is the whole reason this exists.
 *
 * Both stay on the **accent**, and that is deliberate rather than decorative: it is the one
 * colour in the palette allowed to mean "like", and both of these buy likes. The gold on
 * the coin packs means money. Somebody skimming the Shop tab should be able to tell the two
 * shelves apart without reading either.
 *
 * The toast fired after a purchase still takes a Lucide glyph — `Toast.icon` is typed
 * `LucideIcon` and a toast is not a product picture, so `ConsumableShelf` keeps both: the
 * Lucide one for the toast, this one for the 40dp well.
 */
export type ConsumableArt = 'superLike' | 'extraLikes';

export function ConsumableIcon({
  art,
  size = 24,
  color,
}: {
  art: ConsumableArt;
  size?: number;
  color?: string;
}) {
  const colors = useThemeColors();
  const tint = color ?? colors.accent;

  const shape = (fillOpacity: number) => rimmed(tint, fillOpacity);
  const line = outline(tint);

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {art === 'superLike' && (
        // A star, glinting. The copy promises it "stands out", so the mark has to be the
        // loudest thing in the row — a five-point star is the shape that already means
        // "this one counts more" everywhere else somebody has seen it.
        <>
          <Path
            d="M12 6.4 13.79 10.53 18.28 10.96 14.9 13.94 15.88 18.34 12 16.05 8.12 18.34 9.1 13.94 5.72 10.96 10.21 10.53Z"
            {...shape(0.55)}
            strokeLinejoin="round"
          />
          <Path
            d="M4.2 2.8Q4.2 5.2 6.6 5.2Q4.2 5.2 4.2 7.6Q4.2 5.2 1.8 5.2Q4.2 5.2 4.2 2.8Z"
            {...shape(0.75)}
          />
          <Path
            d="M20 3.8Q20 5.6 21.8 5.6Q20 5.6 20 7.4Q20 5.6 18.2 5.6Q20 5.6 20 3.8Z"
            {...shape(0.45)}
          />
        </>
      )}

      {art === 'extraLikes' && (
        // A heart with a plus. Three stacked hearts was the first idea — it matches the
        // "quantity is the picture" logic of the coin packs — but at 26dp three overlapping
        // hearts is a blob. The plus says "more" at any size, and it is the same badge the
        // rest of the category uses.
        <>
          <Path
            d="M10 20.4C10 20.4 3.6 16.3 3.6 12C3.6 9.7 5.4 8.2 7.3 8.2C8.5 8.2 9.5 8.8 10 9.6C10.5 8.8 11.5 8.2 12.7 8.2C14.6 8.2 16.4 9.7 16.4 12C16.4 16.3 10 20.4 10 20.4Z"
            {...shape(0.5)}
            strokeLinejoin="round"
          />
          <Circle cx={18.6} cy={6.4} r={4} {...shape(0.2)} />
          <Path d="M18.6 4.4v4M16.6 6.4h4" {...line} />
        </>
      )}
    </Svg>
  );
}
