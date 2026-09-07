import Svg, { Circle, Path } from 'react-native-svg';
import { useThemeColors } from '../theme';
import { outline, rimmed } from './shopArt';

/**
 * A coin pack's picture, one per rung of the shelf.
 *
 * ## Why these are not all the same glyph
 *
 * Every pack used to render the same Lucide `Coins`, so four rows that differ by 14× in
 * value looked identical and the only thing separating them was the number. A shop ladder
 * has to be readable as a ladder *before* the prices are read — the eye should see "more"
 * going down the list. Two coins, a pouch, a heap, a chest: the quantity is the picture.
 *
 * ## Why they are drawn here rather than shipped as art
 *
 * These are 24dp marks inside a 40dp circle. As bitmaps they would need three densities
 * each, would not take the theme's gold, and would cost download size for something a
 * dozen paths can say. Drawn as SVG they scale, they recolour, and they cost nothing.
 *
 * ## The rules they keep
 *
 * - **24×24 viewBox, round joins** — the same grid `KeywordIcon` and `PlatformIcon` use,
 *   which is what lets these sit in a list beside Lucide glyphs without either looking
 *   wrong.
 * - **One colour, layered by opacity** — the `shopArt` convention, shared with
 *   `ConsumableIcon` so the whole shelf is drawn to one weight.
 * - **Drawn from primitives.** Chests and coin piles are genre furniture and free to use;
 *   another game's *artwork* is not, and these are sold, so this is the same licence rule
 *   the cosmetics generator keeps. Nothing here is traced from anything.
 */
export type CoinPackTier = 1 | 2 | 3 | 4;

/**
 * Which picture a pack gets, from its place on the shelf rather than its coin amount.
 *
 * Position is the thing that actually means "bigger" — a reprice that changes every
 * amount should not silently reshuffle the artwork. Anything past the fourth rung keeps
 * the chest, so adding a fifth pack renders sensibly before anybody draws for it.
 */
export function tierForIndex(index: number): CoinPackTier {
  return (Math.min(index + 1, 4) as CoinPackTier) || 1;
}

export function CoinPackIcon({
  tier,
  size = 24,
  color,
}: {
  tier: CoinPackTier;
  size?: number;
  color?: string;
}) {
  const colors = useThemeColors();
  const gold = color ?? colors.gold;

  // A coin is a rimmed disc. See shopArt for why depth is opacity rather than a second gold.
  const coin = (fillOpacity: number) => rimmed(gold, fillOpacity);
  const line = outline(gold);

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {tier === 1 && (
        // Two coins. The smallest amount somebody can buy, drawn as the smallest amount
        // that still reads as "coins" rather than "a coin".
        <>
          <Circle cx={8.5} cy={15.5} r={5.5} {...coin(0.25)} />
          <Circle cx={15} cy={9.5} r={6} {...coin(0.5)} />
          <Circle cx={15} cy={9.5} r={2.2} {...line} />
        </>
      )}

      {tier === 2 && (
        // A sack: wide at the bottom, gathered at the neck, tied. The first attempt gave it
        // a round body and a narrow neck and it read as a light bulb — the silhouette has
        // to be bottom-heavy or it is not a bag. The coin on the belly says what is in it,
        // which a floating coin above the tie did not.
        <>
          <Path
            d="M8.9 9.4C5.2 11.3 3.6 14.4 4.6 17.2 5.6 20 8.5 21.6 12 21.6s6.4-1.6 7.4-4.4c1-2.8-.6-5.9-4.3-7.8Z"
            {...coin(0.3)}
            strokeLinejoin="round"
          />
          <Path d="M8.6 9.4h6.8" {...line} />
          <Path d="M9.6 9.4 10.2 5.6h3.6l.6 3.8" {...line} />
          <Path d="M10.2 5.6 8.4 3.4M13.8 5.6l1.8-2.2" {...line} />
          <Circle cx={12} cy={15.6} r={2.7} {...coin(0.6)} />
        </>
      )}

      {tier === 3 && (
        // A heap. Three, then two, then one — the stack shape the eye already knows, and
        // the reason this rung does not need a label to look bigger than the pouch.
        <>
          <Circle cx={5.6} cy={18} r={3.5} {...coin(0.2)} />
          <Circle cx={12} cy={19} r={3.5} {...coin(0.28)} />
          <Circle cx={18.4} cy={18} r={3.5} {...coin(0.2)} />
          <Circle cx={8.8} cy={12.6} r={3.5} {...coin(0.45)} />
          <Circle cx={15.2} cy={12.6} r={3.5} {...coin(0.45)} />
          <Circle cx={12} cy={7} r={3.7} {...coin(0.7)} />
          <Circle cx={12} cy={7} r={1.4} {...line} />
        </>
      )}

      {tier === 4 && (
        // A chest, open, overflowing. The top rung should look like the top rung from
        // across the room; a fourth pile of coins would not.
        <>
          {/* The lid, thrown back. Without it the coins float above a box and the whole
              thing reads as a crate someone left some change on. */}
          <Path d="M5.4 8.2 6.6 4.6h10.8l1.2 3.6Z" {...coin(0.25)} strokeLinejoin="round" />
          <Circle cx={8.6} cy={10.4} r={2.4} {...coin(0.5)} />
          <Circle cx={13.6} cy={9.6} r={2.8} {...coin(0.65)} />
          <Circle cx={17} cy={11} r={2} {...coin(0.4)} />
          <Path d="M3 12.6h18v7.2a1.4 1.4 0 0 1-1.4 1.4H4.4A1.4 1.4 0 0 1 3 19.8Z" {...coin(0.3)} strokeLinejoin="round" />
          <Path d="M3 16h18" {...line} />
          <Path d="M10.4 16h3.2v2.8h-3.2Z" {...coin(0.9)} strokeLinejoin="round" />
        </>
      )}
    </Svg>
  );
}
